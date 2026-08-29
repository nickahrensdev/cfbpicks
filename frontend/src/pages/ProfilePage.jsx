import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';

import { useProfile } from '../auth/ProfileProvider.jsx';
import { Loading } from '../components/common.jsx';
import { api } from '../api/client.js';
import { applyTheme } from '../lib/theme.js';

// Swatch colors shown on each theme button - the same values themes.css
// (or, for Midnight, theme.scss) compiles for that theme's light mode.
const THEME_OPTIONS = [
  { value: 'MIDNIGHT', label: 'Midnight', swatch: '#468189' },
  { value: 'OCEAN', label: 'Ocean', swatch: '#1d6fa5' },
  { value: 'EMBER', label: 'Ember', swatch: '#b5651d' },
  { value: 'FOREST', label: 'Forest', swatch: '#2f6b3a' },
  { value: 'SLATE', label: 'Slate', swatch: '#4a5a6a' },
];

export default function ProfilePage() {
  const { profile, loading, refresh } = useProfile();
  const [displayName, setDisplayName] = useState('');
  const [notice, setNotice] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [themeError, setThemeError] = useState(null);
  const [savingTheme, setSavingTheme] = useState(false);

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

  /**
   * A preference toggle, not a validated field - it saves the moment you
   * click it rather than waiting for a separate Save button. Applied
   * locally first for an instant preview, then persisted; if the save
   * fails the local preview is rolled back to whatever the account still
   * has on file.
   */
  const chooseTheme = async (theme, colorMode) => {
    setSavingTheme(true);
    setThemeError(null);
    applyTheme(theme, colorMode);
    try {
      await api.updateTheme(theme, colorMode);
      await refresh();
    } catch (err) {
      applyTheme(profile.theme, profile.colorMode);
      setThemeError(err.message);
    } finally {
      setSavingTheme(false);
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

          <Card className="shadow-sm mt-4">
            <Card.Body className="p-4">
              <h2 className="h5 mb-3">Appearance</h2>
              {themeError && (
                <Alert variant="danger" className="py-2 small">
                  {themeError}
                </Alert>
              )}

              <div className="mb-3">
                <div className="small fw-semibold mb-2">Color scheme</div>
                <div className="d-flex flex-wrap gap-2">
                  {THEME_OPTIONS.map((option) => (
                    <Button
                      key={option.value}
                      variant={profile.theme === option.value ? 'primary' : 'outline-secondary'}
                      size="sm"
                      disabled={savingTheme}
                      onClick={() => chooseTheme(option.value, profile.colorMode)}
                      className="d-inline-flex align-items-center gap-2"
                    >
                      <span
                        className="rounded-circle flex-shrink-0"
                        style={{
                          width: 14,
                          height: 14,
                          background: option.swatch,
                          border: '1px solid rgba(0,0,0,0.15)',
                        }}
                        aria-hidden="true"
                      />
                      {option.label}
                    </Button>
                  ))}
                </div>
              </div>

              <div>
                <div className="small fw-semibold mb-2">Mode</div>
                <div className="d-flex gap-2">
                  {[
                    ['LIGHT', 'Light'],
                    ['DARK', 'Dark'],
                  ].map(([value, label]) => (
                    <Button
                      key={value}
                      variant={profile.colorMode === value ? 'primary' : 'outline-secondary'}
                      size="sm"
                      disabled={savingTheme}
                      onClick={() => chooseTheme(profile.theme, value)}
                    >
                      {label}
                    </Button>
                  ))}
                </div>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}
