import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

/**
 * Back to wherever you came from.
 *
 * <p>Goes back through history so a member returns to the week and filters
 * they were looking at, which a fixed link to the board would throw away.
 * On a page opened directly - a shared link, a bookmark - there is no history
 * to return to, so it falls back to the given route instead of leaving the
 * site.
 */
export function BackButton({ fallback = '/games', label = 'Back', className = '' }) {
  const navigate = useNavigate();
  const location = useLocation();

  // React Router stamps 'default' on the first entry of a session.
  const hasHistory = location.key !== 'default';

  return (
    <Button
      variant="link"
      className={`p-0 text-decoration-none d-inline-flex align-items-center gap-1 ${className}`}
      onClick={() => (hasHistory ? navigate(-1) : navigate(fallback))}
    >
      <span aria-hidden="true">←</span>
      {label}
    </Button>
  );
}

export function Loading({ label = 'Loading' }) {
  return (
    <div className="text-center py-5">
      <Spinner animation="border" role="status" aria-label={label} />
    </div>
  );
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null;
  return (
    <Alert variant="danger" className="d-flex justify-content-between align-items-center gap-3">
      <span>{error.message ?? String(error)}</span>
      {onRetry && (
        <Button size="sm" variant="outline-danger" onClick={onRetry}>
          Retry
        </Button>
      )}
    </Alert>
  );
}

/**
 * A destructive button that needs a second click.
 *
 * <p>The first click swaps the label for the warning, so the confirmation says
 * what will actually be lost rather than asking "are you sure" about nothing.
 * Clicking away resets it - an armed delete button left sitting there is its
 * own hazard.
 */
export function ConfirmButton({
  label,
  confirmLabel,
  onConfirm,
  variant = 'danger',
  size,
  disabled = false,
  className = '',
}) {
  const [armed, setArmed] = useState(false);

  useEffect(() => {
    if (!armed) return undefined;
    const reset = () => setArmed(false);
    // Capture phase, so the button's own click is not what disarms it.
    window.addEventListener('click', reset, { capture: true, once: true });
    return () => window.removeEventListener('click', reset, { capture: true });
  }, [armed]);

  return (
    <Button
      variant={armed ? variant : `outline-${variant}`}
      size={size}
      disabled={disabled}
      className={className}
      onClick={(event) => {
        event.stopPropagation();
        if (armed) {
          setArmed(false);
          onConfirm();
        } else {
          setArmed(true);
        }
      }}
    >
      {armed ? confirmLabel : label}
    </Button>
  );
}

/**
 * Shown in place of a board when the member belongs to no group.
 *
 * <p>Every board is a group's board now, so with no group there is nothing to
 * render - and an empty table would read as "no games this week" rather than
 * "you have not joined a league".
 */
export function NoGroupNotice() {
  return (
    <EmptyState title="You are not in a group yet">
      <p className="mb-3">
        Games, picks and standings all belong to a group. Join one to get started.
      </p>
      <Button as={Link} to="/groups" variant="primary">
        Find a group
      </Button>
    </EmptyState>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="text-center text-body-secondary py-5">
      <p className="mb-2 fw-semibold">{title}</p>
      {children}
    </div>
  );
}

/**
 * A member's username as a handle: {@code @nick}.
 *
 * <p>One helper rather than an "@" typed into each of the dozen places a name
 * appears, so the prefix cannot drift and a handle is never accidentally
 * rendered bare. Usernames carry no spaces, which is what lets the @ read as
 * part of the name rather than as punctuation before it.
 */
export function handle(username) {
  if (!username) return '';
  // Tolerates a stored value that already leads with @ rather than doubling it.
  return username.startsWith('@') ? username : `@${username}`;
}

/**
 * The full identity: {@code Nick Ahrens (@nick)}.
 *
 * <p>Two fields because one could not do both jobs - the display name is what
 * someone is called and may repeat, the username is who they are and does not.
 * Anywhere both fit, both are shown; the handle alone is the fallback when
 * only it is known, and where space is tight.
 */
export function memberName(displayName, username) {
  if (!displayName) return handle(username);
  if (!username) return displayName;
  return `${displayName} (${handle(username)})`;
}

