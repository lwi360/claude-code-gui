import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import styles from './style.module.less';
import { useTranslation } from 'react-i18next';
import ToggleSettingSection from './ToggleSettingSection';
import PermissionDialogTimeoutSetting from './PermissionDialogTimeoutSetting';

/** Upward-opening custom select for sound selection (avoids JCEF clipping) */
const SoundSelectUpward = ({
  value,
  onChange,
  options,
  onTestSound,
  testSoundLabel,
}: {
  value: string;
  onChange: (val: string) => void;
  options: { value: string; label: string }[];
  onTestSound: () => void;
  testSoundLabel: string;
}) => {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedLabel = options.find((o) => o.value === value)?.label ?? value;

  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
      setOpen(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open, handleClickOutside]);

  return (
    <div className={styles.soundSelectRow}>
      <div className={styles.upwardSelect} ref={containerRef}>
        <button
          type="button"
          className={`${styles.upwardSelectTrigger} ${open ? styles.open : ''}`}
          onClick={() => setOpen((prev) => !prev)}
        >
          {selectedLabel}
        </button>
        {open && (
          <div className={styles.upwardSelectDropdown}>
            {options.map((opt) => (
              <div
                key={opt.value}
                className={`${styles.upwardSelectOption} ${opt.value === value ? styles.selected : ''}`}
                onClick={() => {
                  onChange(opt.value);
                  setOpen(false);
                }}
              >
                {opt.label}
              </div>
            ))}
          </div>
        )}
      </div>
      <button
        className={styles.soundTestBtn}
        onClick={onTestSound}
        title={testSoundLabel}
      >
        <span className="codicon codicon-play" />
      </button>
    </div>
  );
};

export interface BehaviorTabProps {
  sendShortcut?: 'enter' | 'cmdEnter';
  onSendShortcutChange?: (shortcut: 'enter' | 'cmdEnter') => void;
  streamingEnabled?: boolean;
  onStreamingEnabledChange?: (enabled: boolean) => void;
  autoOpenFileEnabled?: boolean;
  onAutoOpenFileEnabledChange?: (enabled: boolean) => void;
  diffExpandedByDefault?: boolean;
  onDiffExpandedByDefaultChange?: (enabled: boolean) => void;
  soundNotificationEnabled?: boolean;
  onSoundNotificationEnabledChange?: (enabled: boolean) => void;
  soundOnlyWhenUnfocused?: boolean;
  onSoundOnlyWhenUnfocusedChange?: (enabled: boolean) => void;
  selectedSound?: string;
  onSelectedSoundChange?: (soundId: string) => void;
  customSoundPath?: string;
  onCustomSoundPathChange?: (path: string) => void;
  onSaveCustomSoundPath?: () => void;
  onTestSound?: () => void;
  onBrowseSound?: () => void;
  newSessionConfirmEnabled?: boolean;
  onNewSessionConfirmEnabledChange?: (enabled: boolean) => void;
  detailedOutputEnabled?: boolean;
  onDetailedOutputEnabledChange?: (enabled: boolean) => void;
  permissionDialogTimeoutSeconds?: number;
  onPermissionDialogTimeoutChange?: (seconds: number) => void;
  commitGenerationEnabled?: boolean;
  onCommitGenerationEnabledChange?: (enabled: boolean) => void;
  statusBarWidgetEnabled?: boolean;
  onStatusBarWidgetEnabledChange?: (enabled: boolean) => void;
  taskCompletionNotificationEnabled?: boolean;
  onTaskCompletionNotificationEnabledChange?: (enabled: boolean) => void;
  askUserQuestionNotificationEnabled?: boolean;
  onAskUserQuestionNotificationEnabledChange?: (enabled: boolean) => void;
  askUserQuestionSoundNotificationEnabled?: boolean;
  onAskUserQuestionSoundNotificationEnabledChange?: (enabled: boolean) => void;
  systemNotificationOnlyWhenUnfocused?: boolean;
  onSystemNotificationOnlyWhenUnfocusedChange?: (enabled: boolean) => void;
  aiTitleGenerationEnabled?: boolean;
  onAiTitleGenerationEnabledChange?: (enabled: boolean) => void;
}

