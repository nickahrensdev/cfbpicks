import { useState } from 'react';
import { Alert, Button, Card, Container } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

import { api } from '../../api/client.js';
import GroupSettingsForm, {
  DEFAULT_SETTINGS,
  SETTINGS_STEPS,
  stepIssue,
} from '../../components/GroupSettingsForm.jsx';
import { BackButton } from '../../components/common.jsx';

/**
 * Creating a group, as its own page.
 *
 * <p>It was a panel that unfolded above the list of every existing group,
 * which meant a six-step form competed for the screen with the table it was
 * pushing down. A page has room for the stepper and nothing to scroll past
 * to reach it.
 *
 * <p>On success it goes to the new group's own page rather than back to the
 * list: a group with no members does nothing, so adding them is the next
 * thing to do, and landing there says the group was created more plainly
 * than a message on a list of many would.
 */
export default function AdminGroupCreatePage() {
  const navigate = useNavigate();

  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const lastStep = SETTINGS_STEPS.length - 1;
  const issue = stepIssue(settings, step);

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
    setError(null);
    try {
      const created = await api.adminCreateGroup(settings);
      navigate(`/admin/groups/${created.id}`, { replace: true });
    } catch (err) {
      // Field errors come from Bean Validation, the message from a
      // cross-field rule the settings cannot satisfy.
      setError(err.fieldErrors?.name ?? err.message);
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

  return (
    <Container className="py-4 py-md-5">
      <BackButton className="mb-3" fallback="/admin/groups" label="Back to groups" />

      <div className="mb-3">
        <h1 className="h4 mb-1">New group</h1>
        <p className="text-body-secondary small mb-0">
          You become the owner of whatever you create here.
        </p>
      </div>

      {error && (
        <Alert variant="danger" dismissible onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

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

            {/* The step counter used to sit here too. The stepper names the
                step and counts it directly above the fields now, so a second
                copy at the bottom of the same card said it twice. */}
            <Button
              type="button"
              variant="link"
              className="ms-auto text-body-secondary text-decoration-none"
              onClick={() => navigate('/admin/groups')}
            >
              Cancel
            </Button>
          </div>
        </form>
      </Card>
    </Container>
  );
}
