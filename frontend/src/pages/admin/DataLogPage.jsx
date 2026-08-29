import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, Card, Container, Table } from 'react-bootstrap';

import { EmptyState, ErrorNotice, Loading } from '../../components/common.jsx';
import { api } from '../../api/client.js';

const KIND_LABELS = {
  REFERENCE: 'Calendar / teams / coaches / records',
  GAMES: 'Schedule & lines',
  SCORES: 'Scores & grading',
  RANKINGS: 'Poll rankings',
  ROSTER: 'Team roster',
  ATS: 'Against the spread',
};

const STATUS_VARIANTS = {
  RUNNING: 'info',
  SUCCESS: 'success',
  FAILURE: 'danger',
};

function formatWhen(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
  });
}

/**
 * Every manual data load triggered from the Data page, newest first. Loads
 * run off the request thread now (see AsyncIngestService on the backend),
 * so this is the only place to actually see whether one succeeded.
 *
 * <p>Polls while anything is still RUNNING - a load usually takes a few
 * seconds, and a page that never updates on its own would just train
 * admins to mash refresh instead.
 */
export default function DataLogPage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setRows(await api.dataLoads());
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const anyRunning = rows.some((row) => row.status === 'RUNNING');

  useEffect(() => {
    if (!anyRunning) return undefined;
    const timer = setInterval(load, 4_000);
    return () => clearInterval(timer);
  }, [anyRunning, load]);

  return (
    <Container className="py-4 py-md-5">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h1 className="h3 mb-0">Data log</h1>
        <Button variant="outline-secondary" size="sm" onClick={load} disabled={loading}>
          Refresh
        </Button>
      </div>

      <ErrorNotice error={error} onRetry={load} />

      {loading ? (
        <Loading label="Loading data log" />
      ) : rows.length === 0 ? (
        <EmptyState title="No loads yet">
          <p className="small mb-0">
            Triggering a load from the Data page will show up here.
          </p>
        </EmptyState>
      ) : (
        <Card className="shadow-sm">
          <div className="table-responsive">
            <Table hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Started</th>
                  <th scope="col">Load</th>
                  <th scope="col">Season</th>
                  <th scope="col">Triggered by</th>
                  <th scope="col">Status</th>
                  <th scope="col">Result</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td className="small text-body-secondary text-nowrap">
                      {formatWhen(row.startedAt)}
                    </td>
                    <td className="small">
                      {KIND_LABELS[row.kind] ?? row.kind}
                      {row.parts && (
                        <div className="text-body-tertiary">{row.parts}</div>
                      )}
                    </td>
                    <td className="small text-body-secondary">{row.season ?? '—'}</td>
                    <td className="small">{row.triggeredBy}</td>
                    <td>
                      <Badge bg={STATUS_VARIANTS[row.status] ?? 'secondary'}>
                        {row.status === 'RUNNING' ? 'Running…' : row.status}
                      </Badge>
                    </td>
                    <td className="small">
                      {row.status === 'FAILURE' ? (
                        <span className="text-danger">{row.errorMessage}</span>
                      ) : (
                        row.resultSummary ?? '—'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        </Card>
      )}
    </Container>
  );
}
