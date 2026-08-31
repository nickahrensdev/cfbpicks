import { useLayoutEffect } from 'react';

/**
 * Publishes the real heights of the stacked sticky bars as CSS variables.
 *
 * <p>The navbar and the group bar sit above the games board's pick budget, and
 * each has to be offset by the total height of the ones above it. Hard-coding
 * those offsets does not work: the bars are different heights at different
 * breakpoints, the navbar grows when its burger menu opens, and the group bar
 * is absent entirely for a member in no groups. A number that is even slightly
 * short lets the next bar slide underneath, which reads as each bar being
 * clipped as you scroll.
 *
 * <p>So they are measured. Elements opt in with {@code data-sticky="nav"} and
 * {@code data-sticky="group"}; this writes {@code --nav-height} and
 * {@code --group-bar-height} onto the root for the stylesheet to stack with.
 *
 * <p>Runs after every render, deliberately - the bars mount and unmount with
 * the selected group, so a one-shot effect would miss them appearing. Two
 * {@code offsetHeight} reads is a cheap price for the offsets always matching
 * what is actually on screen.
 */
export function useStickyOffsets() {
  useLayoutEffect(() => {
    const root = document.documentElement;

    const measure = () => {
      const nav = document.querySelector('[data-sticky="nav"]');
      const group = document.querySelector('[data-sticky="group"]');

      // Zero rather than a guess when a bar is absent, so the one below it
      // moves up to meet the navbar instead of floating.
      root.style.setProperty('--nav-height', `${nav ? nav.offsetHeight : 0}px`);
      root.style.setProperty('--group-bar-height', `${group ? group.offsetHeight : 0}px`);
    };

    measure();

    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }

    // Catches the changes a re-render does not: the burger menu opening, a
    // long group name wrapping, the window being resized.
    const observer = new ResizeObserver(measure);
    document.querySelectorAll('[data-sticky]').forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  });
}
