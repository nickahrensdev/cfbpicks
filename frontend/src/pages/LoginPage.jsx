import { useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { Navigate, useLocation, useSearchParams } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';

export default function LoginPage() {
  const { session, signIn, signUp, configured } = useAuth();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  // Arrived from a share link. Signing in returns to the invitation rather
  // than to the board, so the group they were invited to is not lost on the
  // way through the login screen.
  const joinToken = searchParams.get('join');

  const [mode, setMode] = useState(searchParams.get('mode') === 'signup' ? 'signup' : 'signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [username, setUsername] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  if (session) {
    const destination = joinToken ? `/join/${joinToken}` : (location.state?.from ?? '/');
    return <Navigate to={destination} replace />;
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

    // Both fields are optional at the form; the API seeds anything left blank
    // from the email address, then makes the username unique if it collides.
    const { error: authError } =
      mode === 'signin'
        ? await signIn(email, password)
        : await signUp(email, password, displayName.trim(), username.trim());

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
                {joinToken
                  ? 'Sign in and we will take you straight back to the invitation.'
                  : 'Members only - picks and the leaderboard need an account.'}
              </p>

              {error && <Alert variant="danger">{error}</Alert>}
              {notice && <Alert variant="success">{notice}</Alert>}

              <Form onSubmit={submit}>
                {mode === 'signup' && (
                  <>
                    {/* Two names, because one cannot do both jobs: the display
                        name is what you are called and may repeat, the username
                        is who you are and has to be unique. */}
                    <Form.Group className="mb-3" controlId="display-name">
                      <Form.Label>Display name</Form.Label>
                      <Form.Control
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                        autoComplete="name"
                        maxLength={20}
                        placeholder="Nick Ahrens"
                      />
                      <Form.Text>
                        Up to 20 characters. Does not have to be unique.
                      </Form.Text>
                    </Form.Group>

                    <Form.Group className="mb-3" controlId="username">
                      <Form.Label>Username</Form.Label>
                      <Form.Control
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        autoComplete="username"
                        minLength={2}
                        maxLength={20}
                        pattern="[A-Za-z0-9][A-Za-z0-9._-]*[A-Za-z0-9]"
                        placeholder="nick"
                      />
                      <Form.Text>
                        2-20 characters, no spaces. Unique across the site - shown as
                        @username.
                      </Form.Text>
                    </Form.Group>
                  </>
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
