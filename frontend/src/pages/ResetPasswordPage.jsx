import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { supabase } from '../lib/supabase.js';
import { useAuth } from '../auth/AuthProvider.jsx';
import { Loading } from '../components/common.jsx';

/**
 * Where a password reset email lands, and where the new password is set.
 *
 * <p>Built the same way as {@code ConfirmEmailPage} and for the same reason:
 * the email carries a token hash to a route in this app rather than to
 * Supabase's own /auth/v1/verify, whose fallback keeps only the origin of Site
 * URL and drops the base path the site is served under. See
 * docs/email-templates/README.md.
 *
 * <p>The link is the proof of identity. Exchanging it for a session is what
 * authorises the change, which is why no current password is asked for - the
 * person resetting one by definition does not have it.
 *
 * <p>The template that feeds this:
 *
 * <pre>
 *   &lt;a href="{{ .SiteURL }}reset-password?token_hash={{ .TokenHash }}&amp;type=recovery"&gt;
 * </pre>
 */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { updatePassword } = useAuth();

  const tokenHash = params.get('token_hash');

  const [verifying, setVerifying] = useState(true);
  const [linkError, setLinkError] = useState(null);

  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  /*
   * Exchange the link for a session before showing the form.
   *
   * Done first rather than on submit so a dead link says so immediately,
   * instead of after someone has chosen and typed a new password twice.
   */
  const verify = useCallback(async () => {
    if (!tokenHash) {
      setLinkError('That link is missing its reset code. Try the newest email.');
      setVerifying(false);
      return;
    }

    const { error: verifyError } = await supabase.auth.verifyOtp({
      token_hash: tokenHash,
      type: 'recovery',
    });

    if (verifyError) {
      setLinkError(verifyError.message);
    }
    setVerifying(false);
  }, [tokenHash]);

  useEffect(() => {
    verify();
  }, [verify]);

  const submit = async (event) => {
    event.preventDefault();

    // Checked here rather than left to the server: the two boxes exist to
    // catch a typo in something nobody can see themselves type.
    if (password !== confirmation) {
      setError('Those two passwords do not match.');
      return;
    }

    setBusy(true);
    setError(null);

    const { error: updateError } = await updatePassword(password);

    if (updateError) {
      // Shown verbatim. The useful failures here are the password policy and
      // an expired session, and Supabase's own wording for both is clearer
      // than a generic message that hides which one it was.
      setError(updateError.message);
      setBusy(false);
      return;
    }

    setDone(true);
    setBusy(false);
  };

  if (verifying) {
    return <Loading label="Checking your reset link" />;
  }

  const shell = (children) => (
    <Container className="py-4 py-md-5">
      <Row className="justify-content-center">
        <Col md={7} lg={5}>
          <Card className="shadow-sm">
            <Card.Body className="p-4">{children}</Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );

  if (linkError) {
    return shell(
      <>
        <h1 className="h4 mb-3">That link did not work</h1>
        <Alert variant="danger" className="small">
          {linkError}
        </Alert>
        <p className="text-body-secondary small">
          Reset links can only be used once, and they expire. You can send yourself another
          from the sign-in page.
        </p>
        <Button onClick={() => navigate('/login')}>Go to sign in</Button>
      </>,
    );
  }

  if (done) {
    return shell(
      <>
        <h1 className="h4 mb-3">Password changed</h1>
        {/* Signed in already - verifyOtp established the session and the
            update kept it - so this goes to the board rather than making
            somebody prove the password they just chose. */}
        <p className="text-body-secondary small">
          You are signed in with your new password.
        </p>
        <Button onClick={() => navigate('/')}>Go to the board</Button>
      </>,
    );
  }

  return shell(
    <>
      <h1 className="h4 mb-1">Choose a new password</h1>
      <p className="text-body-secondary small mb-4">
        Your link checked out. Set a password and you will be signed in.
      </p>

      {error && <Alert variant="danger">{error}</Alert>}

      <Form onSubmit={submit}>
        <Form.Group className="mb-3" controlId="new-password">
          <Form.Label>New password</Form.Label>
          <Form.Control
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
            autoFocus
          />
          <Form.Text>
            At least 8 characters, with an uppercase letter, a lowercase letter, a number and a
            symbol.
          </Form.Text>
        </Form.Group>

        <Form.Group className="mb-4" controlId="confirm-password">
          <Form.Label>Confirm new password</Form.Label>
          <Form.Control
            type="password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </Form.Group>

        <div className="d-grid">
          <Button type="submit" disabled={busy}>
            {busy && <Spinner as="span" size="sm" animation="border" className="me-2" />}
            Set new password
          </Button>
        </div>
      </Form>

      <div className="text-center mt-3">
        <Link to="/login" className="small">
          Back to sign in
        </Link>
      </div>
    </>,
  );
}
