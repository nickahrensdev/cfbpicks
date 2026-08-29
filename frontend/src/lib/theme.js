const STORAGE_KEY = 'nickspicks-theme';

export const THEMES = ['MIDNIGHT', 'OCEAN', 'EMBER', 'FOREST', 'SLATE'];
export const MODES = ['LIGHT', 'DARK'];

/**
 * Applies a theme/mode to the document and remembers it for next time.
 *
 * <p>Bootstrap's own `data-bs-theme` attribute drives its built-in
 * dark-mode-aware components for free; `data-theme` is ours, switching which
 * block of CSS custom-property overrides in themes.css is in effect. Both
 * live on `<html>` rather than a wrapper element so they apply before React
 * has mounted anything.
 */
export function applyTheme(theme, mode) {
  const safeTheme = THEMES.includes(theme) ? theme : 'MIDNIGHT';
  const safeMode = MODES.includes(mode) ? mode : 'LIGHT';

  document.documentElement.dataset.theme = safeTheme.toLowerCase();
  document.documentElement.dataset.bsTheme = safeMode.toLowerCase();

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ theme: safeTheme, mode: safeMode }));
  } catch {
    // Private browsing or storage disabled - the theme still applies for
    // this page load, it just will not be remembered for the next one.
  }
}

/**
 * The last-known theme, read synchronously so it can be applied before the
 * first paint - without this, the page would flash the default theme for a
 * moment every time a member with a non-default theme loads the app, while
 * `/api/me` is still in flight.
 */
export function cachedTheme() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return [stored?.theme, stored?.mode];
  } catch {
    return [undefined, undefined];
  }
}
