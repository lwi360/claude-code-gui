const STORAGE_KEY = 'skipNewSessionConfirm';
const CHANGE_EVENT = 'skipNewSessionConfirmChanged';

export function getSkipNewSessionConfirm(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function isNewSessionConfirmEnabled(): boolean {
  return !getSkipNewSessionConfirm();
}

export function setNewSessionConfirmEnabled(enabled: boolean): void {
  try {
    if (enabled) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, 'true');
    }
  } catch {
    // localStorage can be unavailable inside the embedded browser.
  }
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT));
}
