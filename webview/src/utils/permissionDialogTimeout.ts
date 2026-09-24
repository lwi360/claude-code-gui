export const DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS = 300;
export const MIN_PERMISSION_DIALOG_TIMEOUT_SECONDS = 30;
export const MAX_PERMISSION_DIALOG_TIMEOUT_SECONDS = 3600;

const CHANGE_EVENT = 'permission-dialog-timeout-changed';

let currentSeconds = DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;

export function clampPermissionDialogTimeoutSeconds(seconds: number): number {
  if (!Number.isFinite(seconds)) {
    return DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;
  }
  const rounded = Math.round(seconds);
  return Math.max(
    MIN_PERMISSION_DIALOG_TIMEOUT_SECONDS,
    Math.min(MAX_PERMISSION_DIALOG_TIMEOUT_SECONDS, rounded),
  );
}

export function getPermissionDialogTimeoutSeconds(): number {
  return currentSeconds;
}

export function setCurrentPermissionDialogTimeoutSeconds(seconds: number): void {
  currentSeconds = clampPermissionDialogTimeoutSeconds(seconds);
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: currentSeconds }));
}
