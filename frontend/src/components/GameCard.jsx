import { Alert, Badge, Button, Card, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { TeamLink } from './links.jsx';
import {
  LockCountdown,
  ResultBadge,
  formatKickoff,
  formatLine,
  formatSpread,
  formatTotal,
} from './common.jsx';

/**
 * One game: two teams, a spread button each, and an over/under button each.
 *
 * <p>Team names sit outside the buttons on purpose. A name is a link to the
 * team page, and an anchor nested inside a button is both invalid HTML and
 * unreachable - the button swallows the click.
 *
 * <p>The O/U column pairs Over with the away row and Under with the home row
 * purely for layout. The buttons carry an `O`/`U` prefix and a market-naming
 * aria-label so nothing implies the total belongs to a particular team.
 */
export default function GameCard({ game, onPick, onClear, onRelock, busy = false }) {
  const spreadPick = game.mySpreadPick;
  const totalPick = game.myTotalPick;
  const locked = game.locked;
  const finished = game.status === 'FINAL';
  // Present only while ESPN has the game in progress, so its presence is the
  // liveness test - there is no separate flag to keep in step with it.
  const live = game.live;

  const matchup = `${game.awayTeamName} at ${game.homeTeamName}`;

  const hasBall = (team) =>
    live?.possessionTeamId != null && String(live.possessionTeamId) === String(team?.id);

  /**
   * @param mine        true once this side already holds a pick - governs the
   *                    click action (clear) and the aria-label wording,
   *                    independent of whether the line has since moved
   * @param lineMatches true when the board's current number still equals
   *                    what was locked in - the button only reads as
   *                    "selected" when both are true, so a moved line never
   *                    shows a number the member did not actually take.
   *                    Re-locking to a better line goes through the "Take X"
   *                    button in the alert below, not this one.
   */
  const marketButton = ({ selection, label, ariaLabel, mine, lineMatches, disabled }) => (
    <Button
      size="sm"
      variant={mine && lineMatches ? 'primary' : 'outline-secondary'}
      className="flex-shrink-0 fw-semibold pick-button"
      disabled={disabled || locked || busy}
      onClick={() => (mine ? onClear?.(game, selection) : onPick?.(game, selection))}
      aria-pressed={mine && lineMatches}
      aria-label={ariaLabel}
    >
      {label}
    </Button>
  );

  /** One team row: name, its spread button, and one side of the total. */
  const sideRow = (side, team, fallbackName, totalSide) => {
    const teamName = team?.school ?? fallbackName;
    const spreadMine = spreadPick?.selection === side;
    const spreadLineMatches =
      spreadMine && String(spreadPick.lockedLine) === String(game.homeSpread);
    const totalMine = totalPick?.selection === totalSide;
    const totalLineMatches = totalMine && String(totalPick.lockedLine) === String(game.overUnder);
    const liveScore = side === 'HOME' ? live?.homeScore : live?.awayScore;

    return (
      <div className="d-flex align-items-center gap-2">
        <div className="flex-grow-1 text-truncate d-flex align-items-center gap-1">
          <TeamLink team={team} name={fallbackName} logoSize={22} />
          {/* A football beside whoever has the ball. Decorative - the score
              and the clock carry the state for a screen reader. */}
          {hasBall(team) && (
            <span aria-hidden="true" title="Has possession">
              🏈
            </span>
          )}
        </div>

        {liveScore != null && (
          <span className="fw-bold fs-5 lh-1" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {liveScore}
          </span>
        )}

        {marketButton({
          selection: side,
          label: formatSpread(game.homeSpread, side),
          mine: spreadMine,
          lineMatches: spreadLineMatches,
          disabled: game.homeSpread == null,
          ariaLabel: spreadMine
            ? `Remove your spread pick on ${teamName}`
            : `Pick ${teamName} at ${formatSpread(game.homeSpread, side)}`,
        })}

        {marketButton({
          selection: totalSide,
          label: formatTotal(game.overUnder, totalSide),
          mine: totalMine,
          lineMatches: totalLineMatches,
          disabled: game.overUnder == null,
          ariaLabel: totalMine
            ? `Remove your ${totalSide.toLowerCase()} pick on ${matchup}`
            : `Pick ${totalSide.toLowerCase()} ${game.overUnder} total points in ${matchup}`,
        })}
      </div>
    );
  };

  /**
   * What you're actually holding on this market, always shown once a pick
   * exists - the buttons above only read as "selected" when the line still
   * matches, so this is the one place the number taken is never in doubt.
   * Appends where the board sits now, if that has since moved.
   */
  const pickSummary = (pick, currentLine, improved, format) => {
    if (!pick) return null;
    const lineMoved =
      !finished && currentLine != null && String(pick.lockedLine) !== String(currentLine);

    return (
      <span className="text-body-secondary">
        {pick.market === 'TOTAL' ? 'Total' : 'Spread'}: you took{' '}
        <strong className="text-body">{format(pick.lockedLine, pick.selection)}</strong>
        {lineMoved && (
          <>
            , now{' '}
            <strong className={improved ? 'text-success' : 'text-body'}>
              {format(currentLine, pick.selection)}
            </strong>
          </>
        )}
      </span>
    );
  };

  /**
   * What this member is holding, shown while the game is on.
   *
   * <p>A market they skipped still gets a chip, muted, showing the side the
   * board would have handed them: the favourite against the spread and the
   * over on the total. Watching a game you did not pick is more interesting
   * with a number attached to it, and the muted styling keeps that number
   * from being mistaken for a pick.
   */
  const pickChip = (pick, fallbackLabel, key) => {
    const mine = Boolean(pick);
    const label = mine ? formatLine(pick.lockedLine, pick.selection) : fallbackLabel;
    if (label == null) return null;

    const name = mine
      ? pick.selection === 'HOME'
        ? game.homeTeamName
        : pick.selection === 'AWAY'
          ? game.awayTeamName
          : null
      : null;

    return (
      <Badge
        key={key}
        bg={mine ? 'primary' : 'secondary-subtle'}
        text={mine ? undefined : 'secondary-emphasis'}
        className="fw-normal text-truncate"
        title={mine ? 'Your pick' : 'Not picked - showing the default side'}
      >
        {name ? `${name} ${label}` : label}
      </Badge>
    );
  };

  /** The favourite and their number, as "Texas -7.5". */
  const favouriteLabel = () => {
    if (game.homeSpread == null) return null;
    const spread = Number(game.homeSpread);
    if (spread === 0) return `Pick'em ${formatSpread(0, 'HOME')}`;
    const side = spread < 0 ? 'HOME' : 'AWAY';
    const name = side === 'HOME' ? game.homeTeamName : game.awayTeamName;
    return `${name} ${formatSpread(game.homeSpread, side)}`;
  };

  const spreadNote = pickSummary(
    spreadPick,
    game.homeSpread,
    game.spreadLineImproved,
    formatSpread,
  );
  const totalNote = pickSummary(totalPick, game.overUnder, game.totalLineImproved, formatTotal);

  const improvedPicks = [
    game.spreadLineImproved && spreadPick
      ? { pick: spreadPick, label: formatSpread(game.homeSpread, spreadPick.selection) }
      : null,
    game.totalLineImproved && totalPick
      ? { pick: totalPick, label: formatTotal(game.overUnder, totalPick.selection) }
      : null,
  ].filter(Boolean);

  return (
    <Card className={`pick-card h-100 shadow-sm${locked && !finished ? ' is-locked' : ''}`}>
      <Card.Body className="d-flex flex-column gap-3">
        {/* Game details on the left, state on the right. While the game is
            being played the right-hand column also carries what this member
            is holding, so the clock and the stake read together. */}
        <div className="d-flex justify-content-between align-items-start gap-2">
          <div className="small text-body-secondary">
            <div>{formatKickoff(game.kickoff, game.startTimeTbd)}</div>
            {game.venue && (
              <div className="text-body-tertiary text-truncate" title={game.venue}>
                {game.venue}
              </div>
            )}
            {live?.downDistance && (
              <div className={`text-truncate ${live.redZone ? 'text-danger fw-semibold' : ''}`}>
                {live.downDistance}
              </div>
            )}
          </div>

          <div className="d-flex flex-column align-items-end gap-1 flex-shrink-0">
            {live ? (
              <Badge bg="danger" className="d-inline-flex align-items-center gap-1">
                <span className="live-dot" aria-hidden="true" />
                {[live.periodLabel, live.clock].filter(Boolean).join(' · ')
                  || live.detail
                  || 'Live'}
              </Badge>
            ) : finished ? (
              <Badge bg="dark">Final</Badge>
            ) : (
              <LockCountdown locksAt={game.locksAt} locked={locked} />
            )}

            {live && (
              <div className="d-flex flex-wrap justify-content-end gap-1 small">
                {pickChip(spreadPick, favouriteLabel(), 'spread')}
                {pickChip(
                  totalPick,
                  game.overUnder == null ? null : formatTotal(game.overUnder, 'OVER'),
                  'total',
                )}
              </div>
            )}
          </div>
        </div>

        <div className="d-grid gap-2">
          {/* Column headings. aria-hidden because each button already names
              its own market and number. */}
          <div className="d-flex align-items-center gap-2" aria-hidden="true">
            <div className="flex-grow-1" />
            <div className="pick-col text-center small fw-semibold text-body-tertiary text-uppercase lh-1">
              SPR
            </div>
            <div className="pick-col text-center small fw-semibold text-body-tertiary text-uppercase lh-1">
              O/U
            </div>
          </div>

          {sideRow('AWAY', game.awayTeam, game.awayTeamName, 'OVER')}
          {sideRow('HOME', game.homeTeam, game.homeTeamName, 'UNDER')}
        </div>

        {(spreadNote || totalNote) && (
          <div className="d-grid gap-1 small">
            {spreadNote}
            {totalNote}
          </div>
        )}

        {improvedPicks.length > 0 && (
          <Alert variant="success" className="py-2 px-3 mb-0 d-flex flex-column gap-2">
            <span className="small">
              {improvedPicks.length > 1
                ? 'Both lines moved your way.'
                : 'The line moved your way.'}
            </span>
            {improvedPicks.map(({ pick, label }) => (
              <Button
                key={pick.id}
                size="sm"
                variant="success"
                disabled={busy}
                onClick={() => onRelock?.(game, pick)}
              >
                Take {label}
              </Button>
            ))}
          </Alert>
        )}

        {finished && (
          <div className="d-flex justify-content-between align-items-center small gap-2">
            <span>
              {game.awayTeamName} {game.awayScore} · {game.homeTeamName} {game.homeScore}
            </span>
            <span className="d-flex gap-1">
              {spreadPick && <ResultBadge result={spreadPick.result} />}
              {totalPick && <ResultBadge result={totalPick.result} />}
            </span>
          </div>
        )}

        {game.homeSpread == null && game.overUnder == null && (
          <div className="small text-body-tertiary">No lines posted yet - not pickable.</div>
        )}

        <div className="d-flex justify-content-between align-items-center mt-auto pt-1">
          <Link to={`/games/${game.id}`} className="small text-decoration-none">
            Game details →
          </Link>
          {busy && <Spinner animation="border" size="sm" />}
        </div>
      </Card.Body>
    </Card>
  );
}
