import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Col, Container, ProgressBar, Row } from 'react-bootstrap';
import { useSearchParams } from 'react-router-dom';

import GameCard from '../components/GameCard.jsx';
import GameFilters from '../components/GameFilters.jsx';
import LineRefreshCountdown from '../components/LineRefreshCountdown.jsx';
import WeekSelector from '../components/WeekSelector.jsx';
import DaySelector, { formatDay } from '../components/DaySelector.jsx';
import { EmptyState, ErrorNotice, Loading, isPickable, marketLabel, marketsOf } from '../components/common.jsx';
import { api } from '../api/client.js';
import { NoGroupNotice } from '../components/common.jsx';
import { useGroup } from '../auth/GroupProvider.jsx';

const NO_FILTERS = {
  conference: null,
  teamId: null,
  status: null,
  minSpread: null,
  maxSpread: null,
  mine: false,
  pickableOnly: false,
  todayOnly: false,
};

/** Today as yyyy-mm-dd in the browser's own zone, not UTC. */
const today = () => new Date().toLocaleDateString('en-CA');

/**
 * The day a daily board should open on: today when it has games, otherwise
 * the next day that does - landing on an empty Tuesday would be a poor
 * greeting. Falls back to the last day of the season once it is over.
 */
const currentGameDay = (days) => {
  const now = today();
  return days.find((value) => value >= now) ?? days[days.length - 1] ?? now;
};

