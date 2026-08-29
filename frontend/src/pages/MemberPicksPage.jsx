import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, Container, Form } from 'react-bootstrap';
import { useParams, useSearchParams } from 'react-router-dom';

import { TeamLink } from '../components/links.jsx';
import {
  EmptyState,
  ErrorNotice,
  Loading,
  ResultBadge,
  formatKickoff,
  formatLine,
} from '../components/common.jsx';
import { api } from '../api/client.js';

/**
 * Another member's card. The API only returns picks on games that have
 * already kicked off, so this page cannot be used to scout the field.
 */
export default function MemberPicksPage() {
  const { userId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();

  const [meta, setMeta] = useState(null);
  const [week, setWeek] = useState(() => {
    const value = searchParams.get('week');
    return value ? Number(value) : null;
  });
  const [picks, setPicks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .currentWeek()
      .then((current) => {
        setMeta(current);
        setWeek((value) => value ?? current.week);
      })
      .catch(setError);
  }, []);

  const load = useCallback(async () => {
    if (week == null) return;
    setLoading(true);
    setError(null);
    try {
      setPicks(await api.memberPicks(userId, { season: meta?.season, week }));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [userId, meta, week]);

  useEffect(() => {
    load();
  }, [load]);

  const changeWeek = (next) => {
    setWeek(next);
    setSearchParams(next ? { week: String(next) } : {});
  };

  return (
    <Container className="py-4 py-md-5">
      <h1 className="h3 mb-4">Member picks</h1>

      <Form.Group className="mb-4" style={{ maxWidth: '14rem' }}>
        <Form.Label htmlFor="member-week" className="small fw-semibold mb-1">
          Week
        </Form.Label>
        <Form.Select
          id="member-week"
          value={week ?? ''}
          onChange={(e) => changeWeek(Number(e.target.value))}
        >
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
            Either this member has no picks in week {week}, or none of their games have kicked off.
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
