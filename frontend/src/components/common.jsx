import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Spinner } from 'react-bootstrap';
import { useLocation, useNavigate } from 'react-router-dom';

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

export function EmptyState({ title, children }) {
  return (
    <div className="text-center text-body-secondary py-5">
      <p className="mb-2 fw-semibold">{title}</p>
      {children}
    </div>
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

/** Formats whichever line a pick was made against. */
export function formatLine(line, selection) {
  return selection === 'OVER' || selection === 'UNDER'
    ? formatTotal(line, selection)
    : formatSpread(line, selection);
}

/** Human label for a market. */
export function marketLabel(market) {
  return market === 'TOTAL' ? 'Total' : 'Spread';
}

/**
 * Whether anything on this game can still be picked.
 *
 * <p>Two ways a game drops out: the 30-minute window has closed (which also
 * covers kicked-off, finished and TBD-kickoff games), or neither market has a
 * number posted. A game with only one of the two lines is still pickable -
 * just not in both markets.
 *
 * <p>Defined once because the card's disabled buttons and the "hide
 * unpickable" filter have to agree; a game the filter keeps but the card
 * cannot act on is worse than either behaviour alone.
 */
export function isPickable(game) {
  if (game.locked) return false;
  return game.homeSpread != null || game.overUnder != null;
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
    return <Badge bg="secondary">Locked</Badge>;
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
    <Badge bg={minutes < 60 ? 'warning' : 'light'} text={minutes < 60 ? 'dark' : 'secondary'}>
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
