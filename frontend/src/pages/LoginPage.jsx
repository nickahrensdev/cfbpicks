import { useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { Navigate, useLocation, useSearchParams } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { appUrl } from '../lib/appUrl.js';

export default function LoginPage() {
  const { session, signIn, signUp, resendConfirmation, configured } = useAuth();
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
  // Offered only once we know the account exists but is not confirmed.
  const [canResend, setCanResend] = useState(false);

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

    // Where a confirmation link should land. Shared by signup and resend -
    // a resent link built without the base path 404s exactly like the first.
    const confirmTo = appUrl(joinToken ? `/join/${joinToken}` : '/');

    // Both fields are optional at the form; the API seeds anything left blank
    // from the email address, then makes the username unique if it collides.
    const { data, error: authError } =
      mode === 'signin'
        ? await signIn(email, password)
        // Someone who arrived from an invitation is sent back to it after
        // confirming, rather than to the board - the group they were invited
        // to is the whole reason they made an account.
        : await signUp(email, password, displayName.trim(), username.trim(), confirmTo);

    if (authError) {
      setError(authError.message);
      // The one error with an obvious remedy: their link expired, or they
      // never opened it. Offer another rather than leaving them stuck on a
      // message that only describes the problem.
      setCanResend(authError.code === 'email_not_confirmed');
    } else if (mode === 'signup') {
      // What actually happened, rather than a message covering every case.
      //
      // Sign-up has three outcomes and the response tells them apart, so the
      // old "Account created. Check your email if confirmation is required"
      // was both hedging about something knowable and, in one case, wrong:
      // Supabase answers a duplicate address with a success, not an error, so
      // an existing account was being congratulated on being created.
      if (data?.session) {
        // Already signed in - the redirect at the top of this component takes
        // over on the next render, so there is nobody left to read a message.
      } else if (data?.user && (data.user.identities?.length ?? 0) === 0) {
        // The anti-enumeration response: a real user object with no identity
        // attached, which is Supabase's way of not confirming out loud that
        // the address is taken. Saying so plainly is the right trade here -
        // this is a private league, and the alternative is someone retyping a
        // password they already have while being told it worked.
        // Supabase answers identically whether the address is confirmed or
        // not - that is the point of the obfuscation - so the message must
        // not claim to know which. It used to say "sign in below", which is
        // wrong for somebody who never confirmed, and signing in would fail.
        setNotice({
          variant: 'warning',
          text: 'That address is already registered. If you have not confirmed it yet, check '
            + 'your email or send a new link below. Otherwise sign in.',
        });
        setCanResend(true);
        setMode('signin');
      } else {
        setNotice({
          variant: 'success',
          text: `Confirm your email to finish. We sent a link to ${email} - open it, then sign in.`,
        });
        setMode('signin');
      }
    }
    setBusy(false);
  };

  /** Sends another confirmation link to whatever is in the email box. */
  const resend = async () => {
    if (!email.trim()) {
      setError('Enter your email address first.');
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);

    const { error: resendError } = await resendConfirmation(
      email.trim(),
      appUrl(joinToken ? `/join/${joinToken}` : '/'),
    );

    if (resendError) {
      setError(resendError.message);
    } else {
      setNotice({
        variant: 'success',
        text: `New link sent to ${email.trim()}. It is good for 24 hours.`,
      });
      setCanResend(false);
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
              {/* Carries its own variant: "check your email" and "that
                  address is already taken" are both non-errors, but they are
                  not the same kind of news and should not look identical. */}
              {notice && <Alert variant={notice.variant}>{notice.text}</Alert>}

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

                {/* Only once something has told us the account exists and is
                    unconfirmed. Offering it unprompted would invite people to
                    spend the email quota on addresses that never signed up. */}
                {canResend && (
                  <div className="d-grid mb-3">
                    <Button variant="outline-secondary" disabled={busy} onClick={resend}>
                      Send a new confirmation link
                    </Button>
                  </div>
                )}

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
