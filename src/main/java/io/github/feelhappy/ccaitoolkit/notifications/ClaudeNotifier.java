package io.github.feelhappy.ccaitoolkit.notifications;

import io.github.feelhappy.ccaitoolkit.PluginIds;
import io.github.feelhappy.ccaitoolkit.i18n.ClaudeCodeGuiBundle;
import io.github.feelhappy.ccaitoolkit.settings.CodemossSettingsService;
import io.github.feelhappy.ccaitoolkit.util.SoundNotificationService;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Simple utility to update the Claude Status Bar Widget.
 */
public class ClaudeNotifier {

    public static void setThinking(@NotNull Project project) {
        update(project, "thinking", ClaudeCodeGuiBundle.message("notifier.thinking"));
    }

    public static void setGenerating(@NotNull Project project) {
        update(project, "generating", ClaudeCodeGuiBundle.message("notifier.generating"));
    }

    public static void setWaiting(@NotNull Project project) {
        update(project, "waiting", ClaudeCodeGuiBundle.message("notifier.waiting"));
    }

    public static void showSuccess(@NotNull Project project, String message) {
        show(project, "Claude ✓", message, 5000);

        // Play the task completion notification sound
        SoundNotificationService.getInstance().playTaskCompleteSound();
    }

    public static void showTaskCompleted(@NotNull Project project, String message) {
        showSuccess(project, message);
        showSystemNotification(project, ClaudeCodeGuiBundle.message("notifier.taskCompleted"),
                CodemossSettingsService::getTaskCompletionNotificationEnabled);
    }

    public static void showAskUserQuestion(@NotNull Project project) {
        showSystemNotification(project, ClaudeCodeGuiBundle.message("notifier.askUserQuestion"),
                CodemossSettingsService::getAskUserQuestionNotificationEnabled);
        SoundNotificationService.getInstance().playAskUserQuestionSound();
    }

    public static void showError(@NotNull Project project, String message) {
        show(project, "Claude ✗", message, 8000);
    }

    public static void showWarning(@NotNull Project project, String message) {
        show(project, "Claude ⚠", message, 6000);
    }

    public static void clearStatus(@NotNull Project project) {
        update(project, "ready", null);
    }
    
    public static void setTokenUsage(@NotNull Project project, int usedTokens, int maxTokens) {
        String tokenInfo = formatTokenUsage(usedTokens, maxTokens);
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) {
                widget.setTokenInfo(tokenInfo);
            }
        });
    }

    private static String formatTokenUsage(int used, int max) {
        if (used == 0) return "";
        String usedStr = formatNumber(used);
        if (max > 0) {
            String maxStr = formatNumber(max);
            return String.format("[%s / %s ctx]", usedStr, maxStr);
        }
        return String.format("[%s ctx]", usedStr);
    }
    
    public static void setModel(@NotNull Project project, String model) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) widget.setModel(model);
        });
    }

    public static void setMode(@NotNull Project project, String mode) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) widget.setMode(mode);
        });
    }

    public static void setAgent(@NotNull Project project, String agent) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) widget.setAgent(agent);
        });
    }

    private static String formatNumber(int num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1000000) return String.format("%.1fk", num / 1000.0);
        return String.format("%.1fm", num / 1000000.0);
    }

    private static void update(@NotNull Project project, String status, String details) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) {
                widget.updateStatus(status, details);
            }
        });
    }

    private static void showSystemNotification(
            @NotNull Project project,
            String message,
            NotificationPreference enabled) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                CodemossSettingsService settings = new CodemossSettingsService();
                if (!enabled.isEnabled(settings)) {
                    return;
                }
                if (settings.getSystemNotificationOnlyWhenUnfocused()
                        && ApplicationManager.getApplication().isActive()) {
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() ->
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup(PluginIds.NOTIFICATION_GROUP_ID)
                                .createNotification(message, NotificationType.INFORMATION)
                                .notify(project));
            } catch (Exception ignored) {
                // A missing notification preference should not interrupt the chat flow.
            }
        });
    }

    @FunctionalInterface
    private interface NotificationPreference {
        boolean isEnabled(CodemossSettingsService settings) throws Exception;
    }

    private static void show(@NotNull Project project, String text, String tooltip, long duration) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ClaudeStatusBarWidget widget = ClaudeStatusBarWidget.Factory.getWidget(project);
            if (widget != null) {
                widget.show(text, tooltip, duration);
            }
        });
    }
}
