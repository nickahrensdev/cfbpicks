import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Row, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import GameCard from '../components/GameCard.jsx';
import WinProbabilityDonut from '../components/WinProbabilityDonut.jsx';
import {
  BackButton,
  ErrorNotice,
  Loading,
  ResultBadge,
  formatLine,
  formatSpread,
  formatTotal,
  marketLabel,
} from '../components/common.jsx';
import { api } from '../api/client.js';

function Stat({ label, value, note }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <Col xs={6} md={3}>
      <div className="small text-body-secondary">{label}</div>
      <div className="fw-semibold">{value}</div>
      {note && <div className="small text-body-tertiary">{note}</div>}
    </Col>
  );
}

/**
 * How far a number has travelled since it opened.
 *
 * <p>Direction is the point, so the sign is always shown - "+1.5" and "-1.5"
 * are different stories and a bare 1.5 tells neither.
 */
function movement(current, open) {
  if (current == null || open == null) return null;
  const delta = Number(current) - Number(open);
  if (delta === 0) return 'unmoved';
  return `${delta > 0 ? '+' : ''}${Number(delta.toFixed(1))} since open`;
}

/** "11-4-1" from an ATS summary, or a dash when that team has no row for the season. */
function atsRecord(ats) {
  if (!ats) return '—';
  return `${ats.wins}-${ats.losses}${ats.pushes ? `-${ats.pushes}` : ''}`;
}

/** "+1.5" average cover margin, or nothing when it is absent. */
function atsMargin(ats) {
  if (!ats || ats.avgCoverMargin == null) return null;
  const margin = Number(ats.avgCoverMargin);
  return `${margin > 0 ? '+' : ''}${margin.toFixed(1)}`;
}

