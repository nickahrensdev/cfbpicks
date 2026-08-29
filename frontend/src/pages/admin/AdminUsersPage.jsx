import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Form, Modal, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { EmptyState, ErrorNotice, Loading } from '../../components/common.jsx';
import { useProfile } from '../../auth/ProfileProvider.jsx';
import { api } from '../../api/client.js';

export default function AdminUsersPage() {
  const { profile } = useProfile();
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await api.adminUsers());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return users;
    return users.filter(
      (user) =>
        user.displayName.toLowerCase().includes(term) ||
        (user.email ?? '').toLowerCase().includes(term),
    );
  }, [users, search]);

  const setRole = async (user, role) => {
    setBusyId(user.id);
    setNotice(null);
    try {
      await api.adminSetRole(user.id, role);
      await load();
      setNotice({ variant: 'success', text: `${user.displayName} is now ${role.toLowerCase()}.` });
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusyId(null);
    }
  };

  const remove = async (user) => {
    setBusyId(user.id);
    setNotice(null);
    try {
      await api.adminDeleteUser(user.id);
      setConfirmDelete(null);
      await load();
      setNotice({ variant: 'success', text: `${user.displayName} was removed.` });
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Container className="py-4 py-md-5">
      <h1 className="h3 mb-4">Members</h1>

      <Form.Control
        type="search"
        placeholder="Search by name or email"
        className="mb-3"
        style={{ maxWidth: '22rem' }}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {notice && (
        <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
          {notice.text}
        </Alert>
      )}
      <ErrorNotice error={error} onRetry={load} />

      {loading ? (
        <Loading label="Loading members" />
      ) : visible.length === 0 ? (
        <EmptyState title="No members match that search" />
      ) : (
        <Card className="shadow-sm">
          <div className="table-responsive">
            <Table hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Member</th>
                  <th scope="col" className="d-none d-md-table-cell">Email</th>
                  <th scope="col" className="text-center">Picks</th>
                  <th scope="col">Role</th>
                  <th scope="col" className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((user) => {
                  const isMe = user.id === profile?.id;
                  return (
                    <tr key={user.id}>
                      <td>
                        <Link to={`/members/${user.id}`} className="text-decoration-none">
                          {user.displayName}
                        </Link>
                        {isMe && (
                          <Badge bg="primary-subtle" text="primary-emphasis" className="ms-2">
                            you
                          </Badge>
                        )}
                      </td>
                      <td className="d-none d-md-table-cell small text-body-secondary">
                        {user.email}
                      </td>
                      <td className="text-center">{user.totalPicks}</td>
                      <td>
                        <Badge bg={user.role === 'ADMIN' ? 'primary' : 'secondary-subtle'}
                               text={user.role === 'ADMIN' ? undefined : 'secondary-emphasis'}>
                          {user.role}
                        </Badge>
                      </td>
                      <td className="text-end">
                        <div className="d-inline-flex gap-2">
                          {/* Self-demotion is blocked server-side so the site
                              cannot end up with zero admins. */}
                          <Button
                            size="sm"
                            variant="outline-secondary"
                            disabled={isMe || busyId === user.id}
                            onClick={() =>
                              setRole(user, user.role === 'ADMIN' ? 'MEMBER' : 'ADMIN')
                            }
                          >
                            {user.role === 'ADMIN' ? 'Revoke admin' : 'Make admin'}
                          </Button>
                          <Button
                            size="sm"
                            variant="outline-danger"
                            disabled={isMe || busyId === user.id}
                            onClick={() => setConfirmDelete(user)}
                          >
                            Delete
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </div>
        </Card>
      )}

      <Modal show={Boolean(confirmDelete)} onHide={() => setConfirmDelete(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="h5">Delete member</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p className="mb-0">
            Delete <strong>{confirmDelete?.displayName}</strong>? This removes their{' '}
            {confirmDelete?.totalPicks ?? 0} pick
            {confirmDelete?.totalPicks === 1 ? '' : 's'} and their activity history. It cannot be
            undone.
          </p>
          <p className="small text-body-secondary mt-3 mb-0">
            Their Supabase login is not removed - delete that separately in the Supabase dashboard
            if you want to block them from signing back in.
          </p>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="light" onClick={() => setConfirmDelete(null)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            disabled={busyId === confirmDelete?.id}
            onClick={() => remove(confirmDelete)}
          >
            Delete member
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
}
