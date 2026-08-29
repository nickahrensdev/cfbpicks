import { useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { Navigate, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';

export default function LoginPage() {
  const { session, signIn, signUp, configured } = useAuth();
  const location = useLocation();

  const [mode, setMode] = useState('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  if (session) {
    return <Navigate to={location.state?.from ?? '/'} replace />;
  }

  if (!configured) {
    return (
      <Container className="py-5">
        <Alert variant="warning">
          <Alert.Heading className="h5">Supabase is not configured</Alert.Heading>
          <p className="mb-0">
            Set <code>VITE_SUPABASE_URL</code> and <code>VITE_SUPABASE_ANON_KEY</code> in{' '}
            <code>frontend/.env.local</code>, then restart the dev server. Both come from
            Supabase → Project Settings → API.
          </p>
        </Alert>
      </Container>
    );
  }

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);

    const { error: authError } =
      mode === 'signin'
        ? await signIn(email, password)
        : await signUp(email, password, displayName.trim() || email.split('@')[0]);

    if (authError) {
      setError(authError.message);
    } else if (mode === 'signup') {
      // Supabase may require email confirmation depending on project settings.
      setNotice('Account created. Check your email if confirmation is required, then sign in.');
      setMode('signin');
    }
    setBusy(false);
  };

  return (
    <Container className="py-4 py-md-5">
      <Row className="justify-content-center">
        <Col md={7} lg={5}>
          <Card className="shadow-sm">
            <Card.Body className="p-4">
              <h1 className="h4 mb-1">{mode === 'signin' ? 'Sign in' : 'Create an account'}</h1>
              <p className="text-body-secondary small mb-4">
                Members only - picks and the leaderboard need an account.
              </p>

              {error && <Alert variant="danger">{error}</Alert>}
              {notice && <Alert variant="success">{notice}</Alert>}

              <Form onSubmit={submit}>
                {mode === 'signup' && (
                  <Form.Group className="mb-3" controlId="display-name">
                    <Form.Label>Display name</Form.Label>
                    <Form.Control
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      autoComplete="nickname"
                      maxLength={55}
                      placeholder="How you appear on the leaderboard"
                    />
                  </Form.Group>
                )}

                <Form.Group className="mb-3" controlId="email">
                  <Form.Label>Email</Form.Label>
                  <Form.Control
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                    required
                  />
                </Form.Group>

                <Form.Group className="mb-4" controlId="password">
                  <Form.Label>Password</Form.Label>
                  <Form.Control
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                    minLength={6}
                    required
                  />
                </Form.Group>

                <div className="d-grid">
                  <Button type="submit" disabled={busy}>
                    {busy && <Spinner as="span" size="sm" animation="border" className="me-2" />}
                    {mode === 'signin' ? 'Sign in' : 'Sign up'}
                  </Button>
                </div>
              </Form>

              <div className="text-center mt-3">
                <Button
                  variant="link"
                  className="p-0 small"
                  onClick={() => {
                    setMode(mode === 'signin' ? 'signup' : 'signin');
                    setError(null);
                  }}
                >
                  {mode === 'signin'
                    ? 'Need an account? Sign up'
                    : 'Already have an account? Sign in'}
                </Button>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}