const BehaviorTab = ({
  sendShortcut = 'enter',
  onSendShortcutChange = () => {},
  streamingEnabled = true,
  onStreamingEnabledChange = () => {},
  autoOpenFileEnabled = true,
  onAutoOpenFileEnabledChange = () => {},
  diffExpandedByDefault = false,
  onDiffExpandedByDefaultChange = () => {},
  soundNotificationEnabled = false,
  onSoundNotificationEnabledChange = () => {},
  soundOnlyWhenUnfocused = false,
  onSoundOnlyWhenUnfocusedChange = () => {},
  selectedSound = 'default',
  onSelectedSoundChange = () => {},
  customSoundPath = '',
  onCustomSoundPathChange = () => {},
  onSaveCustomSoundPath = () => {},
  onTestSound = () => {},
  onBrowseSound = () => {},
  newSessionConfirmEnabled = true,
  onNewSessionConfirmEnabledChange = () => {},
  detailedOutputEnabled = false,
  onDetailedOutputEnabledChange = () => {},
  permissionDialogTimeoutSeconds = 300,
  onPermissionDialogTimeoutChange = () => {},
  commitGenerationEnabled = true,
  onCommitGenerationEnabledChange = () => {},
  statusBarWidgetEnabled = true,
  onStatusBarWidgetEnabledChange = () => {},
  taskCompletionNotificationEnabled = false,
  onTaskCompletionNotificationEnabledChange = () => {},
  askUserQuestionNotificationEnabled = false,
  onAskUserQuestionNotificationEnabledChange = () => {},
  askUserQuestionSoundNotificationEnabled = false,
  onAskUserQuestionSoundNotificationEnabledChange = () => {},
  systemNotificationOnlyWhenUnfocused = false,
  onSystemNotificationOnlyWhenUnfocusedChange = () => {},
  aiTitleGenerationEnabled = true,
  onAiTitleGenerationEnabledChange = () => {},
}: BehaviorTabProps) => {
  const { t } = useTranslation();

  const soundOptions = useMemo(() => [
    { value: 'default', label: t('settings.basic.soundNotification.soundDefault') },
    { value: 'chime', label: t('settings.basic.soundNotification.soundChime') },
    { value: 'bell', label: t('settings.basic.soundNotification.soundBell') },
    { value: 'ding', label: t('settings.basic.soundNotification.soundDing') },
    { value: 'success', label: t('settings.basic.soundNotification.soundSuccess') },
    { value: 'custom', label: t('settings.basic.soundNotification.soundCustom') },
  ], [t]);

  return (
    <div className={styles.tabContent}>
      {/* Send shortcut configuration */}
      <div className={styles.sendShortcutSection}>
        <div className={styles.fieldHeader}>
          <span className="codicon codicon-keyboard" />
          <span className={styles.fieldLabel}>{t('settings.basic.sendShortcut.label')}</span>
        </div>
        <div className={styles.themeGrid}>
          <div
            className={`${styles.themeCard} ${sendShortcut === 'enter' ? styles.active : ''}`}
            onClick={() => onSendShortcutChange('enter')}
          >
            {sendShortcut === 'enter' && (
              <div className={styles.checkBadge}>
                <span className="codicon codicon-check" />
              </div>
            )}
            <div className={styles.themeCardTitle}>{t('settings.basic.sendShortcut.enter')}</div>
            <div className={styles.themeCardDesc}>{t('settings.basic.sendShortcut.enterDesc')}</div>
          </div>

          <div
            className={`${styles.themeCard} ${sendShortcut === 'cmdEnter' ? styles.active : ''}`}
            onClick={() => onSendShortcutChange('cmdEnter')}
          >
            {sendShortcut === 'cmdEnter' && (
              <div className={styles.checkBadge}>
                <span className="codicon codicon-check" />
              </div>
            )}
            <div className={styles.themeCardTitle}>{t('settings.basic.sendShortcut.cmdEnter')}</div>
            <div className={styles.themeCardDesc}>{t('settings.basic.sendShortcut.cmdEnterDesc')}</div>
          </div>
        </div>
      </div>

      {/* Streaming configuration */}
      <div className={styles.streamingSection}>
        <div className={styles.fieldHeader}>
          <span className="codicon codicon-sync" />
          <span className={styles.fieldLabel}>{t('settings.basic.streaming.label')}</span>
        </div>
        <label className={styles.toggleWrapper}>
          <input
            type="checkbox"
            className={styles.toggleInput}
            checked={streamingEnabled}
            onChange={(e) => onStreamingEnabledChange(e.target.checked)}
          />
          <span className={styles.toggleSlider} />
          <span className={styles.toggleLabel}>
            {streamingEnabled
              ? t('settings.basic.streaming.enabled')
              : t('settings.basic.streaming.disabled')}
          </span>
        </label>
        <small className={styles.formHint}>
          <span className="codicon codicon-info" />
          <span>{t('settings.basic.streaming.hint')}</span>
        </small>
      </div>

      {/* Auto open file configuration */}
      <div className={styles.streamingSection}>
        <div className={styles.fieldHeader}>
          <span className="codicon codicon-file" />
          <span className={styles.fieldLabel}>{t('settings.basic.autoOpenFile.label')}</span>
        </div>
        <label className={styles.toggleWrapper}>
          <input
            type="checkbox"
            className={styles.toggleInput}
            checked={autoOpenFileEnabled}
            onChange={(e) => onAutoOpenFileEnabledChange(e.target.checked)}
          />
          <span className={styles.toggleSlider} />
          <span className={styles.toggleLabel}>
            {autoOpenFileEnabled
              ? t('settings.basic.autoOpenFile.enabled')
              : t('settings.basic.autoOpenFile.disabled')}
          </span>
        </label>
        <small className={styles.formHint}>
          <span className="codicon codicon-info" />
          <span>{t('settings.basic.autoOpenFile.hint')}</span>
        </small>
      </div>

      {/* Diff expanded by default configuration */}
      <div className={styles.streamingSection}>
        <div className={styles.fieldHeader}>
          <span className="codicon codicon-diff" />
          <span className={styles.fieldLabel}>{t('settings.basic.diffExpanded.label')}</span>
        </div>
        <label className={styles.toggleWrapper}>
          <input
            type="checkbox"
            className={styles.toggleInput}
            checked={diffExpandedByDefault}
            onChange={(e) => onDiffExpandedByDefaultChange(e.target.checked)}
          />
          <span className={styles.toggleSlider} />
          <span className={styles.toggleLabel}>
            {diffExpandedByDefault
              ? t('settings.basic.diffExpanded.enabled')
              : t('settings.basic.diffExpanded.disabled')}
          </span>
        </label>
        <small className={styles.formHint}>
          <span className="codicon codicon-info" />
          <span>{t('settings.basic.diffExpanded.hint')}</span>
        </small>
      </div>

      <ToggleSettingSection
        icon="codicon-sparkle"
        label={t('settings.basic.aiTitleGeneration.label')}
        checked={aiTitleGenerationEnabled}
        onChange={onAiTitleGenerationEnabledChange}
        enabledLabel={t('settings.basic.aiTitleGeneration.enabled')}
        disabledLabel={t('settings.basic.aiTitleGeneration.disabled')}
        hint={t('settings.basic.aiTitleGeneration.hint')}
      />

      <ToggleSettingSection
        icon="codicon-comment-discussion"
        label={t('settings.basic.newSessionConfirm.label')}
        checked={newSessionConfirmEnabled}
        onChange={onNewSessionConfirmEnabledChange}
        enabledLabel={t('settings.basic.newSessionConfirm.enabled')}
        disabledLabel={t('settings.basic.newSessionConfirm.disabled')}
        hint={t('settings.basic.newSessionConfirm.hint')}
      />

      <ToggleSettingSection
        icon="codicon-list-tree"
        label={t('settings.basic.detailedOutput.label')}
        checked={detailedOutputEnabled}
        onChange={onDetailedOutputEnabledChange}
        enabledLabel={t('settings.basic.detailedOutput.enabled')}
        disabledLabel={t('settings.basic.detailedOutput.disabled')}
        hint={t('settings.basic.detailedOutput.hint')}
      />

      <PermissionDialogTimeoutSetting
        seconds={permissionDialogTimeoutSeconds}
        onCommit={onPermissionDialogTimeoutChange}
      />

      <ToggleSettingSection
        icon="codicon-git-commit"
        label={t('settings.basic.commitGeneration.label')}
        checked={commitGenerationEnabled}
        onChange={onCommitGenerationEnabledChange}
        enabledLabel={t('settings.basic.commitGeneration.enabled')}
        disabledLabel={t('settings.basic.commitGeneration.disabled')}
        hint={t('settings.basic.commitGeneration.hint')}
      />

      <ToggleSettingSection
        icon="codicon-layout-statusbar"
        label={t('settings.basic.statusBarWidget.label')}
        checked={statusBarWidgetEnabled}
        onChange={onStatusBarWidgetEnabledChange}
        enabledLabel={t('settings.basic.statusBarWidget.enabled')}
        disabledLabel={t('settings.basic.statusBarWidget.disabled')}
        hint={t('settings.basic.statusBarWidget.hint')}
      />

      {/* Sound notification */}
      <div className={styles.streamingSection}>
        <div className={styles.fieldHeader}>
          <span className="codicon codicon-unmute" />
          <span className={styles.fieldLabel}>{t('settings.basic.soundNotification.label')}</span>
        </div>
        <label className={styles.toggleWrapper}>
          <input
            type="checkbox"
            className={styles.toggleInput}
            checked={soundNotificationEnabled}
            onChange={(e) => onSoundNotificationEnabledChange(e.target.checked)}
          />
          <span className={styles.toggleSlider} />
          <span className={styles.toggleLabel}>
            {soundNotificationEnabled
              ? t('settings.basic.soundNotification.enabled')
              : t('settings.basic.soundNotification.disabled')}
          </span>
        </label>
        <small className={styles.formHint}>
          <span className="codicon codicon-info" />
          <span>{t('settings.basic.soundNotification.hint')}</span>
        </small>

        {soundNotificationEnabled && (
          <div className={styles.customSoundSection}>
            <div className={styles.soundOnlyWhenUnfocusedSection}>
              <div className={styles.fieldHeader}>
                <span className="codicon codicon-eye-closed" />
                <span className={styles.fieldLabel}>{t('settings.basic.soundNotification.onlyWhenUnfocused')}</span>
              </div>
              <label className={styles.toggleWrapper}>
                <input
                  type="checkbox"
                  className={styles.toggleInput}
                  checked={soundOnlyWhenUnfocused}
                  onChange={(e) => onSoundOnlyWhenUnfocusedChange(e.target.checked)}
                />
                <span className={styles.toggleSlider} />
                <span className={styles.toggleLabel}>
                  {soundOnlyWhenUnfocused
                    ? t('settings.basic.soundNotification.enabled')
                    : t('settings.basic.soundNotification.disabled')}
                </span>
              </label>
              <small className={styles.formHint}>
                <span className="codicon codicon-info" />
                <span>{t('settings.basic.soundNotification.onlyWhenUnfocusedHint')}</span>
              </small>
            </div>

            <div className={styles.fieldHeader}>
              <span className="codicon codicon-library" />
              <span className={styles.fieldLabel}>{t('settings.basic.soundNotification.selectSound')}</span>
            </div>
            <SoundSelectUpward
              value={selectedSound}
              onChange={onSelectedSoundChange}
              options={soundOptions}
              onTestSound={onTestSound}
              testSoundLabel={t('settings.basic.soundNotification.testSound')}
            />

            {selectedSound === 'custom' && (
              <div className={styles.customSoundFileSection}>
                <div className={styles.fieldHeader}>
                  <span className="codicon codicon-file-media" />
                  <span className={styles.fieldLabel}>{t('settings.basic.soundNotification.customSound')}</span>
                </div>
                <div className={styles.nodePathInputWrapper}>
                  <input
                    type="text"
                    className={styles.nodePathInput}
                    placeholder={t('settings.basic.soundNotification.customSoundPlaceholder')}
                    value={customSoundPath}
                    onChange={(e) => onCustomSoundPathChange(e.target.value)}
                  />
                  <button
                    className={styles.saveBtn}
                    onClick={onBrowseSound}
                    title={t('settings.basic.soundNotification.browse')}
                  >
                    <span className="codicon codicon-folder-opened" />
                  </button>
                  <button
                    className={styles.saveBtn}
                    onClick={onSaveCustomSoundPath}
                  >
                    {t('common.save')}
                  </button>
                </div>
                <small className={styles.formHint}>
                  <span className="codicon codicon-info" />
                  <span>{t('settings.basic.soundNotification.customSoundHint')}</span>
                </small>
              </div>
            )}
          </div>
        )}
      </div>

      <ToggleSettingSection
        icon="codicon-bell"
        label={t('settings.basic.taskCompletionNotification.label')}
        checked={taskCompletionNotificationEnabled}
        onChange={onTaskCompletionNotificationEnabledChange}
        enabledLabel={t('settings.basic.taskCompletionNotification.enabled')}
        disabledLabel={t('settings.basic.taskCompletionNotification.disabled')}
        hint={t('settings.basic.taskCompletionNotification.hint')}
      />

      <ToggleSettingSection
        icon="codicon-question"
        label={t('settings.basic.askUserQuestionNotification.label')}
        checked={askUserQuestionNotificationEnabled}
        onChange={onAskUserQuestionNotificationEnabledChange}
        enabledLabel={t('settings.basic.askUserQuestionNotification.enabled')}
        disabledLabel={t('settings.basic.askUserQuestionNotification.disabled')}
        hint={t('settings.basic.askUserQuestionNotification.hint')}
      />

      <ToggleSettingSection
        icon="codicon-unmute"
        label={t('settings.basic.askUserQuestionSound.label')}
        checked={askUserQuestionSoundNotificationEnabled}
        onChange={onAskUserQuestionSoundNotificationEnabledChange}
        enabledLabel={t('settings.basic.askUserQuestionSound.enabled')}
        disabledLabel={t('settings.basic.askUserQuestionSound.disabled')}
        hint={t('settings.basic.askUserQuestionSound.hint')}
      />

      <ToggleSettingSection
        icon="codicon-eye-closed"
        label={t('settings.basic.systemNotificationOnlyWhenUnfocused.label')}
        checked={systemNotificationOnlyWhenUnfocused}
        onChange={onSystemNotificationOnlyWhenUnfocusedChange}
        enabledLabel={t('settings.basic.systemNotificationOnlyWhenUnfocused.enabled')}
        disabledLabel={t('settings.basic.systemNotificationOnlyWhenUnfocused.disabled')}
        hint={t('settings.basic.systemNotificationOnlyWhenUnfocused.hint')}
      />
    </div>
  );
};

export default BehaviorTab;
