import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Row } from 'react-bootstrap';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';

import { supabase } from '../lib/supabase.js';
import { useAuth } from '../auth/AuthProvider.jsx';
import { Loading } from '../components/common.jsx';

/**
 * Where a confirmation email lands.
 *
 * <p>Supabase's own {@code /auth/v1/verify} link decides where to send people
 * afterwards, and that decision goes through Site URL and the redirect
 * allow-list. When it falls back it uses only the <em>origin</em> of Site URL,
 * which drops the base path this app is served under and lands everyone on a
 * GitHub 404.
 *
 * <p>So the email points here instead, carrying the token hash, and the
 * session is established client-side. The destination is then a plain link in
 * the template rather than something Supabase resolves - there is no fallback
 * left to get wrong.
 *
 * <p>The template that feeds this:
 *
 * <pre>
 *   &lt;a href="{{ .SiteURL }}confirm?token_hash={{ .TokenHash }}&amp;type=email"&gt;
 * </pre>
 */
export default function ConfirmEmailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { session } = useAuth();

  const tokenHash = params.get('token_hash');
  // Supabase sends the type it used; forwarded rather than assumed, so the
  // same route serves signup, email-change and recovery links.
  const type = params.get('type') ?? 'email';
  // Someone confirming after an invitation goes back to it, not to the board.
  const joinToken = params.get('join');

  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);

  const confirm = useCallback(async () => {
    if (!tokenHash) {
      setError('That link is missing its confirmation code. Try the newest email.');
      return;
    }
    const { error: verifyError } = await supabase.auth.verifyOtp({
      token_hash: tokenHash,
      type,
    });

    if (verifyError) {
      // Expired, already used, or from a different project. All read the same
      // to somebody holding an old email, so the message says what to do.
      setError(verifyError.message);
      return;
    }
    setDone(true);
  }, [tokenHash, type]);

  useEffect(() => {
    confirm();
  }, [confirm]);

  // verifyOtp signs them in, so the session arrives here rather than needing
  // a second step. Straight on to whatever they came for.
  if (done && session) {
    return <Navigate to={joinToken ? `/join/${joinToken}` : '/'} replace />;
  }

  if (error) {
    return (
      <Container className="py-4 py-md-5">
        <Row className="justify-content-center">
          <Col md={8} lg={6}>
            <Card className="shadow-sm">
              <Card.Body className="p-4">
                <h1 className="h4 mb-3">That link did not work</h1>
                <Alert variant="danger" className="small">
                  {error}
                </Alert>
                <p className="text-body-secondary small">
                  Confirmation links can only be used once, and they expire. Signing in again
                  will send a new one.
                </p>
                <Button onClick={() => navigate('/login')}>Go to sign in</Button>
              </Card.Body>
            </Card>
          </Col>
        </Row>
      </Container>
    );
  }

  return <Loading label="Confirming your email" />;
}
