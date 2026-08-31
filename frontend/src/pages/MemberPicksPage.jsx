import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Card, Container, Form, Table } from 'react-bootstrap';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import { TeamLink } from '../components/links.jsx';
import {
  EmptyState,
  ErrorNotice,
  Loading,
  NoGroupNotice,
  ResultBadge,
  formatKickoff,
  formatLine,
} from '../components/common.jsx';
import { api } from '../api/client.js';
import { useGroup } from '../auth/GroupProvider.jsx';

/**
 * Another member's card. The API only returns picks on games that have
 * already kicked off, so this page cannot be used to scout the field.
 */
export default function MemberPicksPage() {
  const { groupId, hasNoGroups } = useGroup();

  const { userId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();

  const [meta, setMeta] = useState(null);
  // null means every week, which is what arriving without a ?week gets you.
  // Defaulting to the current week showed an empty card the moment the season
  // moved past the last week anyone had picked.
  const [week, setWeek] = useState(() => {
    const value = searchParams.get('week');
    return value ? Number(value) : null;
  });
  const [picks, setPicks] = useState([]);
  // The leagues this member and the viewer are both in - see
  // MemberGroupsController for why it is only the shared ones.
  const [memberGroups, setMemberGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Only for the week list - the selection deliberately stays on "all".
    api.currentWeek().then(setMeta).catch(setError);
  }, []);

  // Independent of the week picker: which leagues someone plays in does not
  // change when you change the week you are looking at.
  useEffect(() => {
    api.memberGroups(userId).then(setMemberGroups).catch(() => setMemberGroups([]));
  }, [userId]);

  const load = useCallback(async () => {
    if (!groupId) return;
    setLoading(true);
    setError(null);
    try {
      setPicks(await api.memberPicks(userId, { groupId, season: meta?.season, week }));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [groupId, userId, meta, week]);

  useEffect(() => {
    load();
  }, [load]);

  const changeWeek = (next) => {
    setWeek(next);
    setSearchParams(next ? { week: String(next) } : {});
  };

  if (hasNoGroups) {
    return (
      <Container className="py-4 py-md-5">
        <NoGroupNotice />
      </Container>
    );
  }

  return (
    <Container className="py-4 py-md-5">
      <h1 className="h3 mb-4">Member picks</h1>

      {/* Shared leagues only. A card is readable by anyone signed in, but the
          other groups someone plays in are not public just because their picks
          in a group you share are. */}
      {memberGroups.length > 0 && (
        <Card className="shadow-sm mb-4">
          <Card.Body className="pb-2">
            <h2 className="h6 text-uppercase text-body-secondary mb-3">Groups you share</h2>
          </Card.Body>
          <div className="table-responsive">
            <Table hover size="sm" className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Group</th>
                  <th scope="col" className="d-none d-md-table-cell">Format</th>
                  <th scope="col" className="text-center">Rank</th>
                  <th scope="col" className="text-center" title="Wins-losses-ties">W-L-T</th>
                  <th scope="col" className="text-end">Pts</th>
                </tr>
              </thead>
              <tbody>
                {memberGroups.map((row) => (
                  <tr key={row.groupId}>
                    <td>
                      <Link to={`/groups/${row.groupId}`} className="text-decoration-none">
                        {row.name}
                      </Link>
                      {row.role === 'OWNER' && (
                        <Badge bg="primary-subtle" text="primary-emphasis" className="ms-2">
                          owner
                        </Badge>
                      )}
                    </td>
                    <td className="d-none d-md-table-cell small text-body-secondary">
                      {row.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em"} ·{' '}
                      {row.cadence === 'DAILY' ? 'Daily' : 'Weekly'} · {row.memberCount}{' '}
                      {row.memberCount === 1 ? 'member' : 'members'}
                    </td>
                    <td className="text-center fw-semibold">{row.rank ?? '—'}</td>
                    <td className="text-center">
                      {row.wins}-{row.losses}-{row.pushes}
                    </td>
                    <td className="text-end fw-semibold">{row.points.toFixed(1)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        </Card>
      )}

      <Form.Group className="mb-4" style={{ maxWidth: '14rem' }}>
        <Form.Label htmlFor="member-week" className="small fw-semibold mb-1">
          Week
        </Form.Label>
        <Form.Select
          id="member-week"
          value={week ?? ''}
          onChange={(e) => changeWeek(e.target.value === '' ? null : Number(e.target.value))}
        >
          <option value="">All weeks</option>
          {(meta?.availableWeeks ?? []).map((option) => (
            <option key={option} value={option}>
              Week {option}
            </option>
          ))}
        </Form.Select>
      </Form.Group>

      <ErrorNotice error={error} onRetry={load} />

      {loading ? (
        <Loading label="Loading picks" />
      ) : picks.length === 0 ? (
        <EmptyState title="Nothing to show yet">
          <p className="small mb-0">
            {week == null
              ? 'Either this member has made no picks in this group, or none of their games have kicked off.'
              : `Either this member has no picks in week ${week}, or none of their games have kicked off.`}
          </p>
        </EmptyState>
      ) : (
        <div className="d-grid gap-3">
          {picks.map(({ pick, game }) => {
            if (!game) return null;

            const isTotal = pick.market === 'TOTAL';
            const side = pick.selection === 'HOME' ? game.homeTeam : game.awayTeam;
            const sideName = pick.selection === 'HOME' ? game.homeTeamName : game.awayTeamName;
            const opponent = pick.selection === 'HOME' ? game.awayTeamName : game.homeTeamName;

            return (
              <Card key={pick.id} className="shadow-sm">
                <Card.Body className="d-flex flex-column flex-sm-row justify-content-between gap-2">
                  <div>
                    <div className="d-flex align-items-center gap-2">
                      {isTotal ? (
                        // A total belongs to the game, not to a side, so it
                        // is labelled with the matchup rather than a team.
                        <span className="fw-semibold">
                          {game.awayTeamName} at {game.homeTeamName}
                        </span>
                      ) : (
                        <TeamLink
                          team={side}
                          name={sideName}
                          logoSize={24}
                          className="fw-semibold"
                        />
                      )}
                      <span className="fw-semibold">
                        {formatLine(pick.lockedLine, pick.selection)}
                      </span>
                    </div>
                    <div className="small text-body-secondary">
                      {isTotal ? (
                        <>Total · </>
                      ) : (
                        <>
                          {pick.selection === 'HOME' ? 'vs' : 'at'} {opponent} ·{' '}
                        </>
                      )}
                      {formatKickoff(game.kickoff, game.startTimeTbd)}
                      {game.status === 'FINAL' && (
                        <>
                          {' '}
                          · final {game.awayScore}-{game.homeScore}
                        </>
                      )}
                    </div>
                  </div>
                  <div className="align-self-sm-center">
                    <ResultBadge result={pick.result} />
                  </div>
                </Card.Body>
              </Card>
            );
          })}
        </div>
      )}
    </Container>
  );
}
