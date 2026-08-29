import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';

import { useProfile } from '../auth/ProfileProvider.jsx';
import { Loading } from '../components/common.jsx';
import { api } from '../api/client.js';

export default function ProfilePage() {
  const { profile, loading, refresh } = useProfile();
  const [displayName, setDisplayName] = useState('');
  const [notice, setNotice] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (profile) setDisplayName(profile.displayName);
  }, [profile]);

  if (loading || !profile) {
    return <Loading label="Loading your profile" />;
  }

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setNotice(null);
    setError(null);
    try {
      await api.updateDisplayName(displayName.trim());
      await refresh();
      setNotice('Display name updated.');
    } catch (err) {
      // The name is well-formed but taken, or it failed validation.
      setError(err.fieldErrors?.displayName ?? err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Container className="py-4 py-md-5">
      <Row className="justify-content-center">
        <Col md={8} lg={6}>
          <h1 className="h3 mb-4">Your profile</h1>

          <Card className="shadow-sm">
            <Card.Body className="p-4">
              {notice && <Alert variant="success">{notice}</Alert>}
              {error && <Alert variant="danger">{error}</Alert>}

              <Form onSubmit={submit}>
                <Form.Group className="mb-3" controlId="profile-display-name">
                  <Form.Label>Display name</Form.Label>
                  <Form.Control
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    minLength={2}
                    maxLength={40}
                    required
                  />
                  <Form.Text>
                    2-40 characters. Letters, numbers, spaces, dots, dashes and underscores. Must be
                    unique.
                  </Form.Text>
                </Form.Group>

                <Form.Group className="mb-4">
                  <Form.Label>Email</Form.Label>
                  <Form.Control value={profile.email} disabled readOnly />
                  <Form.Text>Managed by your sign-in, not editable here.</Form.Text>
                </Form.Group>

                <div className="d-flex justify-content-between align-items-center">
                  <span className="small text-body-secondary">
                    Role: <strong>{profile.role}</strong>
                  </span>
                  <Button
                    type="submit"
                    disabled={saving || displayName.trim() === profile.displayName}
                  >
                    {saving && <Spinner as="span" size="sm" animation="border" className="me-2" />}
                    Save
                  </Button>
                </div>
              </Form>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}
