import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Col, Container, Form, Row, Table } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

import { EmptyState, ErrorNotice, Loading } from '../components/common.jsx';
import { useProfile } from '../auth/ProfileProvider.jsx';
import { api } from '../api/client.js';

const medal = (rank) => (rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : null);

export default function LeaderboardPage() {
  const { profile } = useProfile();
  const navigate = useNavigate();

  const [meta, setMeta] = useState(null);
  // null means Overall, which is the default view.
  const [week, setWeek] = useState(null);
  const [search, setSearch] = useState('');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.currentWeek().then(setMeta).catch(() => setMeta(null));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.leaderboard({ week: week ?? undefined }));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [week]);

  useEffect(() => {
    load();
  }, [load]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return rows;
    return rows.filter((row) => row.displayName.toLowerCase().includes(term));
  }, [rows, search]);

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
                  <th scope="col" className="text-center">Record</th>
                  <th scope="col" className="text-end">Win %</th>
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
                      <td>
                        {row.displayName}
                        {isMe && (
                          <Badge bg="primary-subtle" text="primary-emphasis" className="ms-2">
                            you
                          </Badge>
                        )}
                      </td>
                      <td className="text-center text-body-secondary">{row.totalPicks}</td>
                      <td className="text-center fw-semibold">
                        {row.wins}-{row.losses}
                        {row.pushes > 0 && (
                          <span className="fw-normal text-body-secondary">-{row.pushes}</span>
                        )}
                      </td>
                      <td className="text-end text-body-secondary">
                        {row.winPercentage == null
                          ? '-'
                          : `${(row.winPercentage * 100).toFixed(1)}%`}
                      </td>
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
