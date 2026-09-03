import { useCallback, useEffect, useState } from 'react';
import { Badge, Card, Col, Container, Nav, Row, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { AthleteHeadshot, AthleteLink, CoachLink, TeamLogo } from '../components/links.jsx';
import {
  BackButton,
  ErrorNotice,
  Loading,
  formatKickoff,
  formatSpread,
} from '../components/common.jsx';
import { api } from '../api/client.js';
import { useGroup } from '../auth/GroupProvider.jsx';

const inches = (value) =>
  value ? `${Math.floor(value / 12)}'${String(value % 12).padStart(2, '0')}"` : '-';

const CLASS_YEARS = { 1: 'FR', 2: 'SO', 3: 'JR', 4: 'SR', 5: 'GR' };

/**
 * Black or white text, whichever can actually be read on this team's colour.
 *
 * <p>The banner is painted in whatever hex the team ships, and hardcoding
 * white text assumed every one of them is dark. Most are, but not all - a
 * school on gold or silver got white on near-white. Uses the WCAG relative
 * luminance so the answer is measured rather than guessed at.
 */
const HEX = /^#?([0-9a-f]{6})$/i;

/**
 * The team's colour, or the neutral default when it is unusable.
 *
 * <p>Four schools are stored with the literal string "#null" - a null colour
 * concatenated onto a "#" somewhere upstream. Passing that through would set
 * --team-color to an invalid value, and CSS treats an invalid variable as
 * unset at computed-value time rather than falling back to the default in
 * var(), so the banner would lose its fill entirely.
 */
function bannerColor(hex) {
  return HEX.test(hex ?? '') ? hex : '#12141c';
}

function readableInk(hex) {
  const parsed = HEX.exec(hex ?? '');
  if (!parsed) return '#fff';

  const value = parseInt(parsed[1], 16);
  // sRGB channels, gamma-expanded to linear light before weighting.
  const channel = (raw) => {
    const c = raw / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const luminance =
    0.2126 * channel((value >> 16) & 255)
    + 0.7152 * channel((value >> 8) & 255)
    + 0.0722 * channel(value & 255);

  return luminance > 0.4 ? '#101319' : '#fff';
}

/**
 * A number from the board, boxed by how it came in.
 *
 * <p>Green covered, red did not, no box while the game is unplayed or the
 * result was a push. A push is deliberately unboxed: neither colour is true of
 * it, and inventing a third would say more than the result does.
 */
function LineBox({ value, outcome }) {
  if (value == null) return <span className="text-body-tertiary small">-</span>;
  if (!outcome) return <span className="line-box">{value}</span>;
  return <span className={`line-box line-box--${outcome}`}>{value}</span>;
}

/**
 * Did this team beat the spread?
 *
 * <p>{@code homeSpread} is the home team's number, so it is added to the home
 * score and the comparison runs from there whichever side we are looking at.
 * Returns null when the game has not been scored, or on a push.
 */
function spreadOutcome(game, isHome) {
  if (game.status !== 'FINAL' || game.homeSpread == null) return null;
  if (game.homeScore == null || game.awayScore == null) return null;

  const margin = game.homeScore + Number(game.homeSpread) - game.awayScore;
  if (margin === 0) return null;
  return (margin > 0) === isHome ? 'win' : 'loss';
}

/** Did the game go over? Green over, red under, nothing on a push. */
function totalOutcome(game) {
  if (game.status !== 'FINAL' || game.overUnder == null) return null;
  if (game.homeScore == null || game.awayScore == null) return null;

  const combined = game.homeScore + game.awayScore;
  if (combined === Number(game.overUnder)) return null;
  return combined > Number(game.overUnder) ? 'win' : 'loss';
}

// Column order for the ranking history table, matching the priority used to
// pick the rank shown beside a team's name.
const POLLS = ['Playoff Committee Rankings', 'AP Top 25', 'Coaches Poll'];

/** "11-4" from a split, or "-" once there is nothing to show yet. */
function record(split) {
  if (!split || split.games == null) return '-';
  return split.ties ? `${split.wins}-${split.losses}-${split.ties}` : `${split.wins}-${split.losses}`;
}

export default function TeamPage() {
  const { groupId } = useGroup();

  const { id } = useParams();
  const [team, setTeam] = useState(null);
  const [tab, setTab] = useState('schedule');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTeam(await api.team(id, { groupId }));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [groupId, id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <Loading label="Loading team" />;
  if (error) {
    return (
      <Container className="py-4">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }
  if (!team) return null;

  const espn = team.espn;

  // ESPN fills the gaps rather than overriding: our own record is what every
  // other page renders, and a team should not change name between them.
  const venueName = team.venueName ?? espn?.venueName;
  const venueCity = team.venueCity ?? espn?.venueCity;
  const venueState = team.venueState ?? espn?.venueState;
  const banner = bannerColor(team.color ?? espn?.color);
  const ink = readableInk(banner);

  return (
    <Container className="py-4 py-md-5">
      <BackButton className="mb-3" />

      {/* The logo sits on a light disc rather than straight on the banner.
          Team logos are supplied as the team's own colour on transparency,
          and the banner is painted in that same colour - so TCU's mark was
          being drawn in #4d1979 on #4d1979 and vanishing completely, leaving
          a band of empty purple where it should have been. The disc
          guarantees contrast whatever the school's colour turns out to be. */}
      <div className="team-hero mb-4" style={{ '--team-color': banner, color: ink }}>
        <div className="team-hero-body">
          <span className="team-hero-crest">
            <TeamLogo team={team} size={64} />
          </span>

          <div className="team-hero-text">
            <div className="team-hero-eyebrow">
              {espn?.abbreviation ?? team.abbreviation}
              {team.conference && <> · {team.conference}</>}
              {team.division && <> · {team.division}</>}
            </div>

            <h1 className="team-hero-name">
              {team.rank != null && <span className="team-hero-rank">#{team.rank}</span>}
              {team.school} {team.mascot}
            </h1>

            {venueName && (
              <div className="team-hero-venue">
                {venueName}
                {venueCity && `, ${venueCity}`}
                {venueState && ` ${venueState}`}
                {espn?.venueIndoor && ' · indoor'}
                {espn?.venueGrass === false && ' · turf'}
              </div>
            )}
          </div>

          {espn?.espnUrl && (
            <a
              href={espn.espnUrl}
              target="_blank"
              rel="noreferrer"
              className="team-hero-link"
            >
              ESPN ↗
            </a>
          )}
        </div>
      </div>

      {team.coaches.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body className="d-flex flex-wrap gap-3 align-items-center">
            <span className="small text-uppercase text-body-secondary">Head coach</span>
            {team.coaches.map((coach) => (
              <CoachLink key={coach.id} coach={coach} className="fw-semibold" />
            ))}
          </Card.Body>
        </Card>
      )}

      {/* Season record and ATS. Neither has necessarily been loaded yet -
          record is an admin-triggered pull, ATS refreshes the first time
          anyone views a team since its last game concluded - so the whole
          card is skipped rather than showing a wall of dashes. */}
      {(team.record || team.ats) && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Record</h2>
            <Row className="g-3">
              {team.record && (
                <>
                  <Col xs={6} md={3}>
                    <div className="stat-tile h-100">
                      <div className="small text-body-secondary">Overall</div>
                      <div className="fs-4 fw-bold">{record(team.record.total)}</div>
                    </div>
                  </Col>
                  <Col xs={6} md={3}>
                    <div className="stat-tile h-100">
                      <div className="small text-body-secondary">Conference</div>
                      <div className="fs-4 fw-bold">{record(team.record.conference)}</div>
                    </div>
                  </Col>
                  <Col xs={6} md={3}>
                    <div className="stat-tile h-100">
                      <div className="small text-body-secondary">Home / Away</div>
                      <div className="fs-4 fw-bold">
                        {record(team.record.home)} / {record(team.record.away)}
                      </div>
                    </div>
                  </Col>
                </>
              )}
              {team.ats && (
                <Col xs={6} md={3}>
                  <div className="stat-tile h-100">
                    <div className="small text-body-secondary">Against the spread</div>
                    <div className="fs-4 fw-bold">
                      {team.ats.wins}-{team.ats.losses}
                      {team.ats.pushes ? `-${team.ats.pushes}` : ''}
                    </div>
                    {team.ats.avgCoverMargin != null && (
                      <div className="small text-body-tertiary">
                        {Number(team.ats.avgCoverMargin) > 0 ? '+' : ''}
                        {Number(team.ats.avgCoverMargin).toFixed(1)} avg cover
                      </div>
                    )}
                  </div>
                </Col>
              )}
            </Row>
          </Card.Body>
        </Card>
      )}

      {/* Every season this team has an ATS record for, newest first. Only as
          deep as the seasons an admin has actually loaded. */}
      {team.atsHistory?.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">
              Against the spread by season
            </h2>
            <div className="table-responsive" style={{ maxHeight: '20rem' }}>
              <Table size="sm" hover className="align-middle mb-0">
                <thead>
                  <tr>
                    <th scope="col">Season</th>
                    <th scope="col" className="text-center">Games</th>
                    <th scope="col" className="text-center">ATS</th>
                    <th scope="col" className="text-end">Avg cover</th>
                  </tr>
                </thead>
                <tbody>
                  {team.atsHistory.map((row) => (
                    <tr key={row.season}>
                      <td>{row.season}</td>
                      <td className="text-center text-body-secondary">{row.games ?? '—'}</td>
                      <td className="text-center fw-semibold">
                        {row.wins}-{row.losses}
                        {row.pushes ? (
                          <span className="fw-normal text-body-secondary">-{row.pushes}</span>
                        ) : null}
                      </td>
                      <td className="text-end text-body-secondary">
                        {row.avgCoverMargin == null
                          ? '—'
                          : `${Number(row.avgCoverMargin) > 0 ? '+' : ''}${Number(
                              row.avgCoverMargin,
                            ).toFixed(1)}`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          </Card.Body>
        </Card>
      )}

      {/* Current placement in all three polls, so the single rank shown by
          the name can be checked against the others. */}
      {team.currentRankings?.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body>
            <h2 className="h6 text-uppercase text-body-secondary mb-3">
              Current rankings · week {team.currentRankings[0].week}
            </h2>
            <Row className="g-3">
              {team.currentRankings.map((entry) => (
                <Col xs={6} md={4} key={entry.poll}>
                  <div className="stat-tile h-100">
                    <div className="small text-body-secondary">{entry.poll}</div>
                    <div className="fs-4 fw-bold">#{entry.rank}</div>
                    {entry.firstPlaceVotes > 0 && (
                      <div className="small text-body-tertiary">
                        {entry.firstPlaceVotes} first-place vote
                        {entry.firstPlaceVotes === 1 ? '' : 's'}
                      </div>
                    )}
                  </div>
                </Col>
              ))}
            </Row>
          </Card.Body>
        </Card>
      )}

      <Nav variant="tabs" activeKey={tab} onSelect={setTab} className="mb-3">
        <Nav.Item>
          <Nav.Link eventKey="schedule">Schedule ({team.schedule.length})</Nav.Link>
        </Nav.Item>
        <Nav.Item>
          <Nav.Link eventKey="roster">Roster ({team.roster.length})</Nav.Link>
        </Nav.Item>
        {team.rankingHistory?.length > 0 && (
          <Nav.Item>
            <Nav.Link eventKey="rankings">Rankings ({team.rankingHistory.length})</Nav.Link>
          </Nav.Item>
        )}
      </Nav>

      {tab === 'rankings' ? (
        <Card className="shadow-sm">
          <div className="table-responsive">
            <Table hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Week</th>
                  {POLLS.map((poll) => (
                    <th scope="col" key={poll} className="text-center">
                      {poll}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {team.rankingHistory.map((week) => (
                  <tr key={week.week}>
                    <td className="fw-semibold">{week.week}</td>
                    {POLLS.map((poll) => {
                      const entry = week.placements.find((p) => p.poll === poll);
                      return (
                        <td key={poll} className="text-center">
                          {entry ? (
                            <span className="fw-semibold">#{entry.rank}</span>
                          ) : (
                            // A dash means that poll did not rank them; the
                            // committee simply does not publish early weeks.
                            <span className="text-body-tertiary">—</span>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        </Card>
      ) : tab === 'schedule' ? (
        team.schedule.length === 0 ? (
          <p className="text-body-secondary">No games ingested for this season yet.</p>
        ) : (
          <div className="table-responsive">
            <Table hover className="align-middle">
              <thead>
                <tr>
                  <th scope="col">Wk</th>
                  <th scope="col">Matchup</th>
                  <th scope="col" className="d-none d-md-table-cell">Kickoff</th>
                  <th scope="col">SPR</th>
                  <th scope="col">O/U</th>
                </tr>
              </thead>
              <tbody>
                {team.schedule.map((game) => {
                  const home = String(game.homeTeam?.id) === String(team.id);
                  const opponent = home ? game.awayTeamName : game.homeTeamName;
                  return (
                    <tr key={game.id}>
                      <td>{game.week}</td>
                      <td>
                        <Link to={`/games/${game.id}`} className="text-decoration-none">
                          {home ? 'vs' : 'at'} {opponent}
                        </Link>
                      </td>
                      <td className="d-none d-md-table-cell small text-body-secondary">
                        {formatKickoff(game.kickoff, game.startTimeTbd)}
                      </td>
                      <td>
                        <LineBox
                          value={
                            game.homeSpread == null
                              ? null
                              : formatSpread(game.homeSpread, home ? 'HOME' : 'AWAY')
                          }
                          outcome={spreadOutcome(game, home)}
                        />
                      </td>
                      <td>
                        <LineBox
                          value={game.overUnder == null ? null : Number(game.overUnder)}
                          outcome={totalOutcome(game)}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </div>
        )
      ) : team.roster.length === 0 ? (
        <p className="text-body-secondary">
          No roster available. It is read from the data provider each time this page loads - if
          they were unreachable, reloading should bring it back.
        </p>
      ) : (
        <Row xs={1} sm={2} lg={3} className="g-2">
          {team.roster.map((player) => (
            <Col key={player.id}>
              <Card className="h-100">
                <Card.Body className="d-flex align-items-center gap-3 py-2">
                  {/* The face is what identifies a player on a roster; the
                      number is a detail about them, so it moves to the end of
                      the row where the eye lands last. */}
                  <AthleteHeadshot url={player.headshotUrl} />
                  <div className="flex-grow-1" style={{ minWidth: 0 }}>
                    <AthleteLink athlete={player} className="fw-semibold" />
                    <div className="small text-body-secondary">
                      {player.position}
                      {player.year && <> · {CLASS_YEARS[player.year] ?? player.year}</>}
                    </div>
                  </div>
                  {player.jersey != null && (
                    <span
                      className="text-body-tertiary fw-semibold flex-shrink-0"
                      style={{ fontVariantNumeric: 'tabular-nums' }}
                      title={`Jersey ${player.jersey}`}
                    >
                      #{player.jersey}
                    </span>
                  )}
                </Card.Body>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </Container>
  );
}

export { inches };
