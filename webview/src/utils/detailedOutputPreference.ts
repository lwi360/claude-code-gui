const STORAGE_KEY = 'detailedOutputEnabled';
const CHANGE_EVENT = 'detailed-output-enabled-changed';

export function isDetailedOutputEnabled(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function setDetailedOutputEnabled(enabled: boolean): void {
  try {
    if (enabled) {
      localStorage.setItem(STORAGE_KEY, 'true');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // localStorage can be unavailable inside the embedded browser.
  }
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: enabled }));
}

export function subscribeDetailedOutput(listener: (enabled: boolean) => void): () => void {
  const handler = (event: Event) => {
    const detail = (event as CustomEvent<boolean>).detail;
    listener(typeof detail === 'boolean' ? detail : isDetailedOutputEnabled());
  };
  window.addEventListener(CHANGE_EVENT, handler);
  return () => window.removeEventListener(CHANGE_EVENT, handler);
}
