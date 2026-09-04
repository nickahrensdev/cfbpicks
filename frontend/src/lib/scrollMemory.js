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
 * <p>Keyed by path, not by history entry. The obvious choice is
 * {@code location.key}, so that two visits to the same board - one scrolled to
 * Saturday, one to Thursday - keep separate positions. It does not survive this
 * app: the games board writes its filters and week into the URL with
 * {@code setSearchParams(..., { replace: true })}, and every replace mints a
 * fresh key. The position would be saved under a key nothing ever reads again.
 * Sharing one position per path is the lesser problem, and matches what people
 * seem to expect anyway.
 *
 * <p>Stored in sessionStorage, so it lives as long as the tab and no longer.
 * A remembered scroll position is worth nothing tomorrow.
 *
 * @param ready whether the content that gives the page its height has
 *              rendered. Restoring before then scrolls a short page and the
 *              browser clamps it to the bottom of nothing.
 */
export function useScrollMemory(ready) {
  const { pathname, key } = useLocation();
  const storageKey = `${PREFIX}${pathname}`;
  const restored = useRef(false);

  /*
   * Stop the browser restoring scroll as well - for every history entry, not
   * just the one that existed at mount.
   *
   * scrollRestoration belongs to a single entry and a new entry defaults to
   * "auto". A replace navigation creates a new entry, and this page does one
   * on mount and on every filter change, so a version of this that ran once
   * with [] deps left every entry it created set to "auto". The browser then
   * restored that fresh entry - which has no recorded position - to 0, a frame
   * or two after we had put the page back where it belonged. That is the
   * "jumps to the game, then jumps to the top" flicker.
   *
   * Keyed on location.key so it re-asserts after each navigation.
   */
  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
  }, [key]);

  // Written on the way out rather than on every scroll event: the position
  // that matters is the last one, and a listener firing on every pixel would
  // be writing to storage through the whole of a long scroll.
  //
  // Also written on every navigation away, including the replace navigations
  // this page does to itself - harmless, since the value is the same position
  // under the same key.
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
    if (!ready || restored.current) return undefined;

    let saved = null;
    try {
      saved = window.sessionStorage.getItem(storageKey);
    } catch {
      // As above.
    }
    if (!saved) return undefined;

    const target = Number(saved);
    if (!Number.isFinite(target) || target <= 0) return undefined;

    restored.current = true;

    /*
     * Re-applied over several frames rather than set once.
     *
     * The page is still growing when the first frame lands: card images and
     * fonts settle after the commit, and a scroll issued against a page that
     * is not yet tall enough gets clamped to whatever height exists at that
     * instant. One frame gets past the commit, the next past the first paint,
     * but neither is a guarantee - so keep asking until the page is tall
     * enough to hold the position, then stop.
     *
     * Bounded by a deadline so a page that never gets tall enough - a week
     * with six games, returned to from a week with sixty - stops trying
     * instead of fighting the user's own scrolling forever.
     */
    const deadline = performance.now() + 600;
    let frame = 0;

    /*
     * The user wins. Re-applying the position for up to 600ms would otherwise
     * drag someone back who started scrolling the moment the board appeared -
     * a page that fights the wheel is worse than one that lands at the top.
     * Passive: these only observe.
     */
    let abandoned = false;
    const abandon = () => {
      abandoned = true;
    };
    const interrupts = ['wheel', 'touchstart', 'keydown'];
    interrupts.forEach((type) => window.addEventListener(type, abandon, { passive: true }));

    const settle = () => {
      if (abandoned) return;

      if (Math.abs(window.scrollY - target) > 1) {
        // Instant, not smooth: this is meant to look like the page never
        // moved, and animating back down a long board draws attention to
        // exactly the thing being hidden.
        window.scrollTo({ top: target, behavior: 'instant' });
      }

      // Stop once we are there and the page is tall enough to stay there.
      const settled = Math.abs(window.scrollY - target) <= 1;
      const room = document.documentElement.scrollHeight - window.innerHeight;
      if (settled && room >= target) return;

      if (performance.now() < deadline) {
        frame = window.requestAnimationFrame(settle);
      }
    };

    frame = window.requestAnimationFrame(settle);
    return () => {
      window.cancelAnimationFrame(frame);
      interrupts.forEach((type) => window.removeEventListener(type, abandon));
    };
  }, [ready, storageKey]);
}
