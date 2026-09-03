import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Collapse, Container, Form, InputGroup, Table } from 'react-bootstrap';
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
  const [open, setOpen] = useState(false);

  if (!settings) return null;

  const live = MARKET_LABELS.filter(([key]) => settings[`${key}Enabled`]);
  if (live.length === 0) return null;

  return (
    // Behind a disclosure. It is reference material - read once when you join
    // a group, never again - and as a permanent paragraph of small grey text
    // it pushed the standings themselves further down every single visit.
    <div className="mb-3">
      <Button
        variant="link"
        size="sm"
        className="p-0 text-body-secondary text-decoration-none"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-controls="scoring-note"
      >
        How points work {open ? '▲' : '▼'}
      </Button>

      <Collapse in={open}>
        <div id="scoring-note">
          <div className="small text-body-secondary border-start border-3 ps-3 mt-2">
            {live.map(([key, label]) => (
              <div key={key}>
                <span className="fw-semibold">{label}</span>{' '}
                {points(settings[`${key}WinPoints`])} win /{' '}
                {points(settings[`${key}LossPoints`])} loss /{' '}
                {points(settings[`${key}PushPoints`])} push
              </div>
            ))}
            <div className="mt-1">Ties on points break on most wins, then fewest losses.</div>
          </div>
        </div>
      </Collapse>
    </div>
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

  /**
   * Whether this period has any result in it yet.
   *
   * <p>Before anyone picks, every member ties on nothing and the server ranks
   * them all first - which drew a gold medal on every row. Four golds is not
   * a standing, it is a table that looks broken. Medals wait until there is
   * something to be first at.
   */
  const started = useMemo(
    () => rows.some((row) => row.totalPicks > 0 || row.points !== 0),
    [rows],
  );

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
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">Leaderboard</h1>
        <Button variant="outline-secondary" size="sm" onClick={load} disabled={loading}>
          Refresh
        </Button>
      </div>

      {/* One row, no stacked labels. Two full-width controls each under its
          own heading took a third of a phone screen before a single standing
          was visible; the select names itself ("Overall", "Week 3") and the
          search has a placeholder, so the labels were only restating them. */}
      <InputGroup className="mb-3">
        <Form.Select
          id="leaderboard-week"
          aria-label="Period"
          className="flex-grow-0 w-auto"
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
        <Form.Control
          id="leaderboard-search"
          type="search"
          placeholder="Find a member"
          aria-label="Find a member"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </InputGroup>

      <ErrorNotice error={error} onRetry={load} />

      <ScoringNote settings={scoring} />

      {loading ? (
        <Loading label="Loading leaderboard" />
      ) : visible.length === 0 ? (
        <EmptyState title={search ? 'No members match that search' : 'No members yet'} />
      ) : (
        <Card className="shadow-sm">
          {/* A table of zeroes with no explanation reads as a broken page
              rather than as a season that has not started. Said once, above
              the table, instead of repeated down every row. */}
          {!started && (
            <div className="small text-body-secondary border-bottom px-3 py-2">
              No picks {week ? 'in this week' : 'yet'} - everyone starts level.
            </div>
          )}
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
                      <td className={started ? 'fw-semibold' : 'fw-semibold text-body-tertiary'}>
                        {(started && medal(row.rank)) || row.rank}
                      </td>
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
