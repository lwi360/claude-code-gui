package io.github.feelhappy.ccaitoolkit.action.console;

import io.github.feelhappy.ccaitoolkit.PluginIds;
import io.github.feelhappy.ccaitoolkit.i18n.ClaudeCodeGuiBundle;
import io.github.feelhappy.ccaitoolkit.ui.toolwindow.ClaudeSDKToolWindow;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Sends the selected Run/Debug console text into the chat input.
 */
public class SendConsoleSelectionToInputAction extends AnAction implements DumbAware {

    private static final Logger LOG = Logger.getInstance(SendConsoleSelectionToInputAction.class);
    private static final Map<Class<?>, Optional<Method>> EDITOR_METHOD_CACHE = new ConcurrentHashMap<>();

    public SendConsoleSelectionToInputAction() {
        super(
                ClaudeCodeGuiBundle.message("action.sendConsoleSelection.text"),
                ClaudeCodeGuiBundle.message("action.sendConsoleSelection.description"),
                null
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(resolveSelectedText(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        String selectedText = resolveSelectedText(e);
        if (project == null || selectedText == null) {
            return;
        }
        sendToChatWindow(project, selectedText);
    }

    private static @Nullable String resolveSelectedText(@NotNull AnActionEvent e) {
        Editor editor = findEditor(e.getData(LangDataKeys.CONSOLE_VIEW));
        if (editor == null) {
            return null;
        }
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return null;
        }
        return selectedText;
    }

    private static @Nullable Editor findEditor(@Nullable Object consoleView) {
        if (consoleView == null) {
            return null;
        }
        if (consoleView instanceof ConsoleViewImpl) {
            return ((ConsoleViewImpl) consoleView).getEditor();
        }
        if (consoleView instanceof ConsoleView) {
            return invokeGetEditor(consoleView);
        }
        return null;
    }

    private static @Nullable Editor invokeGetEditor(@NotNull Object consoleView) {
        Class<?> clazz = consoleView.getClass();
        try {
            Optional<Method> cached = EDITOR_METHOD_CACHE.computeIfAbsent(clazz, cls -> {
                try {
                    Method method = cls.getMethod("getEditor");
                    if (!Editor.class.isAssignableFrom(method.getReturnType())) {
                        return Optional.empty();
                    }
                    return Optional.of(method);
                } catch (NoSuchMethodException ex) {
                    return Optional.empty();
                }
            });
            if (cached.isEmpty()) {
                return null;
            }
            Object result = cached.get().invoke(consoleView);
            if (result instanceof Editor) {
                return (Editor) result;
            }
        } catch (ReflectiveOperationException ex) {
            LOG.debug("Failed to invoke getEditor() on " + clazz.getName(), ex);
        }
        return null;
    }

    private static void sendToChatWindow(@NotNull Project project, @NotNull String text) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(PluginIds.TOOL_WINDOW_ID);
        if (toolWindow == null) {
            LOG.warn("CC AI Toolkit tool window was not found");
            return;
        }
        if (!toolWindow.isVisible()) {
            toolWindow.activate(() -> AppExecutorUtil.getAppScheduledExecutorService().schedule(() ->
                    ApplicationManager.getApplication().invokeLater(() -> deliver(project, text)), 300, TimeUnit.MILLISECONDS), true);
            return;
        }
        deliver(project, text);
        toolWindow.activate(null, true);
    }

    private static void deliver(@NotNull Project project, @NotNull String text) {
        if (project.isDisposed()) {
            return;
        }
        try {
            ClaudeSDKToolWindow.addSelectionFromExternal(project, text);
        } catch (Exception ex) {
            LOG.warn("Failed to send console selection: " + ex.getMessage(), ex);
        }
    }
}
