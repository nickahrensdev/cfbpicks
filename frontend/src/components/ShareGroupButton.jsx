import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Modal, Spinner } from 'react-bootstrap';

import { api } from '../api/client.js';
import { appUrl } from '../lib/appUrl.js';
import { usePointerBlur } from '../lib/pointerFocus.js';
import { Loading } from './common.jsx';

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

/** Trims trailing zeroes so 1.00 reads as 1 and 0.50 as 0.5. */
const points = (value) => Number(value).toString();

/**
 * What the group is, as a few lines someone can read in a text message.
 *
 * <p>Only what changes how you would play it. A recipient deciding whether to
 * join cares that it is a weekly pick'em with ten picks and no moneylines;
 * they do not care about the push value on a market the group has turned off.
 */
function summarise(settings) {
  if (!settings) return '';

  const markets = [
    settings.moneylineEnabled && 'Moneyline',
    settings.spreadEnabled && 'Spread',
    settings.totalEnabled && 'Over/Under',
  ].filter(Boolean);

  const period = settings.cadence === 'DAILY' ? 'day' : 'week';

  const lines = [
    `Join "${settings.name}" on Nick's Picks`,
    '',
    [
      settings.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em",
      settings.cadence === 'DAILY' ? 'Daily' : 'Weekly',
      settings.maxPicksPerCadence
        ? `${settings.maxPicksPerCadence} picks per ${period}`
        : 'No pick limit',
    ].join(' · '),
    `Picking: ${markets.join(', ')}`,
  ];

  if (settings.minPicksPerCadence > 0) {
    lines.push(`At least ${settings.minPicksPerCadence} pick`
      + `${settings.minPicksPerCadence === 1 ? '' : 's'} per ${period}`);
  }
  if (settings.groupType === 'ELIMINATION' && settings.strikesAllowed != null) {
    lines.push(`Out after ${settings.strikesAllowed + 1} wrong picks`);
  }

  // Scoring, but only for the markets actually in play.
  const scoring = [
    settings.moneylineEnabled && ['Moneyline', 'moneyline'],
    settings.spreadEnabled && ['Spread', 'spread'],
    settings.totalEnabled && ['Over/Under', 'total'],
  ]
    .filter(Boolean)
    .map(([label, key]) =>
      `${label} ${points(settings[`${key}WinPoints`])}/`
      + `${points(settings[`${key}LossPoints`])}/`
      + `${points(settings[`${key}PushPoints`])}`);
  lines.push(`Scoring (W/L/P): ${scoring.join(' · ')}`);

  lines.push(`Picks close ${settings.lockLeadMinutes} min before kickoff`);

  if (settings.requireApproval) {
    lines.push('An owner has to approve you before you are in');
  }

  return lines.join('\n');
}

/**
 * Sharing a group: the link, and optionally what the group actually is.
 *
 * <p>A bare link asks someone to join something they cannot see. The summary
 * is the difference between "click this" and an invitation they can decide
 * on - so it is offered alongside the link rather than instead of it, and the
 * two buttons are the two things people actually want to send.
 *
 * <p>The link itself is the same every time - see GroupShareLink. Pressing
 * this again hands back the link already given out rather than minting a new
 * one, so a link pasted into a message last week still works. The token
 * identifies the sharer as well as the group, which is what makes referrals
 * countable.
 *
 * <p>Not rendered at all unless the group says the caller may share it. A
 * public group is findable by search anyway; a private one needs its owner to
 * have opted in.
 *
 * @param iconOnly drops the text label, for a dense list where the word
 *                 "Share" costs more width than it earns.
 */
