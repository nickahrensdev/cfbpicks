import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Form, InputGroup } from 'react-bootstrap';
import { Link, useSearchParams } from 'react-router-dom';

import { api } from '../api/client.js';
import {
  BackButton,
  EmptyState,
  ErrorNotice,
  handle,
  Loading,
  LockIcon,
  UnlockIcon,
} from '../components/common.jsx';

/**
 * Searching for a public group to join.
 *
 * <p>Split out of GroupsPage, which was doing two unrelated jobs on one
 * screen: the groups you are already in - a list you read constantly - and a
 * search you use once or twice ever. The search box, its results and the
 * password flow pushed the list you actually came for off the top of the
 * page.
 *
 * <p>The term lives in the URL so a search can be linked to and survives the
 * back button, which is the same reason the games board keeps its week there.
 */
export default function FindGroupPage() {
  const [params, setParams] = useSearchParams();
  const term = params.get('q') ?? '';

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
      setResults(await api.searchGroups({ q: term }));
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

  return (
    <Container className="py-4 py-md-5">
      <BackButton className="mb-3" fallback="/groups" label="Back to my groups" />

      <div className="mb-3">
        <h1 className="h4 mb-1">Find a group</h1>
        <p className="text-body-secondary small mb-0">
          Only public groups are listed. A private one is unlisted - its owner has to add you, or
          send you an invite link.
        </p>
      </div>

      {notice && (
        <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
          {notice.text}
        </Alert>
      )}
      <ErrorNotice error={error} onRetry={load} />

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
            autoFocus
          />
          <Button type="submit" variant="outline-secondary">
            Search
          </Button>
        </InputGroup>
      </Form>

      {loading ? (
        <Loading label="Searching groups" />
      ) : results.length === 0 ? (
        <EmptyState title="No public groups match">
          <p className="mb-0">Private groups are unlisted - an owner has to add you.</p>
        </EmptyState>
      ) : (
        <div className="d-grid gap-2">
          {results.map((group) => (
            <Card key={group.id}>
              <Card.Body className="p-3">
                <div className="d-flex justify-content-between align-items-start gap-3">
                  <div style={{ minWidth: 0 }}>
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
                      <p className="mb-1 small text-body-secondary">{group.description}</p>
                    )}
                    <div className="small text-body-tertiary">
                      {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}
                      {group.creatorName && ` · created by ${handle(group.creatorName)}`}
                    </div>
                  </div>

                  {group.alreadyMember ? (
                    <Button
                      as={Link}
                      to={`/groups/${group.id}`}
                      size="sm"
                      variant="outline-secondary"
                      className="flex-shrink-0"
                    >
                      Open
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      onClick={() => join(group)}
                      disabled={busyId === group.id}
                      className="flex-shrink-0"
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
                      <Button size="sm" variant="outline-secondary" onClick={() => setJoining(null)}>
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
    </Container>
  );
}
