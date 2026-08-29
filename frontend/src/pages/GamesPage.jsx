import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Col, Container, ProgressBar, Row } from 'react-bootstrap';
import { useSearchParams } from 'react-router-dom';

import GameCard from '../components/GameCard.jsx';
import GameFilters from '../components/GameFilters.jsx';
import WeekSelector from '../components/WeekSelector.jsx';
import { EmptyState, ErrorNotice, Loading, isPickable } from '../components/common.jsx';
import { api } from '../api/client.js';

const NO_FILTERS = {
  conference: null,
  teamId: null,
  minSpread: null,
  maxSpread: null,
  mine: false,
  pickableOnly: false,
};

export default function GamesPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [meta, setMeta] = useState(null);
  const [week, setWeek] = useState(null);
  const [games, setGames] = useState([]);
  const [filterOptions, setFilterOptions] = useState(null);
  const [filters, setFilters] = useState({
    ...NO_FILTERS,
    mine: searchParams.get('mine') === '1',
  });
  const [picksUsed, setPicksUsed] = useState(0);
  const [maxPicks, setMaxPicks] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busyGameId, setBusyGameId] = useState(null);

  // Distinguishes the first load of a week (show a spinner) from a pick
  // (leave the board on screen).
  const loadedWeek = useRef(null);

  useEffect(() => {
    api
      .currentWeek()
      .then((data) => {
        setMeta(data);
        setWeek(data.week);
      })
      .catch(setError);
  }, []);

  const loadWeek = useCallback(async () => {
    if (week === null || !meta) return;

    if (loadedWeek.current !== week) setLoading(true);
    setError(null);

    try {
      const [weekGames, picks, options] = await Promise.all([
        api.games({ season: meta.season, week }),
        api.myPicks({ season: meta.season, week }),
        api.gameFilters({ season: meta.season, week }),
      ]);
      setGames(weekGames);
      setPicksUsed(picks.picksUsed);
      setMaxPicks(picks.maxPicks);
      setFilterOptions(options);
      loadedWeek.current = week;
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [meta, week]);

  useEffect(() => {
    loadWeek();
  }, [loadWeek]);

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
        const refreshed = await api.games({ season: meta.season, week });
        setGames(refreshed);
      } catch {
        // A dropped poll is not worth an error banner - the next one is 30
        // seconds away and the board on screen is still valid.
      }
    }, 30_000);

    return () => clearInterval(timer);
  }, [anyLive, busyGameId, meta, week]);

  // Keep "my picks" in the URL so the view survives a refresh and can be
  // linked to, but reset the rest when the week changes.
  useEffect(() => {
    setFilters((current) => ({ ...NO_FILTERS, mine: current.mine }));
  }, [week]);

  const updateFilters = (next) => {
    setFilters(next);
    setSearchParams(next.mine ? { mine: '1' } : {}, { replace: true });
  };

  const visibleGames = useMemo(() => {
    return games.filter((game) => {
      if (filters.mine && !game.mySpreadPick && !game.myTotalPick) return false;
      // Literal: a locked game is not actionable even if it was already
      // picked, which is what "still pick" means.
      if (filters.pickableOnly && !isPickable(game)) return false;
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
  }, [games, filters]);

  /**
   * Applies a pick without reloading the board. The refreshed game from the
   * server is the source of truth; only the affected card and the counter
   * change, so nothing jumps under the user's thumb.
   */
  const applyPick = async (game, action, delta) => {
    setBusyGameId(game.id);
    setNotice(null);
    try {
      await action();
      const refreshed = await api.game(game.id);
      setGames((current) =>
        current.map((row) => (row.id === game.id ? refreshed.game : row)),
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
        const refreshed = await api.game(game.id).catch(() => null);
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
        existing
          ? api.updatePick(existing.id, selection, line)
          : api.createPick(game.id, selection, line),
      // Switching sides within a market spends nothing extra.
      existing ? 0 : 1,
    );
  };

  const handleClear = (game, selection) =>
    applyPick(game, () => api.deletePick(pickFor(game, selection).id), -1);

  const handleRelock = (game, pick) =>
    applyPick(game, () => api.relockPick(pick.id), 0);

  const remaining = Math.max(0, maxPicks - picksUsed);
  const weekNotLoaded =
    meta && week !== null && !meta.loadedWeeks?.includes(week) && games.length === 0;

  return (
    <>
      {/* Pinned so the pick budget stays visible while scrolling a long board. */}
      <div className="picks-bar border-bottom">
        <Container className="py-2">
          <div className="d-flex justify-content-between align-items-center gap-3 small">
            <span className="fw-semibold text-nowrap">
              {picksUsed} / {maxPicks} picks
              <span className="d-none d-sm-inline text-body-secondary fw-normal">
                {' '}
                · week {week ?? '-'}
              </span>
            </span>
            <ProgressBar
              now={(picksUsed / maxPicks) * 100}
              variant={remaining === 0 ? 'secondary' : 'primary'}
              style={{ height: 6, flex: 1 }}
              aria-label={`${picksUsed} of ${maxPicks} picks used`}
            />
            <span className="text-body-secondary text-nowrap">{remaining} left</span>
          </div>
        </Container>
      </div>

      <Container className="py-4">
        <h1 className="h3 mb-3">Week {week ?? '-'}</h1>

        {/* Always rendered, even for a week with no games - it carries the
            week picker, which is how you get to a week that has some. */}
        <GameFilters
          weekSelector={
            <WeekSelector
              compact
              weeks={meta?.availableWeeks}
              current={week}
              onChange={setWeek}
            />
          }
          options={filterOptions}
          value={filters}
          onChange={updateFilters}
          resultCount={visibleGames.length}
          totalCount={games.length}
        />

        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}
        <ErrorNotice error={error} onRetry={loadWeek} />

        {loading ? (
          <Loading label="Loading games" />
        ) : weekNotLoaded ? (
          <EmptyState title={`Week ${week} has not been loaded yet`}>
            <p className="small mb-0">
              An admin can pull it in from Admin → Data. Schedules and lines for future weeks are
              usually posted a week or two ahead.
            </p>
          </EmptyState>
        ) : games.length === 0 ? (
          <EmptyState title="No games this week" />
        ) : visibleGames.length === 0 ? (
          <EmptyState
            title={
              filters.pickableOnly && !filters.mine && games.length > 0
                ? 'Every game this week has locked'
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
