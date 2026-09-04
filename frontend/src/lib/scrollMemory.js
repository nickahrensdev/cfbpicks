import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

const PREFIX = 'nickspicks.scroll:';

/**
 * Puts a page back where it was when you left it.
 *
 * <p>Browsers restore scroll on their own for a full page load, but a
 * single-page app navigating between routes is not a page load - the board
 * unmounts, and coming back from a game's details drops you at the top of a
 * hundred-game week.
 *
 * <p>Keyed by the history entry rather than the URL. React Router stamps each
 * entry with a key and returns the same one when you go Back, so two visits to
 * the same board - once scrolled to Saturday, once to Thursday - keep their own
 * positions instead of overwriting each other.
 *
 * <p>Stored in sessionStorage, so it lives as long as the tab and no longer.
 * A remembered scroll position is worth nothing tomorrow.
 *
 * @param ready whether the content that gives the page its height has
 *              rendered. Restoring before then scrolls a short page and the
 *              browser clamps it to the bottom of nothing.
 */
export function useScrollMemory(ready) {
  const { key } = useLocation();
  const storageKey = `${PREFIX}${key}`;
  const restored = useRef(false);

  // Written on the way out rather than on every scroll event: the position
  // that matters is the last one, and a listener firing on every pixel would
  // be writing to storage through the whole of a long scroll.
  useEffect(() => {
    return () => {
      try {
        window.sessionStorage.setItem(storageKey, String(window.scrollY));
      } catch {
        // Private browsing, or storage refused. Losing the position is not
        // worth failing a navigation over.
      }
    };
  }, [storageKey]);

  useEffect(() => {
    if (!ready || restored.current) return;
    restored.current = true;

    try {
      const saved = window.sessionStorage.getItem(storageKey);
      if (saved) {
        // Instant, not smooth: this is meant to look like the page never
        // moved, and animating back down a long board draws attention to
        // exactly the thing being hidden.
        window.scrollTo({ top: Number(saved), behavior: 'instant' });
      }
    } catch {
      // As above.
    }
  }, [ready, storageKey]);
}
