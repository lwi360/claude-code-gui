package io.github.feelhappy.ccaitoolkit.completion;

import io.github.feelhappy.ccaitoolkit.bridge.BridgeDirectoryResolver;
import io.github.feelhappy.ccaitoolkit.bridge.EnvironmentConfigurator;
import io.github.feelhappy.ccaitoolkit.bridge.NodeDetector;
import io.github.feelhappy.ccaitoolkit.startup.BridgePreloader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Long-lived Node process that turns a cursor prefix into a short inline completion.
 * A new request cancels the previous one.
 */
public class CodePredictionClient implements Disposable {

    private static final Logger LOG = Logger.getInstance(CodePredictionClient.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 20;

    private final Object writeLock = new Object();
    private final ConcurrentHashMap<String, StreamTicket> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private Process process;
    private OutputStreamWriter stdin;
    private volatile boolean loggedStartupFailure;

    public record PredictionInput(
            @NotNull String prefix,
            @NotNull String suffix,
            @NotNull String fileName,
            @NotNull String language
    ) {
    }

    /**
     * Blocks until the model finishes, aborts, or times out. {@code onDelta} receives each newly visible piece
     * and may run on the process stdout thread.
     */
    public void stream(@NotNull PredictionInput input, @NotNull Consumer<String> onDelta) throws InterruptedException {
        if (input.prefix().isBlank() || !ensureStarted()) {
            return;
        }

        String id = Long.toString(sequence.incrementAndGet());
        StreamTicket ticket = new StreamTicket(onDelta);
        synchronized (writeLock) {
            pending.forEach((ignored, previous) -> previous.finish());
            pending.clear();
            pending.put(id, ticket);
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("id", id);
                payload.addProperty("prefix", input.prefix());
                payload.addProperty("suffix", input.suffix());
                payload.addProperty("fileName", input.fileName());
                payload.addProperty("language", input.language());
                writeLine(payload);
            } catch (Exception e) {
                pending.remove(id, ticket);
                LOG.warn("[NextEdit] Failed to send prediction request: " + e.getMessage());
                stopProcess();
                return;
            }
        }

        try {
            ticket.await(REQUEST_TIMEOUT_SECONDS);
        } catch (InterruptedException e) {
            cancel(id);
            throw e;
        } catch (Exception e) {
            cancel(id);
        } finally {
            pending.remove(id, ticket);
        }
    }

    @Override
    public void dispose() {
        stopProcess();
    }

    private void cancel(String id) {
        StreamTicket ticket = pending.remove(id);
        if (ticket != null) {
            ticket.finish();
        }
        synchronized (writeLock) {
            if (stdin == null) {
                return;
            }
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("id", id);
                payload.addProperty("cancel", true);
                writeLine(payload);
            } catch (Exception e) {
                stopProcess();
            }
        }
    }

    private void writeLine(JsonObject payload) throws java.io.IOException {
        stdin.write(payload.toString());
        stdin.write('\n');
        stdin.flush();
    }

    private boolean ensureStarted() {
        Process current = process;
        if (current != null && current.isAlive() && stdin != null) {
            return true;
        }
        synchronized (writeLock) {
            if (process != null && process.isAlive() && stdin != null) {
                return true;
            }
            stopProcess();
            return startProcess();
        }
    }

    private boolean startProcess() {
        try {
            BridgeDirectoryResolver resolver = BridgePreloader.getSharedResolver();
            File sdkDir = resolver.findSdkDir();
            if (sdkDir == null || !sdkDir.isDirectory()) {
                logStartupFailure("ai-bridge directory is not ready");
                return false;
            }
            File script = new File(sdkDir, "code-prediction-server.js");
            if (!script.isFile()) {
                logStartupFailure("code-prediction-server.js is missing from ai-bridge");
                return false;
            }
            String node = NodeDetector.getInstance().findNodeExecutable();
            ProcessBuilder builder = new ProcessBuilder(node, script.getAbsolutePath());
            builder.directory(sdkDir);
            new EnvironmentConfigurator().updateProcessEnvironment(builder, node);
            Process started = builder.start();
            OutputStreamWriter writer = new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8);
            Thread stdoutThread = new Thread(() -> drainStdout(started), "next-edit-stdout");
            Thread stderrThread = new Thread(() -> drainStderr(started), "next-edit-stderr");
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();
            process = started;
            stdin = writer;
            loggedStartupFailure = false;
            return true;
        } catch (Exception e) {
            logStartupFailure(e.getMessage());
            stopProcess();
            return false;
        }
    }

    private void drainStdout(Process started) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                completeLine(line);
            }
        } catch (Exception e) {
            LOG.debug("[NextEdit] Prediction stdout closed: " + e.getMessage());
        }
        failPending();
    }

    private void drainStderr(Process started) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    LOG.info("[NextEdit] " + line.trim());
                }
            }
        } catch (Exception ignored) {
            // The process ending closes the stream.
        }
    }

    private void completeLine(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return;
        }
        try {
            JsonObject result = JsonParser.parseString(trimmed).getAsJsonObject();
            if (!result.has("id") || result.get("id").isJsonNull()) {
                return;
            }
            StreamTicket ticket = pending.get(result.get("id").getAsString());
            if (ticket == null) {
                return;
            }
            if (result.has("delta") && !result.get("delta").isJsonNull()) {
                ticket.push(result.get("delta").getAsString());
                return;
            }
            if (result.has("done") && !result.get("done").isJsonNull() && result.get("done").getAsBoolean()) {
                pending.remove(result.get("id").getAsString(), ticket);
                ticket.finish();
            }
        } catch (Exception e) {
            LOG.debug("[NextEdit] Ignored prediction output: " + e.getMessage());
        }
    }

    private void failPending() {
        pending.forEach((ignored, ticket) -> ticket.finish());
        pending.clear();
    }

    private void stopProcess() {
        OutputStreamWriter writer = stdin;
        stdin = null;
        Process current = process;
        process = null;
        failPending();
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ignored) {
                // Closing stdin asks the server to exit.
            }
        }
        if (current != null) {
            current.destroy();
        }
    }

    private void logStartupFailure(@Nullable String message) {
        if (loggedStartupFailure) {
            return;
        }
        loggedStartupFailure = true;
        LOG.warn("[NextEdit] Prediction process did not start: " + message);
    }

    private static final class StreamTicket {
        private final Consumer<String> onDelta;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private StreamTicket(Consumer<String> onDelta) {
            this.onDelta = onDelta;
        }

        private void push(String delta) {
            if (delta == null || delta.isEmpty() || done.isDone()) {
                return;
            }
            try {
                onDelta.accept(delta);
            } catch (Exception ignored) {
                // The editor session was cancelled between tokens.
            }
        }

        private void finish() {
            done.complete(null);
        }

        private void await(int seconds) throws Exception {
            done.get(seconds, TimeUnit.SECONDS);
        }
    }
}
