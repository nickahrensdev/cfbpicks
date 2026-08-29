import { useCallback, useEffect, useState } from 'react';
import { Card, Container, Table } from 'react-bootstrap';
import { useParams } from 'react-router-dom';

import { TeamLink } from '../components/links.jsx';
import { ErrorNotice, Loading } from '../components/common.jsx';
import { api } from '../api/client.js';

export default function CoachPage() {
  const { id } = useParams();
  const [coach, setCoach] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCoach(await api.coach(id));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <Loading label="Loading coach" />;
  if (error) {
    return (
      <Container className="py-4">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }
  if (!coach) return null;

  const career = coach.seasons.reduce(
    (totals, season) => ({
      wins: totals.wins + (season.wins ?? 0),
      losses: totals.losses + (season.losses ?? 0),
      ties: totals.ties + (season.ties ?? 0),
    }),
    { wins: 0, losses: 0, ties: 0 },
  );

  return (
    <Container className="py-4 py-md-5">
      <Card className="shadow-sm mb-4">
        <Card.Body>
          <h1 className="h3 mb-1">
            {coach.firstName} {coach.lastName}
          </h1>
          <div className="text-body-secondary">
            Career {career.wins}-{career.losses}
            {career.ties > 0 && `-${career.ties}`}
            {coach.hireDate && (
              <> · hired {new Date(coach.hireDate).toLocaleDateString()}</>
            )}
          </div>
        </Card.Body>
      </Card>

      <Card className="shadow-sm">
        <Card.Body>
          <h2 className="h6 text-uppercase text-body-secondary mb-3">Season by season</h2>
          <div className="table-responsive">
            <Table hover size="sm" className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Season</th>
                  <th scope="col">School</th>
                  <th scope="col" className="d-none d-sm-table-cell">Conference</th>
                  <th scope="col">Record</th>
                  <th scope="col" className="d-none d-md-table-cell">SP+</th>
                </tr>
              </thead>
              <tbody>
                {coach.seasons.map((season) => (
                  <tr key={`${season.season}-${season.school}`}>
                    <td>{season.season}</td>
                    <td>
                      <TeamLink
                        team={{ id: season.teamId, school: season.school }}
                        name={season.school}
                        logo={false}
                      />
                    </td>
                    <td className="d-none d-sm-table-cell small text-body-secondary">
                      {season.conference}
                    </td>
                    <td>
                      {season.wins ?? 0}-{season.losses ?? 0}
                      {season.ties ? `-${season.ties}` : ''}
                    </td>
                    <td className="d-none d-md-table-cell">{season.spOverall ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
}
