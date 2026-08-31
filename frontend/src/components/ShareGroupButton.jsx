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
export default function ShareGroupButton({ groupId, size = 'sm', variant = 'outline-secondary' }) {
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

  return (
    <div className="d-inline-flex flex-column align-items-end">
      <Button
        size={size}
        variant={state === 'copied' ? 'success' : variant}
        onClick={share}
        onPointerUp={blurOnPointer}
        disabled={state === 'working'}
        className="text-nowrap"
      >
        {state === 'working' && (
          <Spinner as="span" size="sm" animation="border" className="me-2" />
        )}
        {label}
      </Button>
      {state !== 'idle' && state !== 'working' && state !== 'copied' && (
        <span className="small text-danger mt-1">{state}</span>
      )}
    </div>
  );
}
