import { useCallback, useEffect, useState } from 'react';
import { Badge, Card, Col, Container, Row, Table } from 'react-bootstrap';
import { useParams } from 'react-router-dom';

import { TeamLink, TeamLogo } from '../components/links.jsx';
import { BackButton, ErrorNotice, Loading } from '../components/common.jsx';
import { api } from '../api/client.js';

const CLASS_YEARS = { 1: 'Freshman', 2: 'Sophomore', 3: 'Junior', 4: 'Senior', 5: 'Graduate' };

const height = (value) =>
  value ? `${Math.floor(value / 12)}' ${value % 12}"` : null;

function Stat({ label, value }) {
  if (!value) return null;
  return (
    <Col xs={6} md={3}>
      <div className="small text-body-secondary">{label}</div>
      <div className="fw-semibold">{value}</div>
    </Col>
  );
}

/** "6' 2\", 210 lb" from whichever source has the numbers. */
function formatDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? null
    : date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
}

export default function AthletePage() {
  const { id } = useParams();
  const [athlete, setAthlete] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAthlete(await api.athlete(id));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <Loading label="Loading player" />;
  if (error) {
    return (
      <Container className="py-4">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }
  if (!athlete) return null;

  const espn = athlete.espn;

  // Both halves now come from ESPN - the flat fields are parsed out of its
  // athlete resource, `espn` is the fuller biography beside them. The
  // fallbacks stay because the two are populated independently: a player can
  // have a birth city on one and not the other.
  const hometown =
    [athlete.homeCity, athlete.homeState].filter(Boolean).join(', ')
    || [espn?.birthCity, espn?.birthState].filter(Boolean).join(', ')
    || athlete.homeCountry
    || espn?.birthCountry;

  const jersey = athlete.jersey ?? (espn?.jersey ? Number(espn.jersey) : null);
  const position = athlete.position ?? espn?.position;
  const classYear = CLASS_YEARS[athlete.year] ?? athlete.year ?? espn?.experience;

  return (
    <Container className="py-4 py-md-5">
      <BackButton className="mb-3" fallback="/games" />

      <Card className="shadow-sm mb-4">
        <Card.Body>
          <div className="d-flex align-items-start gap-3 flex-wrap mb-3">
            {/* ESPN's headshot when they have one, the team crest otherwise -
                a player page with a blank space where a face goes reads as
                broken rather than as missing data. */}
            {(athlete.headshotUrl ?? espn?.headshotUrl) ? (
              <img
                src={athlete.headshotUrl ?? espn?.headshotUrl}
                alt=""
                width={88}
                height={64}
                loading="lazy"
                className="rounded bg-body-tertiary"
                style={{ objectFit: 'cover' }}
              />
            ) : (
              <TeamLogo team={athlete.team} size={56} />
            )}
            <div className="flex-grow-1">
              <h1 className="h3 mb-1">
                {jersey != null && <span className="text-body-tertiary me-2">#{jersey}</span>}
                {athlete.firstName} {athlete.lastName}
              </h1>
              <div className="text-body-secondary">
                {position}
                {athlete.team && (
                  <>
                    {' · '}
                    <TeamLink team={athlete.team} logo={false} />
                  </>
                )}
              </div>
              {espn?.status && espn.status !== 'Active' && (
                <Badge bg="secondary-subtle" text="secondary-emphasis" className="mt-2">
                  {espn.status}
                </Badge>
              )}
            </div>
            {espn?.espnUrl && (
              <a
                href={espn.espnUrl}
                target="_blank"
                rel="noreferrer"
                className="small text-decoration-none"
              >
                ESPN profile ↗
              </a>
            )}
          </div>

          <Row className="g-3">
            <Stat label="Class" value={classYear} />
            <Stat label="Height" value={height(athlete.height) ?? espn?.displayHeight} />
            <Stat
              label="Weight"
              value={athlete.weight ? `${athlete.weight} lb` : espn?.displayWeight}
            />
            <Stat label="Hometown" value={hometown} />
            <Stat label="Age" value={espn?.age} />
            <Stat label="Born" value={formatDate(espn?.dateOfBirth)} />
          </Row>
        </Card.Body>
      </Card>

    </Container>
  );
}