export default function GamesPage() {
  const { groupId, group, hasNoGroups } = useGroup();

  // A daily group spends its allowance per game day, so its board is a day
  // rather than a week - a week would mix several allowances together.
  const daily = group?.cadence === 'DAILY';

  // Which markets this group plays, so the board only offers buttons that
  // can actually be picked. Memoised: a fresh object each render would
  // invalidate the filtered list every time.
  const markets = useMemo(() => marketsOf(group), [group]);

  const [searchParams, setSearchParams] = useSearchParams();

  const [meta, setMeta] = useState(null);
  // Seeded from the URL so returning here - from a game's details, or from a
  // reload - lands on the period that was being read rather than snapping
  // back to the current week. Read once, in the initialiser: after that the
  // state is the source of truth and the URL is written from it below.
  const [week, setWeek] = useState(() => {
    const fromUrl = searchParams.get('week');
    return fromUrl == null ? null : Number(fromUrl);
  });
  const [games, setGames] = useState([]);
  const [filterOptions, setFilterOptions] = useState(null);
  const [filters, setFilters] = useState({
    ...NO_FILTERS,
    mine: searchParams.get('mine') === '1',
  });
  const [picksUsed, setPicksUsed] = useState(0);
  const [maxPicks, setMaxPicks] = useState(null);
  const [cadence, setCadence] = useState('WEEKLY');
  // Null whenever no single number describes what is left - see WeekPicks.
  const [picksRemaining, setPicksRemaining] = useState(null);
  // What this period still requires: the overall minimum, and one entry per
  // enabled market with its own used/min/max.
  const [minPicks, setMinPicks] = useState(0);
  const [marketBudgets, setMarketBudgets] = useState([]);
  // Daily boards only: the selected day, and the days that have games.
  const [day, setDay] = useState(() => searchParams.get('date'));
  const [gameDays, setGameDays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busyGameId, setBusyGameId] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  // Distinguishes the first load of a week (show a spinner) from a pick
  // (leave the board on screen).
  const loadedWeek = useRef(null);

  useEffect(() => {
    api
      .currentWeek()
      .then((data) => {
        setMeta(data);
        // Only when the URL did not already name one - otherwise arriving
        // back on this page would overwrite the week being returned to.
        setWeek((current) => current ?? data.week);
      })
      .catch(setError);
  }, []);

  useEffect(() => {
    if (!daily || !meta) return;

    api
      .gameDays({ season: meta.season })
      .then((days) => {
        setGameDays(days);
        setDay((current) => current ?? currentGameDay(days));
      })
      .catch(() => setGameDays([]));
  }, [daily, meta]);

  const loadWeek = useCallback(async () => {
    // The board is group-scoped, so there is nothing to ask for until the
    // selection has resolved. A daily board additionally waits for its day.
    if (!meta || !groupId) return;
    if (daily ? !day : week === null) return;

    const period = daily ? day : week;
    if (loadedWeek.current !== period) setLoading(true);
    setError(null);

    try {
      const [weekGames, picks, options] = await Promise.all([
        daily
          ? api.games({ groupId, date: day })
          : api.games({ groupId, season: meta.season, week }),
        // A daily group asks by day, which is the period its allowance is
        // actually measured in - by week it could only return a number that
        // contradicts what picking enforces.
        api.myPicks(daily
          ? { groupId, season: meta.season, date: day }
          : { groupId, season: meta.season, week }),
        daily
          ? api.gameFilters({ date: day })
          : api.gameFilters({ season: meta.season, week }),
      ]);
      setGames(weekGames);
      setPicksUsed(picks.picksUsed);
      setMaxPicks(picks.maxPicks);
      setCadence(picks.cadence);
      setPicksRemaining(picks.picksRemaining);
      setMinPicks(picks.minPicks ?? 0);
      setMarketBudgets(picks.markets ?? []);
      setFilterOptions(options);
      loadedWeek.current = period;
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [daily, day, groupId, meta, week]);

  useEffect(() => {
    loadWeek();
  }, [loadWeek]);

  // A manual refresh of the week already on screen - loadWeek only flips
  // the full-page spinner for a week it has not shown yet, so this is its
  // own indicator for re-fetching the one already displayed.
  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await loadWeek();
    } finally {
      setRefreshing(false);
    }
  };

  // A scoreboard that does not move is not a scoreboard. While any game on
  // this week's board is in progress, pull the games array again every 30
  // seconds - the server caches ESPN for 15, so this is roughly as fresh as
  // the data gets.
  const anyLive = games.some((game) => game.live);

  useEffect(() => {
    if (!anyLive || !meta || week === null) return undefined;

    const timer = setInterval(async () => {
      // Never while a pick is in flight: the response would be built from
      // state the server has not applied yet and would undo the card.
      if (busyGameId !== null) return;
      try {
        const refreshed = daily
          ? await api.games({ groupId, date: day })
          : await api.games({ groupId, season: meta.season, week });
        setGames(refreshed);
      } catch {
        // A dropped poll is not worth an error banner - the next one is 30
        // seconds away and the board on screen is still valid.
      }
    }, 30_000);

    return () => clearInterval(timer);
  }, [anyLive, busyGameId, daily, day, groupId, meta, week]);

  // Reset the filters when the period changes - a spread band or a team
  // chosen for one week rarely means anything for the next. "My picks" is the
  // exception: it is a view of the board rather than a filter of one week's.
  useEffect(() => {
    setFilters((current) => ({ ...NO_FILTERS, mine: current.mine }));
  }, [week, day]);

  /**
   * The period and the "my picks" view live in the URL so this page can be
   * linked to, survive a reload, and - the reason it was added - be returned
   * to by the browser's Back button showing the same week it was left on.
   *
   * <p>Written from state in one place rather than from each setter, so the
   * two cannot disagree, and replaced rather than pushed: stepping through
   * weeks should not fill the history with entries Back has to walk out of.
   */
  useEffect(() => {
    const next = {};
    if (filters.mine) next.mine = '1';
    if (daily) {
      if (day) next.date = day;
    } else if (week !== null) {
      next.week = String(week);
    }
    setSearchParams(next, { replace: true });
  }, [daily, day, filters.mine, setSearchParams, week]);

  const updateFilters = (next) => setFilters(next);

  // Back to the period the season is actually on. Hidden when already there,
  // so the control only appears when it has somewhere to go.
  const nowPeriod = daily ? currentGameDay(gameDays) : meta?.week ?? null;
  const atNow = daily ? day === nowPeriod : week === nowPeriod;
  const jumpToNow = () => (daily ? setDay(nowPeriod) : setWeek(nowPeriod));

  const visibleGames = useMemo(() => {
    const matched = games.filter((game) => {
      if (filters.mine
          && !game.mySpreadPick && !game.myTotalPick && !game.myMoneylinePick) {
        return false;
      }
      // Literal: a locked game is not actionable even if it was already
      // picked, which is what "still pick" means.
      if (filters.pickableOnly && !isPickable(game, markets)) return false;
      // Local calendar day, not UTC - a kickoff at 11pm local should not
      // fall off "today" just because it is already tomorrow in UTC.
      // Ignored on a daily board: the control is hidden there, and a filter
      // nobody can see or turn off would silently empty the page.
      if (!daily && filters.todayOnly
          && new Date(game.kickoff).toDateString() !== new Date().toDateString()) {
        return false;
      }
      if (filters.status && game.status !== filters.status) return false;
      if (filters.conference) {
        // Read from the game, not the team record: non-FBS opponents have a
        // conference here but no team row to hang it off.
        if (![game.homeConference, game.awayConference].includes(filters.conference)) {
          return false;
        }
      }
      if (filters.teamId) {
        const ids = [game.homeTeam?.id, game.awayTeam?.id].map(String);
        if (!ids.includes(String(filters.teamId))) return false;
      }
      if (filters.minSpread != null || filters.maxSpread != null) {
        // A game with no line has no spread size, so it cannot satisfy a
        // spread filter - dropping it is less confusing than showing it.
        if (game.homeSpread == null) return false;
        const size = Math.abs(Number(game.homeSpread));
        if (filters.minSpread != null && size < filters.minSpread) return false;
        if (filters.maxSpread != null && size > filters.maxSpread) return false;
      }
      return true;
    });

    // Finished games sink to the bottom. The board is a place to make picks,
    // and a game that has ended is the one thing on it nobody can act on -
    // by the back half of a Saturday the pickable games were buried under
    // results. Sorted, not filtered: the scores are still worth reading, just
    // not first.
    //
    // Canceled counts as finished for this purpose. It is equally
    // unpickable, and grouping it with the results keeps the live and
    // upcoming games in one unbroken run at the top.
    //
    // Only the done/not-done key is compared, and Array#sort is stable, so
    // kickoff order - the order the server sent - survives inside each half.
    const done = (game) => (game.status === 'FINAL' || game.status === 'CANCELED' ? 1 : 0);
    return [...matched].sort((a, b) => done(a) - done(b));
  }, [games, filters, daily, markets]);

  /**
   * Applies a pick without reloading the board. `action` resolves straight
   * to the mutation's own updated GameSummary - create/update/relock/delete
   * all return it inline now, so there is no follow-up GET to make (and
   * nothing for a dropped connection on a second request to falsely blame
   * the pick for).
   */
  const applyPick = async (game, action, delta) => {
    setBusyGameId(game.id);
    setNotice(null);
    try {
      const updatedGame = await action();
      setGames((current) =>
        current.map((row) => (row.id === game.id ? updatedGame : row)),
      );
      setPicksUsed((current) => current + delta);
    } catch (err) {
      if (err.code === 'LINE_MOVED') {
        // The board was stale. Pull the real number in and let them decide
        // rather than committing them to a line they never saw.
        setNotice({
          variant: 'warning',
          text: `${err.message}. The card now shows the current line - pick again if you still want it.`,
        });
        const refreshed = await api.game(game.id, { groupId }).catch(() => null);
        if (refreshed) {
          setGames((current) =>
            current.map((row) => (row.id === game.id ? refreshed.game : row)),
          );
        }
      } else {
        setNotice({
          variant: err.code === 'WEEKLY_LIMIT_REACHED' ? 'warning' : 'danger',
          text: err.message,
        });
        if (err.code === 'PICK_WINDOW_CLOSED' || err.code === 'WEEKLY_LIMIT_REACHED') {
          loadedWeek.current = null;
          await loadWeek();
        }
      }
    } finally {
      setBusyGameId(null);
    }
  };

  // Which of the caller's picks and which line a selection belongs to.
  const marketOf = (selection) =>
    selection === 'OVER' || selection === 'UNDER' ? 'TOTAL' : 'SPREAD';
  const pickFor = (game, selection) =>
    marketOf(selection) === 'TOTAL' ? game.myTotalPick : game.mySpreadPick;
  const lineFor = (game, selection) =>
    marketOf(selection) === 'TOTAL' ? game.overUnder : game.homeSpread;

  const handlePick = (game, selection) => {
    const existing = pickFor(game, selection);
    const line = lineFor(game, selection);

    return applyPick(
      game,
      () =>
        (existing
          ? api.updatePick(groupId, existing.id, selection, line)
          : api.createPick(groupId, game.id, selection, line)
        ).then((response) => response.game),
      // Switching sides within a market spends nothing extra.
      existing ? 0 : 1,
    );
  };

  const handleClear = (game, selection) =>
    // deletePick resolves straight to the GameSummary - there is no pick
    // left to wrap it in.
    applyPick(game, () => api.deletePick(groupId, pickFor(game, selection).id), -1);

  const handleRelock = (game, pick) =>
    applyPick(game, () => api.relockPick(groupId, pick.id).then((response) => response.game), 0);

  // Null only when the group sets no maximum at all. A daily group used to
  // land here too - a week holds several of its allowances, so no single
  // number described what was left - but the board now asks by day, which is
  // the period the allowance is actually measured in.
  const remaining = picksRemaining;
  const perPeriod = cadence === 'DAILY' ? 'day' : 'week';

  // Minimums this period has not met yet, phrased as what is left to do rather
  // than as the rule. A minimum is charged as losses when the period closes -
  // see CadenceSettlementService - so saying it only afterwards would be the
  // one time it could not help.
  const outstanding = [
    ...marketBudgets
      .filter((budget) => budget.min > 0 && budget.used < budget.min)
      .map((budget) => `${budget.min - budget.used} more ${marketLabel(budget.market).toLowerCase()}`),
    // The overall minimum is separate: a member can satisfy every market and
    // still be short of the total the group asks for.
    ...(minPicks > picksUsed ? [`${minPicks - picksUsed} more in total`] : []),
  ];
  const weekNotLoaded =
    meta && week !== null && !meta.loadedWeeks?.includes(week) && games.length === 0;

  if (hasNoGroups) {
    return (
      <Container className="py-4 py-md-5">
        <NoGroupNotice />
      </Container>
    );
  }

  return (
    <>
      {/* Pinned so the pick budget stays visible while scrolling a long board.
          A group with no maximum has no budget to show, so the bar becomes a
          plain count rather than a progress bar to nowhere. */}
      <div className="picks-bar border-bottom">
        <Container className="py-2">
          <div className="d-flex justify-content-between align-items-center gap-3 small">
            <span className="fw-semibold text-nowrap">
              {/* A running total against the cap, or a plain count for a group
                  that sets none. */}
              {remaining == null ? `${picksUsed} picks` : `${picksUsed} / ${maxPicks} picks`}
              <span className="d-none d-sm-inline text-body-secondary fw-normal">
                {' '}
                · {daily ? formatDay(day) : `week ${week ?? '-'}`}
              </span>
            </span>

            {/* Beside the budget rather than in the toolbar: both describe the
                board as a whole, and this bar is the one thing that stays on
                screen while a long slate is scrolled. Hidden on the narrowest
                phones, where the budget and the progress bar have the row. */}
            <LineRefreshCountdown className="d-none d-md-inline ms-auto" />
            {remaining != null && (
              <>
                <ProgressBar
                  now={(picksUsed / maxPicks) * 100}
                  variant={remaining === 0 ? 'secondary' : 'primary'}
                  style={{ height: 6, flex: 1 }}
                  aria-label={`${picksUsed} of ${maxPicks} picks used`}
                />
                <span className="text-body-secondary text-nowrap">{remaining} left</span>
              </>
            )}
          </div>

          {/* What this period will charge if it closes as it stands. Only
              rendered when something is actually outstanding - a member who
              has met every minimum should not be reading about minimums. */}
          {outstanding.length > 0 && (
            <div className="small text-warning-emphasis mt-1">
              Still needed this {perPeriod}: {outstanding.join(', ')}
              <span className="d-none d-md-inline text-body-secondary">
                {' '}
                — anything short when the {perPeriod} closes counts as a loss.
              </span>
            </div>
          )}
        </Container>
      </div>

      {/* Tight to the sticky bars above - there is no heading between them
          and the picker any more, so the usual page padding left a gap with
          nothing in it. */}
      <Container className="pt-2 pb-4">
        {/* No heading: the picker below already names the week or day, and
            repeating it above only pushed the board further down.

            Always rendered, even for a period with no games - it carries the
            picker, which is how you get to one that has some. */}
        <GameFilters
          weekSelector={
            <>
              {daily ? (
                <DaySelector compact value={day} onChange={setDay} days={gameDays} />
              ) : (
                <WeekSelector
                  compact
                  weeks={meta?.availableWeeks}
                  current={week}
                  onChange={setWeek}
                />
              )}

              {/* Browsing forward a few weeks and then finding the way back
                  meant remembering which week "now" was. Only rendered when
                  it would actually move the board. */}
              {nowPeriod != null && !atNow && (
                <Button
                  variant="link"
                  size="sm"
                  className="p-0 mt-1 text-decoration-none"
                  onClick={jumpToNow}
                >
                  {daily ? `Jump to ${formatDay(nowPeriod)}` : `Jump to week ${nowPeriod}`}
                </Button>
              )}
            </>
          }
          options={filterOptions}
          value={filters}
          onChange={updateFilters}
          resultCount={visibleGames.length}
          totalCount={games.length}
          daily={daily}
          onRefresh={handleRefresh}
          refreshing={loading || refreshing}
        />

        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}
        <ErrorNotice error={error} onRetry={loadWeek} />

        {loading ? (
          <Loading label="Loading games" />
        ) : !daily && weekNotLoaded ? (
          <EmptyState title={`Week ${week} has not been loaded yet`}>
            <p className="small mb-0">
              An admin can pull it in from Admin → Data. Schedules and lines for future weeks are
              usually posted a week or two ahead.
            </p>
          </EmptyState>
        ) : games.length === 0 ? (
          <EmptyState title={daily ? 'No games on this day' : 'No games this week'}>
            {daily && (
              <p className="small mb-0">
                Use the arrows to step to another day, or the date field to jump.
              </p>
            )}
          </EmptyState>
        ) : visibleGames.length === 0 ? (
          <EmptyState
            title={
              filters.pickableOnly && !filters.mine && games.length > 0
                ? `Every game ${daily ? 'today' : 'this week'} has locked`
                : filters.mine
                  ? 'No picks in this week yet'
                  : 'No games match these filters'
            }
          >
            <Button onClick={() => updateFilters(NO_FILTERS)}>Show all games</Button>
          </EmptyState>
        ) : (
          <Row xs={1} md={2} xl={3} className="g-3 g-md-4">
              {visibleGames.map((game) => (
                <Col key={game.id}>
                  <GameCard
                    game={game}
                    markets={markets}
                    busy={busyGameId === game.id}
                    onPick={handlePick}
                    onClear={handleClear}
                    onRelock={handleRelock}
                  />
                </Col>
              ))}
          </Row>
        )}
      </Container>
    </>
  );
}
