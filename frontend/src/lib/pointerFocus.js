import { useEffect } from 'react';

/**
 * Drops focus from a button after it is clicked or tapped.
 *
 * <p>Bootstrap's focused button takes the hover background plus a ring, which
 * is close enough to its "on" state to be misread: after clicking Refresh the
 * button sits there looking engaged until you click somewhere else, so it
 * reads as though the action stuck rather than ran.
 *
 * <p>Bound to {@code pointerup} rather than {@code click} on purpose. A
 * keyboard activation (Enter or Space) fires click but never pointerup, so
 * keyboard focus is left exactly where it was - which is the whole reason not
 * to do this by preventing focus on mousedown.
 *
 * <p>Toggles are unaffected: "My picks" shows its state through its variant
 * and a check icon, not through focus, so losing the ring costs it nothing.
 */
export function usePointerBlur() {
  useEffect(() => {
    const handler = (event) => {
      // Only buttons. Links keep focus so the next Tab continues from there,
      // and inputs obviously need to keep it.
      const button = event.target.closest?.('button');
      if (button && document.activeElement === button) {
        button.blur();
      }
    };

    document.addEventListener('pointerup', handler);
    return () => document.removeEventListener('pointerup', handler);
  }, []);
}
