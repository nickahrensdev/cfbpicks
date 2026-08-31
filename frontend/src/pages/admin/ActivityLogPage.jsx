import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Container, Form, Table } from 'react-bootstrap';
import { Link, useSearchParams } from 'react-router-dom';

import { EmptyState, ErrorNotice, formatLine, Loading, marketLabel, memberName } from '../../components/common.jsx';
import { api } from '../../api/client.js';

const ACTION_VARIANTS = {
  CREATE: ['success', 'Picked'],
  UPDATE: ['info', 'Changed'],
  CANCEL: ['danger', 'Cancelled'],
};

/**
 * Every pick action, newest first. Backed by an insert-only audit table, so
 * cancelled picks still appear even though the pick row is gone.
 */
export default function ActivityLogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const userId = searchParams.get('userId');

  const [rows, setRows] = useState([]);
  const [limit, setLimit] = useState(50);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.adminActivity({ userId: userId ?? undefined, limit }));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [userId, limit]);

  useEffect(() => {
    load();
  }, [load]);

  const members = useMemo(() => {
    const seen = new Map();
    rows.forEach((row) => seen.set(row.userId, row.displayName));
    return [...seen.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [rows]);

  return (
    <Container className="py-4 py-md-5">
      <h1 className="h3 mb-4">Activity log</h1>

      <div className="d-flex flex-wrap gap-3 align-items-end mb-4">
        <Form.Group style={{ minWidth: '14rem' }}>
          <Form.Label htmlFor="activity-member" className="small fw-semibold mb-1">
            Member
          </Form.Label>
          <Form.Select
            id="activity-member"
            value={userId ?? ''}
            onChange={(e) =>
              setSearchParams(e.target.value ? { userId: e.target.value } : {})
            }
          >
            <option value="">Everyone</option>
            {members.map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </Form.Select>
        </Form.Group>

        <Form.Group style={{ minWidth: '9rem' }}>
          <Form.Label htmlFor="activity-limit" className="small fw-semibold mb-1">
            Show
          </Form.Label>
          <Form.Select
            id="activity-limit"
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
          >
            {[25, 50, 100, 200].map((option) => (
              <option key={option} value={option}>
                Last {option}
              </option>
            ))}
          </Form.Select>
        </Form.Group>

        <Button variant="outline-secondary" onClick={load} disabled={loading}>
          Refresh
        </Button>
      </div>

      <ErrorNotice error={error} onRetry={load} />

      {loading ? (
        <Loading label="Loading activity" />
      ) : rows.length === 0 ? (
        <EmptyState title="No activity yet">
          <p className="small mb-0">Pick actions will appear here as members make them.</p>
        </EmptyState>
      ) : (
        <Card className="shadow-sm">
          <div className="table-responsive">
            <Table hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Member</th>
                  <th scope="col">Action</th>
                  <th scope="col">Game</th>
                  <th scope="col">Market</th>
                  <th scope="col">Detail</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const [variant, label] = ACTION_VARIANTS[row.action] ?? ['secondary', row.action];
                  const line = formatLine(row.lockedLine, row.selection);
                  const previousLine =
                    row.previousLockedLine == null
                      ? null
                      : formatLine(row.previousLockedLine, row.previousSelection ?? row.selection);

                  return (
                    <tr key={row.id}>
                      <td className="small text-body-secondary text-nowrap">
                        {new Date(row.at).toLocaleString(undefined, {
                          month: 'short',
                          day: 'numeric',
                          hour: 'numeric',
                          minute: '2-digit',
                        })}
                      </td>
                      <td>
                        <Link to={`/members/${row.userId}`} className="text-decoration-none">
                          {memberName(row.displayName, row.username)}
                        </Link>
                      </td>
                      <td>
                        <Badge bg={variant}>{label}</Badge>
                      </td>
                      <td className="small">
                        <Link to={`/games/${row.gameId}`} className="text-decoration-none">
                          {row.game}
                        </Link>
                      </td>
                      <td className="small text-body-secondary">{marketLabel(row.market)}</td>
                      <td className="small">
                        {row.action === 'UPDATE' && previousLine ? (
                          <>
                            <span className="text-body-secondary text-decoration-line-through">
                              {row.previousSelection} {previousLine}
                            </span>
                            {' → '}
                            <span className="fw-semibold">
                              {row.selection} {line}
                            </span>
                          </>
                        ) : (
                          <span className={row.action === 'CANCEL' ? 'text-body-secondary' : ''}>
                            {row.selection} {line}
                          </span>
                        )}
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