/**
 * The same identity, but with the handle set smaller and quieter than the name.
 *
 * <p>A table of these reads as a column of names with an identifier attached,
 * rather than as two names of equal weight competing for the eye. The string
 * form above is still what a sentence or a toast wants.
 *
 * <p>`stacked` puts the handle on its own line under the name, for a column
 * wide enough that a long name and a long handle side by side would set the
 * whole table's width.
 */
export function MemberName({ displayName, username, className = '', stacked = false }) {
  if (!displayName) return <span className={className}>{handle(username)}</span>;
  const Wrapper = stacked ? 'div' : 'span';
  return (
    <Wrapper className={className}>
      {displayName}
      {username && (
        <span className={`small text-body-secondary ${stacked ? 'd-block' : 'ms-2'}`}>
          {handle(username)}
        </span>
      )}
    </Wrapper>
  );
}

/** Formats the home spread from the perspective of the named side. */
export function formatSpread(homeSpread, side) {
  if (homeSpread === null || homeSpread === undefined) return 'No line';
  const value = side === 'HOME' ? Number(homeSpread) : -Number(homeSpread);
  return value > 0 ? `+${value}` : `${value}`;
}

/**
 * A total, prefixed with the side taken: `O 46.5` / `U 46.5`.
 *
 * <p>The prefix is not decoration. In a column beside the two team rows a bare
 * number would read as belonging to that team, when it belongs to the game.
 */
export function formatTotal(overUnder, side) {
  if (overUnder === null || overUnder === undefined) return 'No line';
  return `${side === 'OVER' ? 'O' : 'U'} ${Number(overUnder)}`;
}

/**
 * "Over 44.5" rather than "O 44.5", for the places with room to spell it -
 * a held pick reads as a sentence, where the pick buttons are a fixed narrow
 * column and have to stay abbreviated.
 */
export function formatTotalLong(overUnder, side) {
  if (overUnder === null || overUnder === undefined) return 'No line';
  return `${side === 'OVER' ? 'Over' : 'Under'} ${Number(overUnder)}`;
}

/**
 * Formats whichever line a pick was made against.
 *
 * <p>A moneyline pick has no line - it is played against the result, not a
 * number - so there is nothing to format and it says so instead.
 */
export function formatLine(line, selection) {
  if (selection === 'HOME_ML' || selection === 'AWAY_ML') return 'To win';
  return selection === 'OVER' || selection === 'UNDER'
    ? formatTotal(line, selection)
    : formatSpread(line, selection);
}

/** Human label for a market. */
export function marketLabel(market) {
  if (market === 'TOTAL') return 'Total';
  if (market === 'MONEYLINE') return 'Moneyline';
  return 'Spread';
}

/**
 * The same, abbreviated, for a narrow column that repeats it on every row.
 *
 * <p>Three markets rendered at full width push the columns that carry the
 * actual information off a phone screen, and a column whose values repeat is
 * the cheapest place to buy that width back.
 */
export function marketAbbr(market) {
  if (market === 'TOTAL') return 'O/U';
  if (market === 'MONEYLINE') return 'ML';
  return 'SPR';
}

/**
 * A pick's line with nothing but the number.
 *
 * <p>{@link formatLine} prefixes a total with O or U so a bare number in a
 * column beside two team rows cannot be read as belonging to a team. Where the
 * pick itself is already spelled out one column over, that prefix is saying the
 * same thing twice.
 */
export function formatLineBare(line, selection) {
  if (selection === 'HOME_ML' || selection === 'AWAY_ML') return 'To win';
  if (line === null || line === undefined) return 'No line';
  return selection === 'OVER' || selection === 'UNDER'
    ? `${Number(line)}`
    : formatSpread(line, selection);
}

/**
 * A row tint for a pick's outcome.
 *
 * <p>The colour replaces a result column rather than joining it: in a table
 * where every other cell is two or three characters, a badge saying WIN was
 * the widest thing on the row to say what a tint says at a glance.
 *
 * <p>Only the background is set, and only faintly. Bootstrap's `.table-success`
 * and friends also force a near-black text colour, which is right on a white
 * page and unreadable on a dark one - a pale pink row of dark text inside a
 * dark card. Overriding `--bs-table-bg` is how the built-in variants tint a
 * row, so hover and striping still layer over the top correctly, while the
 * text keeps whatever colour the theme gives it.
 *
 * <p>A push and a void share grey - both decided nothing, and the difference
 * between them is about the game, not about the pick.
 */
