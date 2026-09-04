import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Form, Table } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { api } from '../../api/client.js';
import GroupSettingsForm from '../../components/GroupSettingsForm.jsx';
import { BackButton, ConfirmButton, ErrorNotice, handle, Loading, memberName } from '../../components/common.jsx';
import { useGroup } from '../../auth/GroupProvider.jsx';

/**
 * Admin management of one group: its settings, and adding or removing members
 * without going through a password.
 */
export default function AdminGroupEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { refresh: refreshGroups } = useGroup();

  const [group, setGroup] = useState(null);
  const [settings, setSettings] = useState(null);
  const [members, setMembers] = useState([]);
  // Candidates for the add-member picker, fetched per search term rather than
  // held in full - see the search box below.
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [detail, roster] = await Promise.all([
        api.adminGroup(id),
        api.adminGroupMembers(id),
      ]);
      setGroup(detail);
      setSettings(detail.settings);
      setMembers(roster);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const save = async (event) => {
    event.preventDefault();
    setBusy('save');
    setNotice(null);
    try {
      const detail = await api.adminUpdateGroup(id, settings);
      setGroup(detail);
      setSettings(detail.settings);
      // An admin editing a group they are also in has a group bar and a
      // switcher showing the old name until GroupProvider reloads.
      await refreshGroups();
      setNotice({ variant: 'success', text: 'Settings saved.' });
    } catch (err) {
      setNotice({ variant: 'danger', text: err.fieldErrors?.name ?? err.message });
    } finally {
      setBusy(null);
    }
  };

  const removeMember = async (userId) => {
    setBusy(userId);
    setNotice(null);
    try {
      await api.adminRemoveGroupMember(id, userId);
      await load();
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusy(null);
    }
  };

  const destroy = async () => {
    setBusy('delete');
    try {
      await api.adminDeleteGroup(id);
      navigate('/admin/groups');
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
      setBusy(null);
    }
  };

  if (loading) {
    return (
      <Container className="py-4">
        <Loading label="Loading group" />
      </Container>
    );
  }
  if (error) {
    return (
      <Container className="py-4">
        <ErrorNotice error={error} onRetry={load} />
      </Container>
    );
  }
  if (!group) return null;

  return (
    <Container className="py-4 py-md-5">
      {/* minmax(0, 1fr): grid items take their content as a minimum width,
          so the settings tab row - which scrolls sideways - would otherwise
          set this column's width and push the page past the viewport. */}
      <div className="d-grid gap-3" style={{ gridTemplateColumns: 'minmax(0, 1fr)' }}>
        <BackButton fallback="/admin/groups" label="Back to groups" />

        <div>
          <h1 className="h3 mb-1">{settings.name}</h1>
          <p className="text-body-secondary mb-0 small">
            {group.creatorName && `Created by ${handle(group.creatorName)} · `}
            {group.memberCount}{' '}
            {group.memberCount === 1 ? 'member' : 'members'}
          </p>
        </div>

        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}

        <form onSubmit={save}>
          <GroupSettingsForm value={settings} onChange={setSettings} />
          <div className="mt-3">
            <Button type="submit" disabled={busy === 'save'}>
              {busy === 'save' ? 'Saving…' : 'Save settings'}
            </Button>
          </div>
        </form>

        <Card>
          <Card.Header className="fw-semibold">Members</Card.Header>
          <Card.Body className="pb-0">
            {/* The member picker was here. People join a group
                themselves now - by search, or through an invite link - so
                there is no way to put somebody into one they did not choose.
                Removing a member is still an owner's call. */}
          </Card.Body>
          <Table hover responsive className="mb-0 align-middle">
            <thead>
              <tr>
                <th>Member</th>
                <th>Email</th>
                <th>Joined</th>
                <th className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.userId}>
                  <td>
                    <span className="d-flex align-items-center gap-2">
                      {memberName(member.displayName, member.username)}
                      {member.role === 'OWNER' && (
                        <Badge bg="primary" className="fw-normal">
                          owner
                        </Badge>
                      )}
                      {member.creator && (
                        <Badge bg="info" className="fw-normal">
                          creator
                        </Badge>
                      )}
                    </span>
                  </td>
                  <td className="small text-body-secondary">{member.email}</td>
                  <td className="small text-body-secondary">
                    {new Date(member.joinedAt).toLocaleDateString()}
                  </td>
                  <td className="text-end">
                    {/* The server refuses to remove the last owner, so this
                        stays enabled and surfaces the message if tried. */}
                    <ConfirmButton
                      size="sm"
                      label="Remove"
                      confirmLabel="Click again to remove"
                      onConfirm={() => removeMember(member.userId)}
                      disabled={busy === member.userId}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>

        <Card border="danger">
          <Card.Body className="d-flex justify-content-between align-items-center gap-3 flex-wrap">
            <div>
              <p className="fw-semibold mb-1">Delete this group</p>
              <p className="text-body-secondary small mb-0">
                Everything in the group goes with it - every member&apos;s picks, the standings, the
                whole history. This cannot be undone.
              </p>
            </div>
            <ConfirmButton
              label="Delete group"
              confirmLabel="Click again — all picks will be lost"
              onConfirm={destroy}
              disabled={busy === 'delete'}
            />
          </Card.Body>
        </Card>
      </div>
    </Container>
  );
}
