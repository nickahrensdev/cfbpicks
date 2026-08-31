import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Nav, Table } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { api } from '../api/client.js';
import GroupSettingsForm from '../components/GroupSettingsForm.jsx';
import ShareGroupButton from '../components/ShareGroupButton.jsx';
import { BackButton, ConfirmButton, EmptyState, ErrorNotice, handle, Loading, memberName } from '../components/common.jsx';
import { useProfile } from '../auth/ProfileProvider.jsx';
import { useGroup } from '../auth/GroupProvider.jsx';

/**
 * One group: its settings, its members, and the things an owner can do to it.
 *
 * <p>Members see the same settings form the owner does, read-only. Rules a
 * member is playing by should not be harder to read than the rules an owner is
 * setting, and a disabled form is the same layout with the same labels.
 */
export default function GroupDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { profile } = useProfile();
  const { refresh: refreshGroups } = useGroup();

  const [group, setGroup] = useState(null);
  const [members, setMembers] = useState([]);
  const [requests, setRequests] = useState([]);
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(null);
  const [tab, setTab] = useState('settings');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [detail, roster] = await Promise.all([api.group(id), api.groupMembers(id)]);
      setGroup(detail);
      setSettings(detail.settings);
      setMembers(roster);

      // Only owners can read the queue, so only owners ask for it.
      setRequests(detail.manageable ? await api.groupRequests(id).catch(() => []) : []);
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
      const detail = await api.updateGroup(id, settings);
      setGroup(detail);
      setSettings(detail.settings);
      // The group bar, the switcher and every page's scoping read the copy
      // GroupProvider holds, not this one - without this a rename shows here
      // and nowhere else until the next full reload.
      await refreshGroups();
      setNotice({ variant: 'success', text: 'Settings saved.' });
    } catch (err) {
      setNotice({
        variant: 'danger',
        text: err.fieldErrors?.name ?? err.message,
      });
    } finally {
      setBusy(null);
    }
  };

  const remove = async (userId) => {
    setBusy(userId);
    setNotice(null);
    try {
      await api.leaveGroup(id, userId);
      // Leaving means this page is no longer yours to see.
      if (userId === profile?.id) {
        navigate('/groups');
        return;
      }
      await load();
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusy(null);
    }
  };

  const decide = async (userId, approve) => {
    setBusy(userId);
    setNotice(null);
    try {
      await (approve ? api.approveGroupRequest(id, userId) : api.denyGroupRequest(id, userId));
      await load();
    } catch (err) {
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusy(null);
    }
  };

  const setRole = async (userId, role) => {
    setBusy(userId);
    setNotice(null);
    try {
      await api.setGroupMemberRole(id, userId, role);
      await load();
    } catch (err) {
      // The server refuses to leave a group with no owner.
      setNotice({ variant: 'danger', text: err.message });
    } finally {
      setBusy(null);
    }
  };

  const destroy = async () => {
    setBusy('delete');
    try {
      await api.deleteGroup(id);
      navigate('/groups');
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

  const canManage = group.manageable;

  return (
    <Container className="py-4 py-md-5">
      <div className="d-grid gap-3">
        <BackButton fallback="/groups" label="Back to groups" />

        <div className="d-flex justify-content-between align-items-start gap-3">
          <div>
            <h1 className="h3 mb-1">{settings.name}</h1>
            <p className="text-body-secondary mb-0 small">
              {group.creatorName && `Created by ${handle(group.creatorName)} · `}
              {group.memberCount}{' '}
              {group.memberCount === 1 ? 'member' : 'members'}
              {group.myRole && ` · you are ${group.myRole === 'OWNER' ? 'the owner' : 'a member'}`}
            </p>
          </div>
          <div className="d-flex align-items-start gap-2 flex-shrink-0">
            {/* The server decides whether this member may share - a private
                group needs its owner to have allowed it - so the button is
                simply absent rather than present and refused. */}
            {group.shareable && <ShareGroupButton groupId={id} />}
            {group.myRole === 'MEMBER' && (
              <ConfirmButton
                size="sm"
                variant="secondary"
                label="Leave group"
                confirmLabel="Click again to leave"
                onConfirm={() => remove(profile.id)}
                disabled={busy === profile?.id}
              />
            )}
          </div>
        </div>

        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}

        {/* Settings and roster are separate errands - you come here either to
            change how the league plays or to see who is in it - so they get a
            tab each rather than one long scroll. */}
        <Nav variant="tabs" activeKey={tab} onSelect={setTab}>
          <Nav.Item>
            <Nav.Link eventKey="settings">Settings</Nav.Link>
          </Nav.Item>
          <Nav.Item>
            <Nav.Link eventKey="members">Members ({group.memberCount})</Nav.Link>
          </Nav.Item>
          {/* Only owners can act on requests, so only they see the queue. */}
          {canManage && (
            <Nav.Item>
              <Nav.Link eventKey="requests">
                Requests{group.pendingRequests > 0 && ` (${group.pendingRequests})`}
              </Nav.Link>
            </Nav.Item>
          )}
        </Nav>

        {tab === 'settings' && (
          <form onSubmit={save}>
            <GroupSettingsForm value={settings} onChange={setSettings} disabled={!canManage} />
            {canManage && (
              <div className="mt-3">
                <Button type="submit" disabled={busy === 'save'}>
                  {busy === 'save' ? 'Saving…' : 'Save settings'}
                </Button>
              </div>
            )}
          </form>
        )}

        {tab === 'members' && (
          <Card>
            <Table hover responsive className="mb-0 align-middle">
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Joined</th>
                  {canManage && <th className="text-end">Actions</th>}
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
                        {/* Who made the group, which is independent of who
                            runs it now - a creator can be demoted. */}
                        {member.creator && (
                          <Badge bg="info" className="fw-normal">
                            creator
                          </Badge>
                        )}
                        {member.userId === profile?.id && (
                          <Badge
                            bg="secondary-subtle"
                            text="secondary-emphasis"
                            className="fw-normal"
                          >
                            you
                          </Badge>
                        )}
                      </span>
                    </td>
                    <td className="text-body-secondary small">
                      {new Date(member.joinedAt).toLocaleDateString()}
                    </td>
                    {canManage && (
                      <td className="text-end">
                        <div className="d-inline-flex gap-2">
                          <Button
                            size="sm"
                            variant="outline-secondary"
                            disabled={busy === member.userId}
                            onClick={() =>
                              setRole(member.userId, member.role === 'OWNER' ? 'MEMBER' : 'OWNER')
                            }
                          >
                            {member.role === 'OWNER' ? 'Make member' : 'Make owner'}
                          </Button>
                          <ConfirmButton
                            size="sm"
                            label="Remove"
                            confirmLabel="Click again to remove"
                            onConfirm={() => remove(member.userId)}
                            disabled={busy === member.userId}
                          />
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card>
        )}

        {tab === 'requests' && canManage && (
          <Card>
            {requests.length === 0 ? (
              <Card.Body>
                <EmptyState title="Nobody is waiting">
                  <p className="small mb-0">
                    {settings.requireApproval
                      ? 'People asking to join this group will appear here.'
                      : 'This group does not require approval, so joins happen straight away.'}
                  </p>
                </EmptyState>
              </Card.Body>
            ) : (
              <Table hover responsive className="mb-0 align-middle">
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Asked</th>
                    <th className="text-end">Decision</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.map((row) => (
                    <tr key={row.userId}>
                      <td>{memberName(row.displayName, row.username)}</td>
                      <td className="text-body-secondary small">
                        {new Date(row.requestedAt).toLocaleDateString()}
                      </td>
                      <td className="text-end">
                        <div className="d-inline-flex gap-2">
                          <Button
                            size="sm"
                            disabled={busy === row.userId}
                            onClick={() => decide(row.userId, true)}
                          >
                            Approve
                          </Button>
                          <Button
                            size="sm"
                            variant="outline-danger"
                            disabled={busy === row.userId}
                            onClick={() => decide(row.userId, false)}
                          >
                            Deny
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
        )}

        {canManage && tab === 'settings' && (
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
        )}
      </div>
    </Container>
  );
}
