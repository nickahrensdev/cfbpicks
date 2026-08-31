import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Col, Container, Form, Row, Table } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

import { EmptyState, ErrorNotice, Loading, MemberName, NoGroupNotice } from '../components/common.jsx';
import { useProfile } from '../auth/ProfileProvider.jsx';
import { api } from '../api/client.js';
import { useGroup } from '../auth/GroupProvider.jsx';

const medal = (rank) => (rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : null);

const MARKET_LABELS = [
  ['moneyline', 'Moneyline'],
  ['spread', 'Spread'],
  ['total', 'Over/Under'],
];

/** Trims trailing zeroes so 1.00 reads as 1 and 0.50 as 0.5. */
const points = (value) => Number(value).toString();

/**
 * How this group turns results into points.
 *
 * <p>Scoring is per group now, so the Pts column is unreadable without it -
 * two leagues showing "3.0" may have got there completely differently.
 */
function ScoringNote({ settings }) {
  if (!settings) return null;

  const live = MARKET_LABELS.filter(([key]) => settings[`${key}Enabled`]);
  if (live.length === 0) return null;

  return (
    <p className="small text-body-secondary mb-3">
      Scoring:{' '}
      {live.map(([key, label], index) => (
        <span key={key}>
          {index > 0 && ' · '}
          <span className="fw-semibold">{label}</span>{' '}
          {points(settings[`${key}WinPoints`])} win /{' '}
          {points(settings[`${key}LossPoints`])} loss /{' '}
          {points(settings[`${key}PushPoints`])} push
        </span>
      ))}
      . Ties on points break on most wins, then fewest losses.
    </p>
  );
}

export default function LeaderboardPage() {
  const { groupId, hasNoGroups } = useGroup();

  const { profile } = useProfile();
  const navigate = useNavigate();

  const [meta, setMeta] = useState(null);
  // null means Overall, which is the default view.
  const [week, setWeek] = useState(null);
  const [search, setSearch] = useState('');
  const [rows, setRows] = useState([]);
  const [scoring, setScoring] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.currentWeek().then(setMeta).catch(() => setMeta(null));
  }, []);

  const load = useCallback(async () => {
    if (!groupId) return;
    setLoading(true);
    setError(null);
    try {
      // The group's settings come along so the table can say how its points
      // are worked out - with scoring configurable, "Pts" alone means nothing.
      const [standings, detail] = await Promise.all([
        api.leaderboard({ groupId, week: week ?? undefined }),
        api.group(groupId).catch(() => null),
      ]);
      setScoring(detail?.settings ?? null);
      setRows(standings);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [groupId, week]);

  useEffect(() => {
    load();
  }, [load]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return rows;
    // Either name finds them - people search for whichever one they know.
    return rows.filter(
      (row) =>
        (row.displayName ?? '').toLowerCase().includes(term) ||
        (row.username ?? '').toLowerCase().includes(term),
    );
  }, [rows, search]);

  if (hasNoGroups) {
    return (
      <Container className="py-4 py-md-5">
        <NoGroupNotice />
      </Container>
    );
  }

  return (
    <Container className="py-4 py-md-5">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 mb-0">Leaderboard</h1>
        <Button variant="outline-secondary" size="sm" onClick={load} disabled={loading}>
          Refresh
        </Button>
      </div>

      <Row className="g-3 mb-4">
        <Col xs={12} sm={5} md={4}>
          <Form.Label htmlFor="leaderboard-week" className="small fw-semibold mb-1">
            Period
          </Form.Label>
          <Form.Select
            id="leaderboard-week"
            value={week ?? ''}
            onChange={(e) => setWeek(e.target.value === '' ? null : Number(e.target.value))}
          >
            <option value="">Overall</option>
            {(meta?.availableWeeks ?? []).map((option) => (
              <option key={option} value={option}>
                Week {option}
              </option>
            ))}
          </Form.Select>
        </Col>
        <Col xs={12} sm={7} md={5}>
          <Form.Label htmlFor="leaderboard-search" className="small fw-semibold mb-1">
            Find a member
          </Form.Label>
          <Form.Control
            id="leaderboard-search"
            type="search"
            placeholder="Search by name"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </Col>
      </Row>

      <ErrorNotice error={error} onRetry={load} />

      <ScoringNote settings={scoring} />

      {loading ? (
        <Loading label="Loading leaderboard" />
      ) : visible.length === 0 ? (
        <EmptyState title={search ? 'No members match that search' : 'No members yet'} />
      ) : (
        <Card className="shadow-sm">
          <div className="table-responsive">
            <Table hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col" style={{ width: '3.5rem' }}>#</th>
                  <th scope="col">Member</th>
                  <th scope="col" className="text-center">Picks</th>
                  <th scope="col" className="text-center" title="Wins-losses-ties">
                    W-L-T
                  </th>
                  {/* Point values are per group now, so the header cannot
                      state them - the group's settings page does. */}
                  <th scope="col" className="text-end" title="Points, scored by this group's rules">
                    Pts
                  </th>
                </tr>
              </thead>
              <tbody>
                {visible.map((row) => {
                  const isMe = profile && row.userId === profile.id;
                  return (
                    <tr
                      key={row.userId}
                      className={isMe ? 'table-active' : undefined}
                      style={{ cursor: 'pointer' }}
                      onClick={() =>
                        navigate(`/members/${row.userId}${week ? `?week=${week}` : ''}`)
                      }
                    >
                      <td className="fw-semibold">{medal(row.rank) ?? row.rank}</td>
                      {/* No "you" badge - the highlighted row already says
                          which one is yours, and the badge only crowded the
                          name it sat next to. */}
                      <td>
                        <MemberName displayName={row.displayName} username={row.username} stacked />
                        {/* Out of the pool. Worth saying on the row rather than
                            leaving it to be inferred from a record, since an
                            eliminated member keeps their points and can still
                            sit mid-table. */}
                        {row.eliminated && (
                          <Badge bg="danger-subtle" text="danger-emphasis" className="ms-2">
                            out
                          </Badge>
                        )}
                      </td>
                      <td className="text-center text-body-secondary">{row.totalPicks}</td>
                      {/* Always all three segments, ties included - a column
                          that changes shape row to row is harder to scan than
                          one that reads the same way every time. */}
                      {/* Charged minimums are inside this record already, so
                          the note explains a W-L-T that would otherwise not
                          add up to the picks beside it. */}
                      <td
                        className="text-center fw-semibold"
                        title={row.penaltyLosses > 0
                          ? `Includes ${row.penaltyLosses} loss${row.penaltyLosses === 1 ? '' : 'es'} `
                            + 'charged for minimums not met'
                          : undefined}
                      >
                        {row.wins}-{row.losses}-{row.pushes}
                        {row.penaltyLosses > 0 && <span className="text-warning-emphasis">*</span>}
                      </td>
                      {/* One decimal always, so the halves line up in a column
                          whose whole point is half-points. */}
                      <td className="text-end fw-semibold">{row.points.toFixed(1)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </div>
        </Card>
      )}
    </Container>
  );
}
