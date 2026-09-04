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
  /*
   * Stop the browser restoring scroll as well.
   *
   * The default is "auto", which means on a Back navigation the browser
   * restores the position it remembers - except in a single-page app the
   * content has not rendered yet, so it restores onto an empty document and
   * lands at the top. That attempt can be flushed *after* ours, which looked
   * like the page briefly appearing in the right place and then jumping to
   * the top.
   *
   * Set here rather than at startup so it is owned by the thing that replaces
   * it, and left set: nothing else in the app relies on the browser's copy.
   */
  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
  }, []);

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

    let saved = null;
    try {
      saved = window.sessionStorage.getItem(storageKey);
    } catch {
      // As above.
    }
    if (!saved) return undefined;

    /*
     * Two frames, not one. The effect runs after React commits but before the
     * browser has laid the cards out, so a scroll issued here is clamped to
     * whatever height the page has at that instant - which on a long board is
     * far less than where we are trying to get to. One frame gets us past the
     * commit; the second gets us past the paint that gives the page its real
     * height.
     */
    let second = 0;
    const first = window.requestAnimationFrame(() => {
      second = window.requestAnimationFrame(() => {
        // Instant, not smooth: this is meant to look like the page never
        // moved, and animating back down a long board draws attention to
        // exactly the thing being hidden.
        window.scrollTo({ top: Number(saved), behavior: 'instant' });
      });
    });

    return () => {
      window.cancelAnimationFrame(first);
      window.cancelAnimationFrame(second);
    };
  }, [ready, storageKey]);
}
