import { useEffect, useState } from 'react';
import { Alert, Button, Card, Container, Form, ProgressBar } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { ErrorNotice } from '../../components/common.jsx';
import { api } from '../../api/client.js';

/** The four reference feeds, each its own CFBD call and its own checkbox. */
const REFERENCE_PARTS = [
  ['calendar', 'Calendar', 'Week boundaries, so future weeks are selectable.'],
  ['teams', 'Teams', 'Every FBS and FCS program. Team pages need this.'],
  ['coaches', 'Coaches', 'FBS only - the provider has no FCS coach records.'],
  ['records', 'Records', 'Season win/loss splits for every team.'],
];

/**
 * A year box. Free text rather than a dropdown of known seasons: the whole
 * point of these controls is to reach a season the site has never loaded.
 */
function YearInput({ id, value, onChange, disabled }) {
  return (
    <Form.Group className="d-flex align-items-center gap-2">
      <Form.Label htmlFor={id} className="small fw-semibold mb-0">
        Year
      </Form.Label>
      <Form.Control
        id={id}
        type="number"
        inputMode="numeric"
        min={1900}
        max={2100}
        style={{ width: '6.5rem' }}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    </Form.Group>
  );
}

/**
 * Manual data loading. Each button spends real CFBD quota, so the remaining
 * allowance is shown next to them rather than buried in a log.
 *
 * <p>Every load takes its own year, defaulted to the configured season. They
 * are separate fields on purpose - backfilling last season's rankings while
 * this season's schedule stays current is a normal thing to want, and one
 * shared year box would make it two trips.
 */