export default function ShareGroupButton({
  groupId,
  size = 'sm',
  variant = 'outline-secondary',
  iconOnly = false,
}) {
  const blurOnPointer = usePointerBlur();

  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState(null);
  const [token, setToken] = useState(null);
  const [message, setMessage] = useState('');
  const [includePassword, setIncludePassword] = useState(true);
  const [state, setState] = useState('idle');
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    setDetail(null);
    setToken(null);
    try {
      // The link and the group in one go. Minting the link on open rather
      // than on send means the modal can show the actual URL being sent.
      const [group, share] = await Promise.all([
        api.group(groupId),
        api.shareGroup(groupId),
      ]);
      setDetail(group);
      setToken(share.token);
      setMessage(summarise(group.settings));
    } catch (err) {
      setError(err.message ?? 'Could not build a share link');
    }
  }, [groupId]);

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  // appUrl, not origin alone: the app is served from a sub-path on GitHub
  // Pages, and a link without it lands on GitHub's 404 rather than in the app.
  const url = token ? appUrl(`/join/${token}`) : null;

  // Only someone who may manage the group is sent the password at all - see
  // GroupService.settingsOf - so this is absent for an ordinary member even
  // when the group has one.
  const password = detail?.settings?.joinPassword || null;

  // Owners edit; everyone else sends what the group says it is. A member
  // rewriting the rules in an invitation would be describing a group that
  // does not exist.
  const editable = Boolean(detail?.manageable);

  // GroupDetail carries no name of its own - it is part of the settings - so
  // detail.name was undefined everywhere it was read.
  const name = detail?.settings?.name ?? null;

  /**
   * Everything to send, assembled from whichever button was pressed.
   *
   * <p>The link leads. Most messaging apps preview the first URL in a message
   * and truncate long text behind a tap, so a link under six lines of summary
   * is one the recipient has to go looking for.
   *
   * <p>The password comes last, after the summary - it is what you reach for
   * once you have decided to join, so it reads as the final step rather than
   * a condition attached to the link.
   */
  const compose = (withSummary) => {
    const parts = [url];
    if (withSummary && message.trim()) parts.push(message.trim());
    if (password && includePassword) parts.push(`Password: ${password}`);
    return parts.filter(Boolean).join('\n\n');
  };

  /** The same content minus the link, for the share sheet's separate url field. */
  const summaryAndPassword = () =>
    [message.trim(), password && includePassword ? `Password: ${password}` : null]
      .filter(Boolean)
      .join('\n\n');

  const passwordOnly = () =>
    (password && includePassword ? `Password: ${password}` : undefined);

  /**
   * The phone's own share sheet where there is one, the clipboard otherwise.
   *
   * <p>Feature-detected rather than sniffed for a phone: some desktop
   * browsers have the share sheet too, and a user agent string is a poor
   * proxy for what the browser can actually do.
   */
  const send = async (withSummary) => {
    const text = compose(withSummary);
    setState('working');
    setError(null);

    if (navigator.share) {
      try {
        /*
         * url as its own field, not buried in text.
         *
         * The Web Share API has a dedicated url, and iOS Messages treats a
         * payload whose text merely *contains* a link as a link share: it
         * builds a rich preview from the URL and throws the rest of the text
         * away. That is what dropped the summary - and putting the link first
         * made it certain, since iOS reads the leading URL.
         *
         * Passing them separately is the documented usage and lets the target
         * app compose them itself. Some targets still take only the url; the
         * clipboard path below always carries everything, which is why the
         * dialog shows the full text.
         */
        await navigator.share({
          title: name ?? undefined,
          text: withSummary ? summaryAndPassword() : passwordOnly(),
          url: url ?? undefined,
        });
        setState('idle');
        setOpen(false);
        return;
      } catch (err) {
        // Dismissing the sheet rejects with AbortError. That is a choice, not
        // a failure, and must not fall through to copying something they just
        // decided not to send.
        if (err?.name === 'AbortError') {
          setState('idle');
          return;
        }
        // Anything else - a browser that advertises share but refuses this
        // payload - falls through to the clipboard below.
      }
    }

    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
      setTimeout(() => setState('idle'), 2000);
    } catch {
      // Clipboard access needs a secure context and can be refused outright,
      // so the text is shown as a fallback rather than lost.
      window.prompt('Copy this invitation', text);
      setState('idle');
    }
  };

  const label = state === 'copied' ? 'Link copied' : 'Share';
  const glyph =
    state === 'copied' ? <CheckIcon /> : <ShareIcon className={iconOnly ? '' : 'me-1'} />;

  return (
    <>
      <Button
        size={size}
        variant={state === 'copied' ? 'success' : variant}
        onClick={() => setOpen(true)}
        onPointerUp={blurOnPointer}
        className={iconOnly ? 'control-btn' : 'text-nowrap'}
        aria-haspopup="dialog"
        aria-label={iconOnly ? label : undefined}
        title={iconOnly ? label : undefined}
      >
        {glyph}
        {!iconOnly && label}
      </Button>

      <Modal show={open} onHide={() => setOpen(false)} centered scrollable>
        <Modal.Header closeButton>
          <Modal.Title className="h5">Share {name ?? 'this group'}</Modal.Title>
        </Modal.Header>

        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}

          {!detail && !error ? (
            <Loading label="Building the invitation" />
          ) : (
            detail && (
              <>
                <Form.Group className="mb-3">
                  <Form.Label className="small fw-semibold mb-1">Message</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={9}
                    value={message}
                    onChange={(event) => setMessage(event.target.value)}
                    readOnly={!editable}
                    className={editable ? undefined : 'bg-body-secondary'}
                    aria-label="Invitation message"
                  />
                  <Form.Text className="text-body-secondary">
                    {editable
                      ? 'Sent with the link when you choose "Link and summary". Edit it however you like.'
                      : 'A summary of how this group plays. Only an owner can change it.'}
                  </Form.Text>
                </Form.Group>

                {/* Only when there is a password, and only for someone the
                    server actually sent it to. */}
                {password && (
                  <Form.Group className="mb-3">
                    <Form.Check
                      type="switch"
                      id="share-include-password"
                      label="Include the join password"
                      checked={includePassword}
                      onChange={(event) => setIncludePassword(event.target.checked)}
                    />
                    <Form.Text className="text-body-secondary">
                      Without it they will be asked for the password before they can join.
                    </Form.Text>
                  </Form.Group>
                )}

                <div className="small text-body-tertiary text-break">{url}</div>
              </>
            )
          )}
        </Modal.Body>

        <Modal.Footer className="justify-content-between">
          {/* Two buttons because these are two different messages, not one
              with an option. "Here is the link" and "here is what you are
              joining" get sent to different people. */}
          <Button
            variant="outline-secondary"
            disabled={!url || state === 'working'}
            onClick={() => send(false)}
          >
            {state === 'working' && (
              <Spinner as="span" size="sm" animation="border" className="me-2" />
            )}
            Link only
          </Button>
          <Button disabled={!url || state === 'working'} onClick={() => send(true)}>
            Link and summary
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
