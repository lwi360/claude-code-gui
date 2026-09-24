package com.github.claudecodegui.startup;

import com.github.claudecodegui.bridge.BridgeDirectoryResolver;
import com.github.claudecodegui.util.PlatformUtils;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.io.FileUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Plugin update listener that cleans up old ai-bridge cache when plugin version changes.
 * This ensures that users always get the latest ai-bridge version after plugin updates.
 */
public class PluginUpdateListener implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(PluginUpdateListener.class);
    private static final String LAST_VERSION_KEY = "claude.code.last.plugin.version";
    private static final String SDK_DIR_NAME = "ai-bridge";

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                checkAndCleanupOldCache();
            } catch (Exception e) {
                LOG.warn("[PluginUpdateListener] Failed to check plugin version: " + e.getMessage(), e);
            }
        });
        return Unit.INSTANCE;
    }

    /**
     * Check if plugin version has changed and cleanup old cache if needed.
     */
    private void checkAndCleanupOldCache() {
        try {
            PluginId pluginId = PluginId.getId(PlatformUtils.getPluginId());
            IdeaPluginDescriptor descriptor = PlatformUtils.findPluginDescriptor(pluginId);
            if (descriptor == null) {
                LOG.debug("[PluginUpdateListener] Plugin descriptor not found");
                return;
            }

            String currentVersion = descriptor.getVersion();
            PropertiesComponent props = PropertiesComponent.getInstance();
            String lastVersion = props.getValue(LAST_VERSION_KEY);

            LOG.info("[PluginUpdateListener] Current version: " + currentVersion + ", Last version: " + lastVersion);

            if (lastVersion != null && !lastVersion.equals(currentVersion)) {
                LOG.info("[PluginUpdateListener] Plugin version changed from " + lastVersion + " to " + currentVersion + ", cleaning up old cache...");
                cleanupOldBridgeCache(descriptor, currentVersion);
            }

            // Update stored version
            props.setValue(LAST_VERSION_KEY, currentVersion);
        } catch (Exception e) {
            LOG.warn("[PluginUpdateListener] Failed to check plugin version: " + e.getMessage(), e);
        }
    }

    /**
     * Cleanup old ai-bridge cache directory.
     */
    private void cleanupOldBridgeCache(IdeaPluginDescriptor descriptor, String currentVersion) {
        try {
            File pluginDir = descriptor.getPluginPath().toFile();
            File bridgeDir = new File(pluginDir, SDK_DIR_NAME);

            if (bridgeDir.exists() && bridgeDir.isDirectory()) {
                if (bridgeMatchesVersion(bridgeDir, currentVersion)) {
                    LOG.info("[PluginUpdateListener] ai-bridge already matches " + currentVersion + ", skipping cleanup");
                    return;
                }

                LOG.info("[PluginUpdateListener] Deleting old ai-bridge cache: " + bridgeDir.getAbsolutePath());
                boolean deleted = FileUtil.delete(bridgeDir);
                if (deleted) {
                    LOG.info("[PluginUpdateListener] Successfully deleted old ai-bridge cache");
                    BridgeDirectoryResolver resolver = BridgePreloader.getSharedResolver();
                    resolver.clearCache();
                    LOG.info("[PluginUpdateListener] Re-extracting ai-bridge for " + currentVersion);
                    resolver.findSdkDir();
                } else {
                    LOG.warn("[PluginUpdateListener] Failed to delete old ai-bridge cache, will be overwritten on next extraction");
                }
            } else {
                LOG.debug("[PluginUpdateListener] No old ai-bridge cache found at: " + bridgeDir.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.warn("[PluginUpdateListener] Failed to cleanup old cache: " + e.getMessage(), e);
        }
    }

    /**
     * The extracted bridge is already for this plugin version when startup extraction
     * finishes before the version-change cleanup runs.
     */
    private boolean bridgeMatchesVersion(File bridgeDir, String currentVersion) {
        File versionFile = new File(bridgeDir, ".bridge-version");
        if (!versionFile.isFile() || currentVersion == null || currentVersion.isEmpty()) {
            return false;
        }
        try {
            String signature = Files.readString(versionFile.toPath(), StandardCharsets.UTF_8).trim();
            return signature.startsWith(currentVersion + ":");
        } catch (Exception e) {
            LOG.warn("[PluginUpdateListener] Failed to read bridge version file: " + e.getMessage());
            return false;
        }
    }
}
