import { Badge, Button, Card, Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { TeamLink } from './links.jsx';
import {
  LockCountdown,
  ResultBadge,
  formatKickoff,
  formatSpread,
  formatTotal,
  formatTotalLong,
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
export default function GameCard({
  game,
  onPick,
  onClear,
  onRelock,
  busy = false,
  // Hidden on the game details page itself, where the link would just
  // point back to the page already on screen.
  showDetailsLink = true,
}) {
  const navigate = useNavigate();

  const spreadPick = game.mySpreadPick;
  const totalPick = game.myTotalPick;
  const locked = game.locked;
  const finished = game.status === 'FINAL';
  // Present only while ESPN has the game in progress, so its presence is the
  // liveness test - there is no separate flag to keep in step with it.
  const live = game.live;
  const inProgress = Boolean(live);

  const matchup = `${game.awayTeamName} at ${game.homeTeamName}`;

  const hasBall = (team) =>
    live?.possessionTeamId != null && String(live.possessionTeamId) === String(team?.id);

  /**
   * @param mine        true once this side already holds a pick - governs
   *                    the aria-label wording and the coloring below,
   *                    independent of whether the line has since moved
   * @param lineMatches true when the board's current number still equals
   *                    what was locked in
   * @param marketTaken true once *either* side of this market holds a pick.
   *                    The button is disabled whenever this is true,
   *                    matching side included - modifying a pick now goes
   *                    through the cancel button in the picks panel rather
   *                    than clicking a button here, so there is one place
   *                    that does it instead of two different ones.
   * @param result      this pick's graded outcome, once the game is final
   *
   * <p>No solid highlight for "this is picked" - being disabled already says
   * that. The only color a button carries is: a light-blue tint while the
   * pick is live and its line has not moved, or green/red once the game has
   * been graded. A side nobody holds, or one whose line has moved, stays the
   * plain disabled grey.
   */
  const marketButton = ({ selection, label, ariaLabel, mine, lineMatches, marketTaken, disabled,
                          result }) => {
    const stateClass = !mine
      ? ''
      : finished
        ? result === 'WIN'
          ? 'pick-button--win'
          : result === 'LOSS'
            ? 'pick-button--loss'
            : ''
        : lineMatches
          ? 'pick-button--mine'
          : '';

    return (
      <Button
        size="sm"
        variant="outline-secondary"
        className={`flex-shrink-0 fw-semibold pick-button ${stateClass}`}
        disabled={disabled || marketTaken || locked || busy}
        onClick={() => onPick?.(game, selection)}
        aria-pressed={mine}
        aria-label={ariaLabel}
      >
        {label}
      </Button>
    );
  };

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

        {/* Once the game is on, nothing here is actionable - the lines are
            long since locked. Dropping the buttons entirely lets the live
            score sit at the right edge, where the eye goes for a scoreboard,
            instead of squeezed between the name and two dead controls. What
            this member actually holds is shown by the chips up in the header. */}
        {!inProgress && (
          <>
            {marketButton({
              selection: side,
              label: formatSpread(game.homeSpread, side),
              mine: spreadMine,
              lineMatches: spreadLineMatches,
              marketTaken: Boolean(spreadPick),
              disabled: game.homeSpread == null,
              result: spreadPick?.result,
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
              result: totalPick?.result,
              ariaLabel: totalMine
                ? `Your total pick: ${formatTotal(game.overUnder, totalSide)}`
                : totalPick
                  ? `${totalSide.toLowerCase()} ${game.overUnder} - cancel your current total pick first`
                  : `Pick ${totalSide.toLowerCase()} ${game.overUnder} total points in ${matchup}`,
            })}
          </>
        )}
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

        {/* Once locked, neither cancelling nor re-locking is possible - the
            server rejects both - so the controls go rather than sitting there
            greyed out. The pick itself stays on show; it is just no longer
            something you can act on. */}
        {finished ? (
          <ResultBadge result={pick.result} />
        ) : locked ? null : (
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
              disabled={busy}
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
   * The tint on a held pick's row. Carries the pick's state now that the
   * chips beside the clock are gone:
   *
   * <p>graded - green won, red lost, neutral for a push or a void; warning
   * while the board has moved somewhere better and there is an Update button
   * to press; otherwise the blue that says "this one is yours", the same
   * blue the pick buttons use.
   *
   * <p>The greens and reds are Bootstrap's -subtle variants, which stay
   * legible in both light and dark. The blue is ours (see .pick-row--mine in
   * theme.scss) because Bootstrap's equivalent follows the selected theme and
   * would not be blue under Ember or Forest.
   */
  const pickTint = (pick, improved) => {
    if (finished) {
      if (pick?.result === 'WIN') return 'bg-success-subtle';
      if (pick?.result === 'LOSS') return 'bg-danger-subtle';
      return 'bg-body-tertiary';
    }
    // Warning wins over the blue while a better number is on the board -
    // that row has an Update button on it and needs to draw the eye.
    return improved ? 'bg-warning-subtle' : 'pick-row--mine';
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
    // Spelled out here, unlike the buttons above - this row has the room.
    format: formatTotalLong,
    withTeamName: false,
  });

  /**
   * The whole card goes to game details, except the controls that do their
   * own thing - team links, pick buttons, cancel/update, the details link.
   * One capture-phase check on the click's actual target beats wiring
   * stopPropagation onto every control and forgetting the next one added:
   * anything interactive (a link or a button, wherever it sits in the card)
   * simply keeps its own behaviour, and dead space navigates. Skipped on the
   * details page itself, where navigating to the page on screen would just
   * scroll-jump.
   */
  const handleCardClick = (event) => {
    if (!showDetailsLink) return;
    if (event.target.closest('a, button')) return;
    // Respect text selection - a drag to copy a team name is not a click.
    if (window.getSelection?.()?.toString()) return;
    navigate(`/games/${game.id}`);
  };

  return (
    <Card
      className={`pick-card h-100 shadow-sm${locked && !finished ? ' is-locked' : ''}`}
      onClick={handleCardClick}
      style={showDetailsLink ? { cursor: 'pointer' } : undefined}
    >
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
          </div>
        </div>

        <div className="d-grid gap-2">
          {/* Column headings. aria-hidden because each button already names
              its own market and number. Gone once the game is on, along with
              the columns they label. */}
          {!inProgress && (
            <div className="d-flex align-items-center gap-2" aria-hidden="true">
              <div className="flex-grow-1" />
              <div className="pick-col text-center small fw-semibold text-body-tertiary text-uppercase lh-1">
                SPR
              </div>
              <div className="pick-col text-center small fw-semibold text-body-tertiary text-uppercase lh-1">
                O/U
              </div>
            </div>
          )}

          {sideRow('AWAY', game.awayTeam, game.awayTeamName, 'OVER')}
          {sideRow('HOME', game.homeTeam, game.homeTeamName, 'UNDER')}
        </div>

        {/* "Your picks" - one row per market held on this game. Each row
            manages its own market independently: cancel either pick without
            touching the other, and update to a better line only where one
            actually moved that way. */}
        {(spreadRow || totalRow) && (
          <div className="d-flex align-items-stretch gap-2">
            <div
              className="flex-shrink-0 small fw-semibold text-uppercase text-body-tertiary text-center"
              style={{ writingMode: 'vertical-rl', transform: 'rotate(180deg)' }}
            >
              Picks
            </div>
            <div className="d-grid gap-1 gap-sm-2 flex-grow-1">
              {spreadRow && (
                <div
                  className={`rounded-3 p-1 p-sm-2 ${
                    pickTint(spreadPick, game.spreadLineImproved)
                  }`}
                >
                  {spreadRow}
                </div>
              )}
              {totalRow && (
                <div
                  className={`rounded-3 p-1 p-sm-2 ${
                    pickTint(totalPick, game.totalLineImproved)
                  }`}
                >
                  {totalRow}
                </div>
              )}
            </div>
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
          <div className="d-flex align-items-center gap-2">
            <span className="small text-body-tertiary" title="Game ID">
              #{game.id}
            </span>
            {busy && <Spinner animation="border" size="sm" />}
          </div>
          {showDetailsLink && (
            <Link to={`/games/${game.id}`} className="small text-decoration-none">
              Game details →
            </Link>
          )}
        </div>
      </Card.Body>
    </Card>
  );
}
