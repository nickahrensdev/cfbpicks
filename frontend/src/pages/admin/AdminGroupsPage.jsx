import { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { api } from '../../api/client.js';
import GroupSettingsForm, {
  DEFAULT_SETTINGS,
  SETTINGS_STEPS,
  stepIssue,
} from '../../components/GroupSettingsForm.jsx';
import { EmptyState, ErrorNotice, handle, Loading } from '../../components/common.jsx';

/**
 * Every group on the site, and the form that creates one.
 *
 * <p>Creation is admin-only for now, so it lives here rather than on the
 * member-facing groups page. The creator becomes the group's owner.
 */
export default function AdminGroupsPage() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const [creating, setCreating] = useState(false);
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);

  const lastStep = SETTINGS_STEPS.length - 1;
  const issue = stepIssue(settings, step);

  /** Back to a blank first step, so the next group does not inherit this one. */
  const resetForm = () => {
    setSettings(DEFAULT_SETTINGS);
    setStep(0);
  };

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setGroups(await api.adminGroups());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const goNext = () => {
    if (!issue && step < lastStep) setStep(step + 1);
  };

  /**
   * Creates the group. Called explicitly rather than by form submission -
   * see the note on the footer buttons.
   */
  const submitCreate = async () => {
    if (issue || busy) return;

    setBusy(true);
    setNotice(null);
    try {
      const created = await api.adminCreateGroup(settings);
      setCreating(false);
      resetForm();
      setNotice({ variant: 'success', text: `Created ${created.settings.name}.` });
      await load();
    } catch (err) {
      // Field errors come from Bean Validation, the message from a
      // cross-field rule the settings cannot satisfy.
      setNotice({ variant: 'danger', text: err.fieldErrors?.name ?? err.message });
    } finally {
      setBusy(false);
    }
  };

  /**
   * Only reached by pressing Enter in a field. A stepper should treat that as
   * "next", not "done" - creating the group from step one because someone hit
   * Enter after typing the name would be a rotten surprise.
   */
  const handleSubmit = (event) => {
    event.preventDefault();
    if (step < lastStep) {
      goNext();
    } else {
      submitCreate();
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
      <div className="d-grid gap-3">
        <div className="d-flex justify-content-between align-items-center gap-3">
          <div>
            <h1 className="h3 mb-1">Groups</h1>
            <p className="text-body-secondary mb-0 small">
              Every group on the site. Creating one makes you its owner.
            </p>
          </div>
          <Button
            variant={creating ? 'outline-secondary' : 'primary'}
            onClick={() => {
              // Opening always starts at step one with fresh defaults.
              setCreating((open) => !open);
              resetForm();
            }}
          >
            {creating ? 'Cancel' : 'New group'}
          </Button>
        </div>

        <ErrorNotice error={error} onRetry={load} />
        {notice && (
          <Alert variant={notice.variant} dismissible onClose={() => setNotice(null)}>
            {notice.text}
          </Alert>
        )}

        {creating && (
          <Card body>
            <form onSubmit={handleSubmit}>
              <GroupSettingsForm
                value={settings}
                onChange={setSettings}
                creating
                mode="stepper"
                step={step}
                onStepChange={setStep}
              />

              {/* Only what this step can see. Everything else is the server's
                  call, reported after Create. */}
              {issue && (
                <p className="text-danger small mt-2 mb-0" role="alert">
                  {issue}
                </p>
              )}

              <div className="mt-3 d-flex flex-wrap gap-2 align-items-center">
                <Button
                  type="button"
                  variant="outline-secondary"
                  disabled={step === 0}
                  onClick={() => setStep(step - 1)}
                >
                  Back
                </Button>

                {/* Both are type="button", and both carry a key.
                    These two render in the same slot, so React would otherwise
                    reuse one DOM node between them and merely swap its props.
                    When Next became Create that meant flipping a live button's
                    type to "submit" mid-click: the handler advanced the step,
                    React flushed, and the browser then ran the click's default
                    action against a button that had turned into a submit -
                    creating the group from the Scoring step. Keys stop the node
                    being shared; type="button" means there is nothing to
                    accidentally submit even if it were. */}
                {step < lastStep ? (
                  <Button key="next" type="button" disabled={Boolean(issue)} onClick={goNext}>
                    Next
                  </Button>
                ) : (
                  <Button
                    key="create"
                    type="button"
                    disabled={busy || Boolean(issue)}
                    onClick={submitCreate}
                  >
                    {busy ? 'Creating…' : 'Create group'}
                  </Button>
                )}

                <Button
                  type="button"
                  variant="link"
                  className="text-body-secondary text-decoration-none"
                  onClick={() => {
                    setCreating(false);
                    resetForm();
                  }}
                >
                  Cancel
                </Button>

                <span className="ms-auto small text-body-secondary">
                  Step {step + 1} of {SETTINGS_STEPS.length}
                </span>
              </div>
            </form>
          </Card>
        )}

        {groups.length === 0 ? (
          <EmptyState title="No groups yet">
            <p className="mb-0">Create the first one to start assigning members to it.</p>
          </EmptyState>
        ) : (
          <Card>
            <Table hover responsive className="mb-0 align-middle">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Creator</th>
                  <th className="text-end">Members</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => (
                  <tr key={group.id}>
                    <td>
                      <span className="d-flex align-items-center gap-2">
                        <Link to={`/admin/groups/${group.id}`} className="fw-semibold">
                          {group.name}
                        </Link>
                        {group.visibility === 'PRIVATE' && (
                          <Badge bg="secondary-subtle" text="secondary-emphasis" className="fw-normal">
                            private
                          </Badge>
                        )}
                      </span>
                      {group.description && (
                        <div className="small text-body-secondary">{group.description}</div>
                      )}
                    </td>
                    <td className="small">
                      {group.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em"}
                      <div className="text-body-secondary">
                        {group.cadence === 'DAILY' ? 'Daily' : 'Weekly'} ·{' '}
                        {group.lengthType === 'PER_YEAR' ? 'Per year' : 'Continuous'}
                      </div>
                    </td>
                    <td className="small">{group.creatorName ? handle(group.creatorName) : "—"}</td>
                    <td className="text-end">{group.memberCount}</td>
                    <td className="text-end">
                      <Button
                        as={Link}
                        to={`/admin/groups/${group.id}`}
                        size="sm"
                        variant="outline-secondary"
                      >
                        Manage
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card>
        )}
      </div>
    </Container>
  );
}