export default function AdminPage() {
  const [quota, setQuota] = useState(null);
  const [meta, setMeta] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);
  const [confirmingDeploy, setConfirmingDeploy] = useState(false);

  // Baked into the bundle by the deploy workflow from a GitHub secret, so the
  // button below calls Render directly and the backend is not involved at all.
  const deployHookUrl = import.meta.env.VITE_RENDER_DEPLOY_HOOK_URL ?? '';
  const deployConfigured = deployHookUrl !== '';

  // One year per load, plus which reference feeds to include.
  const [years, setYears] = useState({
    reference: '',
    games: '',
    scores: '',
    rankings: '',
    ats: '',
    settle: '',
  });
  const [parts, setParts] = useState(['calendar', 'teams', 'coaches']);

  const refreshQuota = () => api.quota().then(setQuota).catch(setError);

  useEffect(() => {
    refreshQuota();
    api
      .currentWeek()
      .then((data) => {
        setMeta(data);
        // Seed every year box with the season the site is running, so the
        // common case is still one click.
        const season = String(data.season);
        setYears({
          reference: season,
          games: season,
          scores: season,
          rankings: season,
          ats: season,
        });
      })
      .catch(() => setMeta(null));
  }, []);

  // Each load runs in the background on the server and returns immediately -
  // this just confirms it was queued. The Data log tab has the actual
  // result once it finishes.
  const run = async (label, action) => {
    setBusy(label);
    setError(null);
    setResult(null);
    try {
      await action();
      setResult({ label, queued: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  };

  /**
   * Hits Render's deploy hook straight from the browser.
   *
   * <p>`no-cors` because the hook returns no Access-Control-Allow-Origin
   * header. The request is still delivered and the deploy still runs - this
   * is a plain POST with no custom headers, so it is a "simple" request the
   * browser sends without a preflight - but the response is opaque. Without
   * `no-cors` the fetch would reject on the unreadable response and report a
   * failure for a deploy that actually started. The trade is that success
   * cannot be confirmed here, which is why the message below says triggered
   * rather than succeeded.
   */
  const runDeploy = async () => {
    setBusy('Deploy');
    setError(null);
    setResult(null);
    try {
      await fetch(deployHookUrl, { method: 'POST', mode: 'no-cors' });
      setResult({ label: 'Deploy', queued: false });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  };

  const setYear = (key) => (value) => setYears((current) => ({ ...current, [key]: value }));

  // An empty or nonsense box falls back to the server's own default rather
  // than sending it a year it will reject.
  const season = (key) => {
    const value = Number(years[key]);
    return Number.isInteger(value) && value > 1900 ? value : undefined;
  };

  const togglePart = (key) =>
    setParts((current) =>
      current.includes(key) ? current.filter((part) => part !== key) : [...current, key],
    );

  // From CFBD's own /info, refreshed server-side at most once a day - see
  // CfbdQuotaService. usedCalls/monthlyLimit are undefined for the first
  // instant before that first fetch has ever happened.
  const used = quota?.usedCalls;
  const limit = quota?.monthlyLimit;
  const hasQuota = used != null && limit != null;

  return (
    <Container className="py-4 py-md-5">
      <h1 className="h3 mb-4">Data admin</h1>

      <Card className="shadow-sm mb-4">
        <Card.Body>
          <div className="d-flex justify-content-between small mb-1">
            <span>API calls used this month</span>
            <span className="fw-semibold">{hasQuota ? `${used} / ${limit}` : '—'}</span>
          </div>
          <ProgressBar
            now={hasQuota ? (used / limit) * 100 : 0}
            variant={
              hasQuota && used > limit * 0.8
                ? 'danger'
                : hasQuota && used > limit * 0.5
                  ? 'warning'
                  : 'success'
            }
            style={{ height: 8 }}
          />
          {quota && !quota.configured && (
            <Alert variant="warning" className="mt-3 mb-0 py-2 small">
              No API key configured - set <code>app.cfbd.api-key</code>.
            </Alert>
          )}
          {quota?.resetAt && (
            <div className="small text-body-secondary mt-2">
              Resets {new Date(quota.resetAt).toLocaleDateString(undefined, {
                month: 'long',
                day: 'numeric',
              })}
              . Checked at most once a day.
            </div>
          )}
          {meta && (
            <div className="small text-body-secondary mt-2">
              Site season: <strong>{meta.season}</strong>, current week{' '}
              <strong>{meta.week}</strong>. Loading another year stores its data but does not
              change which season members see.
            </div>
          )}
        </Card.Body>
      </Card>

      <ErrorNotice error={error} />

      {result && (
        <Alert variant="success" dismissible onClose={() => setResult(null)}>
          {result.queued ? (
            <>
              <strong>{result.label} queued.</strong> It is running in the background - see{' '}
              <Alert.Link as={Link} to="/admin/data-log">
                Data log
              </Alert.Link>{' '}
              for the result.
            </>
          ) : (
            <>
              <strong>{result.label} requested.</strong> Render does not report back to the
              browser - check the Render dashboard to confirm it started.
            </>
          )}
        </Alert>
      )}

      <div className="d-grid gap-3">
        <Card className="shadow-sm">
          <Card.Body>
            <div className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-start gap-3">
              <div>
                <div className="fw-semibold">Calendar, teams, coaches &amp; records</div>
                <div className="small text-body-secondary">
                  Run once per season. One API call per feed selected.
                </div>
              </div>
              <div className="d-flex gap-2 align-items-center flex-shrink-0">
                <YearInput
                  id="year-reference"
                  value={years.reference}
                  onChange={setYear('reference')}
                  disabled={busy !== null}
                />
                <Button
                  disabled={busy !== null || parts.length === 0}
                  onClick={() =>
                    run('Reference ingest', () =>
                      api.ingestReference({ season: season('reference'), parts }))
                  }
                >
                  {busy === 'Reference ingest' ? 'Loading…' : `Load (${parts.length})`}
                </Button>
              </div>
            </div>

            <div className="d-flex flex-wrap gap-4 mt-3">
              {REFERENCE_PARTS.map(([key, label, hint]) => (
                <Form.Check
                  key={key}
                  type="checkbox"
                  id={`part-${key}`}
                  checked={parts.includes(key)}
                  disabled={busy !== null}
                  onChange={() => togglePart(key)}
                  label={
                    <span>
                      <span className="fw-semibold">{label}</span>
                      <span className="d-block small text-body-tertiary">{hint}</span>
                    </span>
                  }
                />
              ))}
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Schedule &amp; lines</div>
              <div className="small text-body-secondary">
                The whole season&apos;s games and every posted line, in 2 API calls. Re-run to pick
                up line movement - lines usually post a week or two ahead of kickoff.
              </div>
            </div>
            <div className="d-flex gap-2 align-items-center flex-shrink-0">
              <YearInput
                id="year-games"
                value={years.games}
                onChange={setYear('games')}
                disabled={busy !== null}
              />
              <Button
                disabled={busy !== null}
                onClick={() =>
                  run('Schedule ingest', () => api.ingestGames({ season: season('games') }))
                }
              >
                {busy === 'Schedule ingest' ? 'Loading…' : 'Load'}
              </Button>
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Scores &amp; grading</div>
              <div className="small text-body-secondary">
                Pulls final scores and win probabilities for the whole season and settles every
                pending pick. 1 API call.
              </div>
            </div>
            <div className="d-flex gap-2 align-items-center flex-shrink-0">
              <YearInput
                id="year-scores"
                value={years.scores}
                onChange={setYear('scores')}
                disabled={busy !== null}
              />
              <Button
                disabled={busy !== null}
                onClick={() =>
                  run('Score ingest', () => api.ingestScores({ season: season('scores') }))
                }
              >
                {busy === 'Score ingest' ? 'Loading…' : 'Load'}
              </Button>
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Poll rankings</div>
              <div className="small text-body-secondary">
                Every week and every poll in 1 API call. Run weekly as new polls publish — the
                committee on Tuesdays from about week 11, AP and coaches on Sundays.
              </div>
            </div>
            <div className="d-flex gap-2 align-items-center flex-shrink-0">
              <YearInput
                id="year-rankings"
                value={years.rankings}
                onChange={setYear('rankings')}
                disabled={busy !== null}
              />
              <Button
                disabled={busy !== null}
                onClick={() =>
                  run('Rankings ingest', () => api.ingestRankings({ season: season('rankings') }))
                }
              >
                {busy === 'Rankings ingest' ? 'Loading…' : 'Load'}
              </Button>
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Against the spread</div>
              <div className="small text-body-secondary">
                Every team&apos;s ATS record in 1 API call. The provider reports these as
                season-to-date totals with no weekly breakdown, so this refreshes the whole
                season — run it weekly once that week&apos;s games are final. Team and game
                pages show whatever this last stored; nothing fetches ATS on its own.
              </div>
            </div>
            <div className="d-flex gap-2 align-items-center flex-shrink-0">
              <YearInput
                id="year-ats"
                value={years.ats}
                onChange={setYear('ats')}
                disabled={busy !== null}
              />
              <Button
                disabled={busy !== null}
                onClick={() => run('ATS ingest', () => api.ingestAts({ season: season('ats') }))}
              >
                {busy === 'ATS ingest' ? 'Loading…' : 'Load'}
              </Button>
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Settle closed periods</div>
              <div className="small text-body-secondary">
                Charges every group minimum that a finished week or day closed without meeting,
                as losses on the leaderboard. An hourly job already does this — the button is
                for a group whose minimums were set after a period had already closed, or for
                seeing the result without waiting. Costs no API calls, and a period that has
                already been settled is skipped rather than charged twice.
              </div>
            </div>
            <div className="d-flex gap-2 align-items-center flex-shrink-0">
              <YearInput
                id="year-settle"
                value={years.settle}
                onChange={setYear('settle')}
                disabled={busy !== null}
              />
              <Button
                disabled={busy !== null}
                onClick={() => run('Settlement', () =>
                  api.settlePeriods({ season: season('settle') }))}
              >
                {busy === 'Settlement' ? 'Settling…' : 'Settle'}
              </Button>
            </div>
          </Card.Body>
        </Card>

        <Card className="shadow-sm border-danger-subtle">
          <Card.Body className="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center gap-3">
            <div>
              <div className="fw-semibold">Redeploy backend</div>
              <div className="small text-body-secondary">
                Triggers a fresh deploy of the live API on Render. Members lose their connection
                for a few minutes while it restarts - use it for a real deploy, not by habit.
                Render gives no readable answer to a browser, so this can confirm the request
                was sent but not that the deploy succeeded - watch Render for that.
              </div>
              {!deployConfigured && (
                <div className="small text-warning-emphasis mt-1">
                  Not configured - set the <code>RENDER_DEPLOY_HOOK_URL</code> repository secret
                  in GitHub, then re-run the frontend deploy workflow so it is picked up.
                </div>
              )}
            </div>
            {confirmingDeploy ? (
              <div className="d-flex gap-2 flex-shrink-0">
                <Button
                  variant="outline-secondary"
                  disabled={busy !== null}
                  onClick={() => setConfirmingDeploy(false)}
                >
                  Cancel
                </Button>
                <Button
                  variant="danger"
                  disabled={busy !== null}
                  onClick={() => {
                    setConfirmingDeploy(false);
                    runDeploy();
                  }}
                >
                  {busy === 'Deploy' ? 'Triggering…' : 'Confirm redeploy'}
                </Button>
              </div>
            ) : (
              <Button
                variant="outline-danger"
                disabled={busy !== null || !deployConfigured}
                onClick={() => setConfirmingDeploy(true)}
                className="flex-shrink-0"
              >
                Redeploy backend
              </Button>
            )}
          </Card.Body>
        </Card>
      </div>
    </Container>
  );
}
