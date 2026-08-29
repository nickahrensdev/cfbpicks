import { useCallback, useEffect, useState } from 'react';
import { Badge, Card, Col, Container, Row, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { TeamLink, TeamLogo } from '../components/links.jsx';
import WinProbabilityDonut from '../components/WinProbabilityDonut.jsx';
import {
  BackButton,
  ErrorNotice,
  LockCountdown,
  Loading,
  ResultBadge,
  formatKickoff,
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

export default function GameDetailPage() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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
  const final = game.status === 'FINAL';
  const live = game.live;
  const espn = detail.espn;

  // The live score supersedes the stored one while a game is on; afterwards
  // the stored score is the graded one and wins.
  const homeScore = final ? game.homeScore : live?.homeScore ?? game.homeScore;
  const awayScore = final ? game.awayScore : live?.awayScore ?? game.awayScore;

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
      <BackButton className="mb-3" />

      <Card className="shadow-sm mb-4">
        <Card.Body>
          <div className="d-flex justify-content-between align-items-start gap-2 mb-3">
            <div className="small text-body-secondary">
              Week {game.week} · {formatKickoff(game.kickoff, game.startTimeTbd)}
              {game.venue && <> · {game.venue}</>}
              {espn?.broadcast && <> · {espn.broadcast}</>}
              {live?.downDistance && (
                <div className={live.redZone ? 'text-danger fw-semibold' : ''}>
                  {live.downDistance}
                </div>
              )}
            </div>
            {live ? (
              <Badge bg="danger" className="d-inline-flex align-items-center gap-1">
                <span className="live-dot" aria-hidden="true" />
                {[live.periodLabel, live.clock].filter(Boolean).join(' · ')
                  || live.detail
                  || 'Live'}
              </Badge>
            ) : final ? (
              <Badge bg="dark">Final</Badge>
            ) : (
              <LockCountdown locksAt={game.locksAt} locked={game.locked} />
            )}
          </div>

          <Row className="g-3 align-items-center">
            {[
              ['AWAY', game.awayTeam, game.awayTeamName, awayScore, detail.awayConference],
              ['HOME', game.homeTeam, game.homeTeamName, homeScore, detail.homeConference],
            ].map(([side, team, name, score, conference]) => (
              <Col xs={12} md={6} key={side}>
                <div className="d-flex align-items-center gap-3">
                  <TeamLogo team={team} size={48} />
                  <div className="flex-grow-1">
                    <div className="small text-body-secondary">
                      {side === 'HOME' ? 'Home' : 'Away'}
                      {conference && <> · {conference}</>}
                    </div>
                    <div className="h5 mb-0 d-flex align-items-center gap-2">
                      <TeamLink team={team} name={name} logo={false} />
                      {live?.possessionTeamId != null
                        && String(live.possessionTeamId) === String(team?.id) && (
                          <span aria-hidden="true" title="Has possession">
                            🏈
                          </span>
                        )}
                    </div>
                    <div className="small text-body-secondary">
                      {formatSpread(game.homeSpread, side)}
                    </div>
                  </div>
                  {score != null && (final || live) && (
                    <div className="display-6 fw-bold">{score}</div>
                  )}
                </div>
              </Col>
            ))}
          </Row>
        </Card.Body>
      </Card>

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
