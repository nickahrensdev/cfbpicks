import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { Navigate, useNavigate, useParams } from 'react-router-dom';

import { api } from '../api/client.js';
import { useAuth } from '../auth/AuthProvider.jsx';
import { useGroup } from '../auth/GroupProvider.jsx';
import { ErrorNotice, Loading, handle } from '../components/common.jsx';

/**
 * The landing page for a share link.
 *
 * <p>Four outcomes, decided in this order:
 *
 * <ol>
 *   <li>Not signed in — show what the invitation is for, then send them to
 *       sign in or sign up carrying the token, so they come back here.</li>
 *   <li>Signed in and already a member — the link is just a way back to the
 *       board, so select the group and go.</li>
 *   <li>Signed in, not a member — join, asking for a password if the group
 *       needs one.</li>
 *   <li>Waiting on approval — say so, since there is nothing more to do.</li>
 * </ol>
 *
 * <p>Step one shows the invitation <em>before</em> asking for an account:
 * someone deciding whether to sign up needs to know what they are being asked
 * to join, and a bare login screen tells them nothing.
 */
export default function JoinByLinkPage() {
  const { token } = useParams();
  const navigate = useNavigate();
  const { session } = useAuth();
  const { selectGroup, refresh: refreshGroups } = useGroup();

  const [invite, setInvite] = useState(null);
  const [claim, setClaim] = useState(null);
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // The preview needs no account; the claim does, and is what credits
      // whoever shared the link.
      setInvite(await api.shareInvite(token));
      setClaim(session ? await api.claimShare(token) : null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [session, token]);

  useEffect(() => {
    load();
  }, [load]);

  // Selecting a group is a side effect, so it belongs in an effect rather than
  // in the render that decides to redirect - React may render a component more
  // than once before committing, and this must happen exactly once.
  const alreadyMember = claim?.alreadyMember;

  useEffect(() => {
    if (alreadyMember && invite) selectGroup(invite.groupId);
  }, [alreadyMember, invite, selectGroup]);

  const join = async (event) => {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const result = await api.joinByShare(token, password.trim() || undefined);
      await refreshGroups();

      if (result.pending) {
        setClaim((current) => ({ ...current, pending: true }));
        return;
      }
      selectGroup(invite.groupId);
      navigate('/leaderboard');
    } catch (err) {
      setNotice(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <Loading label="Opening invite" />;

  if (error) {
    return (
      <Container className="py-4 py-md-5">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }

  // Already in: the link is a way back to the board, not an invitation.
  if (alreadyMember) {
    return <Navigate to="/leaderboard" replace />;
  }

  return (
    <Container className="py-4 py-md-5">
      <Row className="justify-content-center">
        <Col md={8} lg={6}>
          <Card className="shadow-sm">
            <Card.Body className="p-4">
              <p className="text-body-secondary small mb-1">
                {invite.sharerName
                  ? `${handle(invite.sharerName)} invited you to`
                  : 'You have been invited to'}
              </p>
              <h1 className="h4 mb-2">{invite.name}</h1>
              {invite.description && (
                <p className="text-body-secondary">{invite.description}</p>
              )}
              <p className="small text-body-secondary">
                {invite.memberCount} {invite.memberCount === 1 ? 'member' : 'members'}
                {invite.passwordRequired && ' · password required'}
                {invite.requiresApproval && ' · an owner approves new members'}
              </p>

              {notice && <Alert variant="danger" className="py-2 small">{notice}</Alert>}

              {claim?.pending ? (
                <Alert variant="info" className="mb-0">
                  Your request is with the group&apos;s owners. You will see the group once one of
                  them approves it.
                </Alert>
              ) : !session ? (
                <>
                  {/* The token rides along so signing in or signing up comes
                      back here rather than dropping the invitation. */}
                  <div className="d-grid gap-2">
                    <Button onClick={() => navigate(`/login?join=${token}`)}>
                      Sign in to join
                    </Button>
                    <Button
                      variant="outline-secondary"
                      onClick={() => navigate(`/login?join=${token}&mode=signup`)}
                    >
                      Create an account
                    </Button>
                  </div>
                </>
              ) : (
                <Form onSubmit={join}>
                  {invite.passwordRequired && (
                    <Form.Group className="mb-3" controlId="join-password">
                      <Form.Label>Group password</Form.Label>
                      <Form.Control
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoComplete="off"
                        required
                      />
                    </Form.Group>
                  )}
                  <div className="d-grid">
                    <Button type="submit" disabled={busy}>
                      {busy && (
                        <Spinner as="span" size="sm" animation="border" className="me-2" />
                      )}
                      {invite.requiresApproval ? 'Ask to join' : 'Join group'}
                    </Button>
                  </div>
                </Form>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}