export function pickRowStyle(result) {
  const tint = (name) => ({ '--bs-table-bg': `rgba(var(--bs-${name}-rgb), 0.13)` });
  if (result === 'WIN') return tint('success');
  if (result === 'LOSS') return tint('danger');
  if (result === 'PUSH' || result === 'VOID') return tint('secondary');
  return tint('warning');
}

/**
 * Whether anything on this game can still be picked, in this group.
 *
 * <p>Two ways a game drops out: the lock window has closed (which also covers
 * kicked-off, finished and TBD-kickoff games), or none of the markets the
 * group plays has what it needs. A game with only one of the two lines is
 * still pickable - just not in every market.
 *
 * <p>The moneyline market needs nothing posted, so a group that plays moneylines can
 * pick any game that has not locked.
 *
 * <p>Defined once because the card's disabled buttons and the "hide
 * unpickable" filter have to agree; a game the filter keeps but the card
 * cannot act on is worse than either behaviour alone.
 */
export function isPickable(game, markets = ALL_MARKETS) {
  if (game.locked) return false;
  if (markets.moneyline) return true;
  return (markets.spread && game.homeSpread != null)
    || (markets.total && game.overUnder != null);
}

/** Every market, for callers with no group context to narrow it. */
export const ALL_MARKETS = { moneyline: true, spread: true, total: true };

/** The markets a group plays, in the shape the board and card expect. */
export function marketsOf(group) {
  return group
    ? {
      moneyline: Boolean(group.moneylineEnabled),
      spread: Boolean(group.spreadEnabled),
      total: Boolean(group.totalEnabled),
    }
    : ALL_MARKETS;
}

export function ResultBadge({ result }) {
  if (!result || result === 'PENDING') {
    return <Badge bg="secondary-subtle" text="secondary-emphasis">Pending</Badge>;
  }
  const variants = {
    WIN: ['success', 'Win'],
    LOSS: ['danger', 'Loss'],
    PUSH: ['secondary', 'Push'],
    VOID: ['warning', 'Void'],
  };
  const [variant, label] = variants[result] ?? ['secondary', result];
  return <Badge bg={variant}>{label}</Badge>;
}

/** Open padlock, for a group anyone can join. Paired with {@link LockIcon}. */
export function UnlockIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="12"
      height="12"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M11 1a2 2 0 0 0-2 2v4H3a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-.5V3a1.5 1.5 0 1 1 3 0v3h1V3a2 2 0 0 0-2-2z" />
    </svg>
  );
}

/** Padlock. Decorative - the badge it sits in carries the accessible name via title. */
export function LockIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="12"
      height="12"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M8 1a2 2 0 0 1 2 2v4H6V3a2 2 0 0 1 2-2zm3 6V3a3 3 0 0 0-6 0v4a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" />
    </svg>
  );
}

/**
 * Live "locks in 2h 14m" countdown.
 *
 * <p>Convenience only - the server decides whether a pick is accepted. A tab
 * left open past kickoff will get a 409 rather than a silent success.
 */
export function LockCountdown({ locksAt, locked }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (locked) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, [locked]);

  if (locked) {
    return (
      <Badge bg="secondary" title="Locked" aria-label="Locked">
        <LockIcon />
      </Badge>
    );
  }

  const remaining = new Date(locksAt).getTime() - now;
  if (remaining <= 0) {
    return <Badge bg="secondary">Locking</Badge>;
  }

  const minutes = Math.floor(remaining / 60_000);
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;

  let text;
  if (days > 0) text = `${days}d ${hours}h`;
  else if (hours > 0) text = `${hours}h ${mins}m`;
  else text = `${mins}m`;

  return (
    <Badge
      bg={minutes < 60 ? 'warning' : 'secondary-subtle'}
      text={minutes < 60 ? 'dark' : 'secondary-emphasis'}
    >
      Locks in {text}
    </Badge>
  );
}

export function formatKickoff(kickoff, startTimeTbd) {
  if (startTimeTbd) {
    return `${new Date(kickoff).toLocaleDateString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
    })} · time TBD`;
  }
  return new Date(kickoff).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}
