import { useState } from 'react';
import { Button, Spinner } from 'react-bootstrap';

import { api } from '../api/client.js';
import { usePointerBlur } from '../lib/pointerFocus.js';

/**
 * Copies this member's invite link for a group.
 *
 * <p>The link is the same one every time - see GroupShareLink. Pressing this
 * again gives back the link already handed out rather than minting a new one,
 * so a link pasted into a message last week still works.
 *
 * <p>The token identifies the sharer as well as the group, which is what makes
 * referrals countable: two members of one league hand out two different links.
 *
 * <p>Not rendered at all unless the group says the caller may share it. A
 * public group is findable by search anyway; a private one needs its owner to
 * have opted in.
 */
/** Three nodes joined by two lines. Decorative - the button carries the label. */
function ShareIcon({ className = '' }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d="M11 2.5a2.5 2.5 0 1 1 .603 1.628l-6.718 3.12a2.5 2.5 0 0 1 0 1.504l6.718 3.12a2.5 2.5 0 1 1-.488.876l-6.718-3.12a2.5 2.5 0 1 1 0-3.256l6.718-3.12A2.5 2.5 0 0 1 11 2.5" />
    </svg>
  );
}

/** Confirmation tick, swapped in for the share glyph after a copy. */
function CheckIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M13.485 1.929a1 1 0 0 1 .143 1.407l-7 8.5a1 1 0 0 1-1.49.083L2.153 8.933a1 1 0 1 1 1.394-1.433l2.21 2.152 6.34-7.7a1 1 0 0 1 1.388-.023z" />
    </svg>
  );
}

/**
 * @param iconOnly drops the text label, for a dense list where the word
 *                 "Share" costs more width than it earns. The accessible name
 *                 moves to aria-label so the control is still announced.
 */
export default function ShareGroupButton({
  groupId,
  size = 'sm',
  variant = 'outline-secondary',
  iconOnly = false,
}) {
  const blurOnPointer = usePointerBlur();
  const [state, setState] = useState('idle');

  const share = async () => {
    setState('working');
    try {
      const { token } = await api.shareGroup(groupId);
      const url = `${window.location.origin}/join/${token}`;

      // Clipboard access needs a secure context and can be refused outright,
      // so the link is shown as a fallback rather than lost - a share button
      // that silently does nothing is worse than one that asks for a copy.
      try {
        await navigator.clipboard.writeText(url);
        setState('copied');
        setTimeout(() => setState('idle'), 2000);
      } catch {
        window.prompt('Copy this invite link', url);
        setState('idle');
      }
    } catch (err) {
      setState(err.message || 'That did not work');
    }
  };

  const label = state === 'working' ? 'Copying…' : state === 'copied' ? 'Link copied' : 'Share';

  // The glyph carries the meaning on its own once the label is gone, so the
  // copied state swaps the icon rather than only the colour - a green button
  // that still shows a share arrow does not say "done".
  const glyph =
    state === 'copied' ? <CheckIcon /> : <ShareIcon className={iconOnly ? '' : 'me-1'} />;

  return (
    <div className="d-inline-flex flex-column align-items-end">
      <Button
        size={size}
        variant={state === 'copied' ? 'success' : variant}
        onClick={share}
        onPointerUp={blurOnPointer}
        disabled={state === 'working'}
        className={iconOnly ? 'control-btn' : 'text-nowrap'}
        aria-label={iconOnly ? label : undefined}
        title={iconOnly ? label : undefined}
      >
        {state === 'working' ? (
          <Spinner as="span" size="sm" animation="border" className={iconOnly ? '' : 'me-2'} />
        ) : (
          glyph
        )}
        {!iconOnly && label}
      </Button>
      {state !== 'idle' && state !== 'working' && state !== 'copied' && (
        <span className="small text-danger mt-1">{state}</span>
      )}
    </div>
  );
}
