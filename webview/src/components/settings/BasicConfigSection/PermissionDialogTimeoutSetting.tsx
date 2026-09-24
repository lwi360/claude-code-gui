import { useEffect, useState } from 'react';
import styles from './style.module.less';
import { useTranslation } from 'react-i18next';
import { clampPermissionDialogTimeoutSeconds } from '../../../utils/permissionDialogTimeout';

interface PermissionDialogTimeoutSettingProps {
  seconds: number;
  onCommit: (seconds: number) => void;
}

const PermissionDialogTimeoutSetting = ({ seconds, onCommit }: PermissionDialogTimeoutSettingProps) => {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(String(seconds));

  useEffect(() => {
    setDraft(String(seconds));
  }, [seconds]);

  const commit = () => {
    const next = clampPermissionDialogTimeoutSeconds(Number(draft));
    setDraft(String(next));
    if (next !== seconds) {
      onCommit(next);
    }
  };

  return (
    <div className={styles.streamingSection}>
      <div className={styles.fieldHeader}>
        <span className="codicon codicon-watch" />
        <span className={styles.fieldLabel}>{t('settings.basic.permissionDialogTimeout.label')}</span>
      </div>
      <div className={styles.timeoutInputWrapper}>
        <input
          type="number"
          className={styles.nodePathInput}
          min={30}
          max={3600}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              commit();
            }
          }}
        />
        <span>{t('settings.basic.permissionDialogTimeout.unit')}</span>
      </div>
      <small className={styles.formHint}>
        <span className="codicon codicon-info" />
        <span>{t('settings.basic.permissionDialogTimeout.hint')}</span>
      </small>
    </div>
  );
};

export default PermissionDialogTimeoutSetting;
