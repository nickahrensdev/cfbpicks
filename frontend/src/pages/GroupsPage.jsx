import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Form, InputGroup } from 'react-bootstrap';
import { Link, useSearchParams } from 'react-router-dom';

import { api } from '../api/client.js';
import ShareGroupButton from '../components/ShareGroupButton.jsx';
import { EmptyState, ErrorNotice, handle, Loading, LockIcon, UnlockIcon } from '../components/common.jsx';

/**
 * The member's view of groups: the ones they are in, and the public ones they
 * could join.
 *
 * <p>Search and join share a page because they are the same errand - a member
 * arriving here either wants a group they already have or one they do not.
 */
export default function GroupsPage() {
  const [params, setParams] = useSearchParams();
  const term = params.get('q') ?? '';

  const [mine, setMine] = useState([]);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  // Which group's password box is open, and what has been typed into it.
  const [joining, setJoining] = useState(null);
  const [password, setPassword] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [groups, found] = await Promise.all([api.myGroups(), api.searchGroups({ q: term })]);
      setMine(groups);
      setResults(found);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [term]);

  useEffect(() => {
    load();
  }, [load]);

  const join = async (group) => {
    // An open group needs no password, so the first click joins outright; a
    // locked one opens the box and the second click submits it.
    if (group.passwordRequired && joining !== group.id) {
      setJoining(group.id);
      setPassword('');
      return;
    }

    setBusyId(group.id);
    setNotice(null);
    try {
      const result = await api.joinGroup(group.id, group.passwordRequired ? password : null);
      setJoining(null);
      setPassword('');

      // A group that vets its members hands back a pending request rather
      // than a membership, so the message has to say which happened.
      setNotice(
        result?.pending
          ? {
              variant: 'info',
              text: `Your request to join ${group.name} is waiting for an owner to approve it.`,
            }
          : { variant: 'success', text: `You joined ${group.name}.` },
      );
      await load();
    } catch (err) {
      const text =
        err.code === 'GROUP_PASSWORD_INCORRECT' || err.code === 'GROUP_PASSWORD_REQUIRED'
          ? err.message
          : `Could not join: ${err.message}`;
      setNotice({ variant: 'danger', text });
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <Container className="py-4">
        <Loading label="Loading groups" />
      </Container>
    );
  }

  return (
    <Container className="py-4 py-md-5">
      <div className="d-grid gap-4">
        <div>
          <h1 className="h3 mb-1">Groups</h1>
          <p className="text-body-secondary mb-0">
            Each group is its own league, with its own rules and its own leaderboard.
          </p>
        </div>

        <ErrorNotice error={error} onRetry={load} />
        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}

        <section>
          <h2 className="h6 text-uppercase text-body-secondary mb-2">My groups</h2>
          {mine.length === 0 ? (
            <EmptyState title="You are not in a group yet">
              <p className="mb-0">Find a public one below, or ask an owner to add you.</p>
            </EmptyState>
          ) : (
            <div className="d-grid gap-2">
              {mine.map((group) => (
                <Card key={group.id} as={Link} to={`/groups/${group.id}`} className="text-decoration-none">
                  <Card.Body className="d-flex justify-content-between align-items-center gap-3">
                    <div>
                      <div className="fw-semibold d-flex align-items-center gap-2">
                        {group.name}
                        {group.myRole === 'OWNER' && (
                          <Badge bg="primary" className="fw-normal">
                            owner
                          </Badge>
                        )}
                      </div>
                      <div className="small text-body-secondary">
                        {group.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em"} ·{' '}
                        {group.cadence === 'DAILY' ? 'Daily' : 'Weekly'} · {group.memberCount}{' '}
                        {group.memberCount === 1 ? 'member' : 'members'}
                      </div>
                    </div>
                    <div className="d-flex align-items-center gap-3 flex-shrink-0">
                      {/* The whole card is a link, so the button has to stop
                          the click reaching it - sharing a group should not
                          also navigate away from the list. */}
                      {group.shareable && (
                        <div
                          onClick={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                          }}
                        >
                          <ShareGroupButton groupId={group.id} />
                        </div>
                      )}
                      <span aria-hidden="true" className="text-body-secondary">
                        →
                      </span>
                    </div>
                  </Card.Body>
                </Card>
              ))}
            </div>
          )}
        </section>

        <section>
          <h2 className="h6 text-uppercase text-body-secondary mb-2">Find a group</h2>

          <Form
            className="mb-3"
            onSubmit={(event) => {
              event.preventDefault();
              load();
            }}
          >
            <InputGroup>
              <Form.Control
                value={term}
                onChange={(event) => setParams(event.target.value ? { q: event.target.value } : {})}
                placeholder="Search public groups by name"
                aria-label="Search public groups"
              />
              <Button type="submit" variant="outline-secondary">
                Search
              </Button>
            </InputGroup>
          </Form>

          {results.length === 0 ? (
            <EmptyState title="No public groups match">
              <p className="mb-0">Private groups are unlisted - an owner has to add you.</p>
            </EmptyState>
          ) : (
            <div className="d-grid gap-2">
              {results.map((group) => (
                <Card key={group.id}>
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-start gap-3">
                      <div>
                        <div className="fw-semibold d-flex align-items-center gap-2">
                          {group.name}
                          <Badge
                            bg="secondary-subtle"
                            text="secondary-emphasis"
                            title={group.passwordRequired ? 'Password required' : 'Open to anyone'}
                          >
                            {group.passwordRequired ? <LockIcon /> : <UnlockIcon />}
                          </Badge>
                        </div>
                        {group.description && (
                          <p className="mb-1 small">{group.description}</p>
                        )}
                        <div className="small text-body-secondary">
                          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}
                          {group.creatorName && ` · created by ${handle(group.creatorName)}`}
                        </div>
                      </div>

                      {group.alreadyMember ? (
                        <Button as={Link} to={`/groups/${group.id}`} size="sm" variant="outline-secondary">
                          Open
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          onClick={() => join(group)}
                          disabled={busyId === group.id}
                        >
                          {busyId === group.id ? 'Joining…' : 'Join'}
                        </Button>
                      )}
                    </div>

                    {joining === group.id && (
                      <Form
                        className="mt-3"
                        onSubmit={(event) => {
                          event.preventDefault();
                          join(group);
                        }}
                      >
                        <InputGroup size="sm">
                          <Form.Control
                            type="password"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            placeholder="Group password"
                            aria-label={`Password for ${group.name}`}
                            autoFocus
                          />
                          <Button type="submit" size="sm" disabled={busyId === group.id}>
                            Join
                          </Button>
                          <Button
                            size="sm"
                            variant="outline-secondary"
                            onClick={() => setJoining(null)}
                          >
                            Cancel
                          </Button>
                        </InputGroup>
                      </Form>
                    )}
                  </Card.Body>
                </Card>
              ))}
            </div>
          )}
        </section>
      </div>
    </Container>
  );
}
