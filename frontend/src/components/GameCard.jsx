import { Badge, Button, Card, Spinner } from 'react-bootstrap';
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
   * @param mine        true once this side already holds a pick - governs
   *                    the aria-label wording, independent of whether the
   *                    line has since moved
   * @param lineMatches true when the board's current number still equals
   *                    what was locked in - the button only reads as
   *                    "selected" when both are true, so a moved line never
   *                    shows a number the member did not actually take
   * @param marketTaken true once *either* side of this market holds a pick.
   *                    The button is disabled whenever this is true,
   *                    matching side included - modifying a pick now goes
   *                    through the cancel button in the picks panel rather
   *                    than clicking a button here, so there is one place
   *                    that does it instead of two different ones.
   */
  const marketButton = ({ selection, label, ariaLabel, mine, lineMatches, marketTaken, disabled }) => (
    <Button
      size="sm"
      variant={mine && lineMatches ? 'primary' : 'outline-secondary'}
      className="flex-shrink-0 fw-semibold pick-button"
      disabled={disabled || marketTaken || locked || busy}
      onClick={() => onPick?.(game, selection)}
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
          marketTaken: Boolean(spreadPick),
          disabled: game.homeSpread == null,
          ariaLabel: spreadMine
            ? `Your spread pick: ${teamName} ${formatSpread(game.homeSpread, side)}`
            : spreadPick
              ? `${teamName} at ${formatSpread(game.homeSpread, side)} - cancel your current spread pick first`
              : `Pick ${teamName} at ${formatSpread(game.homeSpread, side)}`,
        })}

        {marketButton({
          selection: totalSide,
          label: formatTotal(game.overUnder, totalSide),
          mine: totalMine,
          lineMatches: totalLineMatches,
          marketTaken: Boolean(totalPick),
          disabled: game.overUnder == null,
          ariaLabel: totalMine
            ? `Your total pick: ${formatTotal(game.overUnder, totalSide)}`
            : totalPick
              ? `${totalSide.toLowerCase()} ${game.overUnder} - cancel your current total pick first`
              : `Pick ${totalSide.toLowerCase()} ${game.overUnder} total points in ${matchup}`,
        })}
      </div>
    );
  };

  /**
   * One market's row in the "Your picks" panel: what you took, and the two
   * things you can do about it - cancel outright, or (only when the board
   * has since moved in your favor) update to the better number. Spread and
   * total each get their own row so one can be changed without touching the
   * other.
   */
  const pickRow = ({ marketLabel, pick, currentLine, improved, format, withTeamName }) => {
    if (!pick) return null;

    const yourLabel = withTeamName
      ? `${pick.selection === 'HOME' ? game.homeTeamName : game.awayTeamName} ${format(pick.lockedLine, pick.selection)}`
      : format(pick.lockedLine, pick.selection);

    return (
      <div className="d-flex justify-content-between align-items-center gap-2 flex-wrap">
        <div className="d-flex align-items-center gap-2 min-width-0">
          <Badge
            bg="secondary-subtle"
            text="secondary-emphasis"
            className="text-uppercase fw-semibold"
          >
            {marketLabel}
          </Badge>
          <span className="fw-semibold text-truncate">{yourLabel}</span>
        </div>

        {finished ? (
          <ResultBadge result={pick.result} />
        ) : (
          <div className="d-flex gap-2 flex-shrink-0">
            {improved && (
              <Button
                size="sm"
                variant="warning"
                disabled={busy}
                onClick={() => onRelock?.(game, pick)}
              >
                Update to {format(currentLine, pick.selection)}
              </Button>
            )}
            <Button
              size="sm"
              variant="outline-danger"
              disabled={locked || busy}
              onClick={() => onClear?.(game, pick.selection)}
              aria-label={`Cancel your ${marketLabel === 'SPR' ? 'spread' : 'total'} pick`}
              title="Cancel this pick"
            >
              ✕
            </Button>
          </div>
        )}
      </div>
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

  const spreadRow = pickRow({
    marketLabel: 'SPR',
    pick: spreadPick,
    currentLine: game.homeSpread,
    improved: game.spreadLineImproved,
    format: formatSpread,
    withTeamName: true,
  });
  const totalRow = pickRow({
    marketLabel: 'O/U',
    pick: totalPick,
    currentLine: game.overUnder,
    improved: game.totalLineImproved,
    format: formatTotal,
    withTeamName: false,
  });

  return (
    <Card className={`pick-card h-100 shadow-sm${locked && !finished ? ' is-locked' : ''}`}>
      <Card.Body className="d-flex flex-column gap-2 gap-sm-3 p-2 p-sm-3">
        {/* Game details on the left, state on the right. While the game is
            being played the right-hand column also carries what this member
            is holding, so the clock and the stake read together. */}
        <div className="d-flex justify-content-between align-items-start gap-2">
          <div className="small text-body-secondary lh-sm">
            <div>{formatKickoff(game.kickoff, game.startTimeTbd)}</div>
            {game.venue && (
              <div className="text-body-tertiary text-truncate d-none d-sm-block" title={game.venue}>
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

        {/* "Your picks" - one row per market held on this game. Each row
            manages its own market independently: cancel either pick without
            touching the other, and update to a better line only where one
            actually moved that way. */}
        {(spreadRow || totalRow) && (
          <div className="d-grid gap-1 gap-sm-2">
            {spreadRow && (
              <div
                className={`rounded-3 p-1 p-sm-2 ${
                  game.spreadLineImproved ? 'bg-warning-subtle' : 'bg-body-tertiary'
                }`}
              >
                {spreadRow}
              </div>
            )}
            {totalRow && (
              <div
                className={`rounded-3 p-1 p-sm-2 ${
                  game.totalLineImproved ? 'bg-warning-subtle' : 'bg-body-tertiary'
                }`}
              >
                {totalRow}
              </div>
            )}
          </div>
        )}

        {finished && (
          <div className="small text-body-secondary">
            {game.awayTeamName} {game.awayScore} · {game.homeTeamName} {game.homeScore}
          </div>
        )}

        {game.homeSpread == null && game.overUnder == null && (
          <div className="small text-body-tertiary">No lines posted yet - not pickable.</div>
        )}

        <div className="d-flex justify-content-between align-items-center mt-auto">
          <Link to={`/games/${game.id}`} className="small text-decoration-none">
            Game details →
          </Link>
          <div className="d-flex align-items-center gap-2">
            {busy && <Spinner animation="border" size="sm" />}
            <span className="small text-body-tertiary" title="Game ID">
              #{game.id}
            </span>
          </div>
        </div>
      </Card.Body>
    </Card>
  );
}