export default function GameDetailPage() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [matchup, setMatchup] = useState(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await api.game(id));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Which of the caller's picks and which line a selection belongs to -
  // same logic GamesPage uses for its own cards.
  const marketOf = (selection) =>
    selection === 'OVER' || selection === 'UNDER' ? 'TOTAL' : 'SPREAD';
  const pickFor = (g, selection) =>
    marketOf(selection) === 'TOTAL' ? g.myTotalPick : g.mySpreadPick;
  const lineFor = (g, selection) =>
    marketOf(selection) === 'TOTAL' ? g.overUnder : g.homeSpread;

  /**
   * Applies a pick from this page's own card. `action` resolves straight to
   * the mutation's updated GameSummary (see PickController), so only the
   * `game` slice of `detail` needs replacing - everything else on the page
   * (lines, ATS, ESPN box score, head-to-head) is untouched.
   */
  const applyPick = async (action) => {
    setBusy(true);
    setNotice(null);
    try {
      const updatedGame = await action();
      setDetail((current) => ({ ...current, game: updatedGame }));
    } catch (err) {
      if (err.code === 'LINE_MOVED') {
        setNotice({
          variant: 'warning',
          text: `${err.message}. The card now shows the current line - pick again if you still want it.`,
        });
        await load();
      } else {
        setNotice({
          variant: err.code === 'WEEKLY_LIMIT_REACHED' ? 'warning' : 'danger',
          text: err.message,
        });
        if (err.code === 'PICK_WINDOW_CLOSED' || err.code === 'WEEKLY_LIMIT_REACHED') {
          await load();
        }
      }
    } finally {
      setBusy(false);
    }
  };

  const handlePick = (g, selection) => {
    const existing = pickFor(g, selection);
    const line = lineFor(g, selection);
    return applyPick(() =>
      (existing
        ? api.updatePick(existing.id, selection, line)
        : api.createPick(g.id, selection, line)
      ).then((response) => response.game),
    );
  };

  const handleClear = (g, selection) =>
    applyPick(() => api.deletePick(pickFor(g, selection).id));

  const handleRelock = (g, pick) =>
    applyPick(() => api.relockPick(pick.id).then((response) => response.game));

  // Head-to-head history. Fetched once the two team ids are known - a
  // non-FBS opponent with no team id just means no section, not an error.
  // The backend caches this and only re-hits CFBD when it's actually stale,
  // so this call is safe to make on every page view.
  const homeTeamId = detail?.game?.homeTeam?.id;
  const awayTeamId = detail?.game?.awayTeam?.id;

  useEffect(() => {
    if (!homeTeamId || !awayTeamId) {
      setMatchup(null);
      return;
    }
    api.matchup(homeTeamId, awayTeamId).then(setMatchup).catch(() => setMatchup(null));
  }, [homeTeamId, awayTeamId]);

  // Refresh the score, clock and box score while the game is on. Silent: a
  // dropped poll leaves the page as it was rather than replacing it with a
  // spinner or an error.
  const isLive = Boolean(detail?.game?.live);

  useEffect(() => {
    if (!isLive) return undefined;
    const timer = setInterval(() => {
      api.game(id).then(setDetail).catch(() => {});
    }, 30_000);
    return () => clearInterval(timer);
  }, [isLive, id]);

  if (loading) return <Loading label="Loading game" />;
  if (error) {
    return (
      <Container className="py-4">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }
  if (!detail) return null;

  const { game } = detail;
  const live = game.live;
  const espn = detail.espn;

  // ATS keyed by season for each side, plus the union of the years either of
  // them has - so the table can show one row per season with a dash where a
  // team has no record for that year.
  const homeAtsBySeason = new Map(
    (detail.homeAtsHistory ?? []).map((row) => [row.season, row]),
  );
  const awayAtsBySeason = new Map(
    (detail.awayAtsHistory ?? []).map((row) => [row.season, row]),
  );
  const atsSeasons = [...new Set([...homeAtsBySeason.keys(), ...awayAtsBySeason.keys()])]
    .sort((a, b) => b - a);

  // ESPN's in-game model while the game is on, our stored postgame figure
  // once it is over. Both are "home team's chance of winning", 0..1.
  const homeWinProbability =
    live && espn?.homeWinProbability != null
      ? Number(espn.homeWinProbability)
      : detail.homeWinProbability != null
        ? Number(detail.homeWinProbability)
        : null;
  const awayWinProbability =
    homeWinProbability == null
      ? null
      : live && espn?.homeWinProbability != null
        ? 1 - Number(espn.homeWinProbability)
        : Number(detail.awayWinProbability);

  return (
    <Container className="py-4 py-md-5">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <BackButton />
        <Button variant="outline-secondary" size="sm" onClick={load} disabled={loading || busy}>
          Refresh
        </Button>
      </div>

      {notice && (
        <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)} className="mb-3">
          {notice.text}
        </Alert>
      )}

      {/* The same card the games board uses, so a pick made or changed here
          is the identical action, not a second implementation of it. */}
      <div className="mb-4">
        <GameCard
          game={game}
          busy={busy}
          onPick={handlePick}
          onClear={handleClear}
          onRelock={handleRelock}
          showDetailsLink={false}
        />
      </div>

      <Card className="shadow-sm mb-4">
        <Card.Body>
          <h2 className="h6 text-uppercase text-body-secondary mb-3">The line</h2>
          <Row className="g-3">
            <Stat
              label="Current spread"
              value={formatSpread(game.homeSpread, 'HOME')}
              note={movement(game.homeSpread, detail.spreadOpen)}
            />
            <Stat
              label="Current total"
              value={detail.overUnder != null ? Number(detail.overUnder) : null}
              note={movement(detail.overUnder, detail.overUnderOpen)}
            />
            <Stat label="Book" value={detail.spreadProvider} />
            <Stat label="Home moneyline" value={detail.homeMoneyline} />
            <Stat label="Away moneyline" value={detail.awayMoneyline} />
            <Stat label="Home Elo" value={detail.homePregameElo} />
            <Stat label="Away Elo" value={detail.awayPregameElo} />
          </Row>

          {/* Where the book started, next to where it is now. Only rendered
              when an opener was captured - not every game has one. */}
          {(detail.spreadOpen != null || detail.overUnderOpen != null) && (
            <>
              <hr className="my-3" />
              <h3 className="h6 text-uppercase text-body-secondary mb-3">Opening lines</h3>
              <Row className="g-3">
                <Stat
                  label="Opening spread"
                  value={
                    detail.spreadOpen != null ? formatSpread(detail.spreadOpen, 'HOME') : null
                  }
                  note={detail.spreadOpen != null ? game.homeTeamName : null}
                />
                <Stat
                  label="Opening total"
                  value={detail.overUnderOpen != null ? Number(detail.overUnderOpen) : null}
                />
              </Row>
            </>
          )}
        </Card.Body>
      </Card>

      {/* Season-long ATS, refreshed on demand - see TeamAtsService. Distinct
          from ESPN's per-game "Around the game" ATS summary below, which is
          a different provider's number for this matchup specifically. */}
      {atsSeasons.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">
              Against the spread by season
            </h2>
            <div className="table-responsive" style={{ maxHeight: '20rem' }}>
              <Table size="sm" className="align-middle mb-0 text-center">
                <thead>
                  <tr>
                    <th scope="col" className="text-start">Season</th>
                    <th scope="col">{game.awayTeamName}</th>
                    <th scope="col">{game.homeTeamName}</th>
                  </tr>
                </thead>
                <tbody>
                  {/* Every season either side has a record for, newest first.
                      A team with nothing for a given year shows a dash rather
                      than dropping the row, so the years stay aligned. */}
                  {atsSeasons.map((season) => {
                    const away = awayAtsBySeason.get(season);
                    const home = homeAtsBySeason.get(season);
                    return (
                      <tr key={season}>
                        <td className="text-start">{season}</td>
                        <td>
                          <div className="fw-semibold">{atsRecord(away)}</div>
                          {atsMargin(away) && (
                            <div className="small text-body-tertiary">
                              {atsMargin(away)} avg cover
                            </div>
                          )}
                        </td>
                        <td>
                          <div className="fw-semibold">{atsRecord(home)}</div>
                          {atsMargin(home) && (
                            <div className="small text-body-tertiary">
                              {atsMargin(home)} avg cover
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </div>
          </Card.Body>
        </Card>
      )}

      {/* Win probability is a postgame figure from the provider - shown
          whenever it is stored, which in practice means once the game has
          been scored. */}
      {homeWinProbability != null && awayWinProbability != null && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">
              {live ? 'Live win probability' : 'Postgame win probability'}
            </h2>
            <WinProbabilityDonut
              homeName={game.homeTeamName}
              awayName={game.awayTeamName}
              homeProbability={homeWinProbability}
              awayProbability={awayWinProbability}
              homeColor={game.homeTeam?.color}
              awayColor={game.awayTeam?.color}
            />
            {detail.excitementIndex != null && (
              <div className="small text-body-secondary mt-3">
                Excitement index{' '}
                <strong className="text-body">
                  {Number(detail.excitementIndex).toFixed(1)}
                </strong>
              </div>
            )}
          </Card.Body>
        </Card>
      )}

      {/* Everything below comes from ESPN's game summary. It appears once
          there is a box score to show, which in practice means kickoff. */}
      {espn?.teamStats?.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Team stats</h2>
            <div className="table-responsive">
              <Table size="sm" className="align-middle mb-0">
                <thead>
                  <tr>
                    <th scope="col" />
                    {espn.teamStats.map((side) => (
                      <th scope="col" key={side.teamId} className="text-center">
                        {side.team}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {/* Both sides report the same stats in the same order, so
                      the first team's list drives the rows. */}
                  {espn.teamStats[0].stats.map((stat, index) => (
                    <tr key={stat.label}>
                      <th scope="row" className="fw-normal text-body-secondary">
                        {stat.label}
                      </th>
                      {espn.teamStats.map((side) => (
                        <td key={side.teamId} className="text-center fw-semibold">
                          {side.stats[index]?.value ?? '-'}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          </Card.Body>
        </Card>
      )}

      {espn?.leaders?.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Leaders</h2>
            <Row className="g-4">
              {espn.leaders.map((side) => (
                <Col xs={12} md={6} key={side.teamId}>
                  <div className="fw-semibold mb-2">{side.team}</div>
                  <div className="d-grid gap-2">
                    {side.leaders.map((leader) => (
                      <div
                        key={`${leader.category}-${leader.athleteId}`}
                        className="d-flex align-items-center gap-2"
                      >
                        {leader.headshotUrl && (
                          <img
                            src={leader.headshotUrl}
                            alt=""
                            width={40}
                            height={29}
                            loading="lazy"
                            className="rounded bg-body-tertiary flex-shrink-0"
                            style={{ objectFit: 'cover' }}
                          />
                        )}
                        <div className="flex-grow-1 min-width-0">
                          <div className="small text-body-tertiary">{leader.category}</div>
                          {/* Athlete ids are ESPN's, which is also what our
                              own roster uses - so this links straight to the
                              player page. */}
                          <Link
                            to={`/athletes/${leader.athleteId}`}
                            className="text-decoration-none fw-semibold"
                          >
                            {leader.name}
                          </Link>
                          <div className="small text-body-secondary">{leader.value}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </Col>
              ))}
            </Row>
          </Card.Body>
        </Card>
      )}

      {(espn?.againstTheSpread?.length > 0 || espn?.attendance != null) && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Around the game</h2>
            <Row className="g-3">
              {(espn.againstTheSpread ?? []).map((row) => (
                <Stat key={row.teamId} label={`${row.team} ATS`} value={row.summary} />
              ))}
              <Stat
                label="Attendance"
                value={espn.attendance != null ? espn.attendance.toLocaleString() : null}
              />
            </Row>
          </Card.Body>
        </Card>
      )}

      {matchup && matchup.games?.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Head-to-head</h2>
            <div className="mb-3">
              {matchup.team1Wins === matchup.team2Wins ? (
                <>
                  Series tied {matchup.team1Wins}-{matchup.team2Wins}
                  {matchup.ties ? `-${matchup.ties}` : ''} all-time.
                </>
              ) : (
                <>
                  <span className="fw-semibold">
                    {matchup.team1Wins > matchup.team2Wins ? matchup.team1Name : matchup.team2Name}
                  </span>{' '}
                  leads {Math.max(matchup.team1Wins, matchup.team2Wins)}-
                  {Math.min(matchup.team1Wins, matchup.team2Wins)}
                  {matchup.ties ? `-${matchup.ties}` : ''} all-time.
                </>
              )}
            </div>
            <div className="table-responsive" style={{ maxHeight: '20rem' }}>
              <Table hover size="sm" className="align-middle mb-0 text-center">
                <thead>
                  <tr>
                    <th scope="col" className="text-start">Season</th>
                    <th scope="col">{game.awayTeamName}</th>
                    <th scope="col" />
                    <th scope="col">{game.homeTeamName}</th>
                  </tr>
                </thead>
                <tbody>
                  {/* Scores stay fixed under this game's away/home columns
                      regardless of which side actually hosted that year -
                      the middle symbol carries which one it really was. */}
                  {[...matchup.games].reverse().map((meeting, index) => {
                    const sameOrientation = meeting.awayTeam === game.awayTeamName;
                    const awayColScore = sameOrientation ? meeting.awayScore : meeting.homeScore;
                    const homeColScore = sameOrientation ? meeting.homeScore : meeting.awayScore;
                    const awayColWon = meeting.winner != null
                      && meeting.winner === (sameOrientation ? meeting.awayTeam : meeting.homeTeam);
                    const homeColWon = meeting.winner != null
                      && meeting.winner === (sameOrientation ? meeting.homeTeam : meeting.awayTeam);

                    return (
                      <tr key={`${meeting.season}-${index}`}>
                        <td className="text-start">{meeting.season}</td>
                        <td className={awayColWon ? 'fw-bold' : undefined}>
                          {awayColScore ?? '-'}
                        </td>
                        <td className="text-body-tertiary">{sameOrientation ? '@' : 'vs.'}</td>
                        <td className={homeColWon ? 'fw-bold' : undefined}>
                          {homeColScore ?? '-'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </div>
          </Card.Body>
        </Card>
      )}

      <Card className="shadow-sm">
        <Card.Body>
          <h2 className="h6 text-uppercase text-body-secondary mb-3">Member picks</h2>

          {!detail.picksRevealed ? (
            <p className="text-body-secondary mb-0 small">
              Everyone&apos;s picks appear once this game kicks off.
              {game.mySpreadPick && (
                <>
                  {' '}
                  You took{' '}
                  <strong>
                    {game.mySpreadPick.selection === 'HOME'
                      ? game.homeTeamName
                      : game.awayTeamName}
                  </strong>{' '}
                  at {formatSpread(game.mySpreadPick.lockedLine, game.mySpreadPick.selection)}.
                </>
              )}
              {game.myTotalPick && (
                <>
                  {' '}
                  You took the{' '}
                  <strong>{game.myTotalPick.selection.toLowerCase()}</strong> at{' '}
                  {formatTotal(game.myTotalPick.lockedLine, game.myTotalPick.selection)}.
                </>
              )}
            </p>
          ) : detail.memberPicks.length === 0 ? (
            <p className="text-body-secondary mb-0 small">Nobody picked this game.</p>
          ) : (
            <div className="table-responsive">
              <Table hover size="sm" className="align-middle mb-0">
                <thead>
                  <tr>
                    <th scope="col">Member</th>
                    <th scope="col">Market</th>
                    <th scope="col">Pick</th>
                    <th scope="col">Line</th>
                    <th scope="col">Result</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.memberPicks.map((pick) => (
                    <tr key={`${pick.userId}-${pick.market}`}>
                      <td>{pick.displayName}</td>
                      <td className="text-body-secondary">{marketLabel(pick.market)}</td>
                      <td>
                        {pick.market === 'TOTAL'
                          ? pick.selection.charAt(0) + pick.selection.slice(1).toLowerCase()
                          : pick.selection === 'HOME'
                            ? game.homeTeamName
                            : game.awayTeamName}
                      </td>
                      <td>{formatLine(pick.lockedLine, pick.selection)}</td>
                      <td>
                        <ResultBadge result={pick.result} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
        </Card.Body>
      </Card>
    </Container>
  );
}
