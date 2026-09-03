import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, Container } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { api } from '../api/client.js';
import ShareGroupButton from '../components/ShareGroupButton.jsx';
import { useGroup } from '../auth/GroupProvider.jsx';
import { ChevronIcon, EmptyState, ErrorNotice, Loading } from '../components/common.jsx';

/** "Pick'em · Weekly · 4 members" - the line that tells two groups apart. */
function describe(group) {
  return [
    group.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em",
    group.cadence === 'DAILY' ? 'Daily' : 'Weekly',
    `${group.memberCount} ${group.memberCount === 1 ? 'member' : 'members'}`,
  ].join(' · ');
}

/**
 * The member's view of groups: the ones they are in, and the public ones they
 * could join.
 *
 * <p>Search and join share a page because they are the same errand - a member
 * arriving here either wants a group they already have or one they do not.
 */
export default function GroupsPage() {
  // Only to mark which row is the one every other page is showing. The list
  // is otherwise identical whichever group is selected, and without this the
  // bar above says "Current Group: X" while the list gives no sign which X is.
  const { groupId: currentGroupId } = useGroup();

  const [mine, setMine] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setMine(await api.myGroups());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <Container className="py-4">
        <Loading label="Loading groups" />
      </Container>
    );
  }

  return (
    <Container className="py-4 py-md-5">
      <div className="d-grid gap-4" style={{ gridTemplateColumns: 'minmax(0, 1fr)' }}>
        {/* The action sits beside the heading rather than after the list.
            It is how you get to the other half of this page's job, and at
            the bottom it was below however many groups you happen to be in.
            align-items-start so it lines up with the heading rather than
            centring against the two-line blurb beside it. */}
        <div className="d-flex justify-content-between align-items-start gap-3">
          <div style={{ minWidth: 0 }}>
            <h1 className="h4 mb-1">Groups</h1>
            <p className="text-body-secondary small mb-0">
              Each group is its own league, with its own rules and its own leaderboard.
            </p>
          </div>
          <Button
            as={Link}
            to="/groups/find"
            size="sm"
            variant="outline-primary"
            className="flex-shrink-0 text-nowrap"
          >
            Find a group
          </Button>
        </div>

        <ErrorNotice error={error} onRetry={load} />

        <section>
          <h2 className="h6 text-uppercase text-body-secondary mb-2">My groups</h2>
          {mine.length === 0 ? (
            <EmptyState title="You are not in a group yet">
              <p className="mb-3">Search the public ones, or ask an owner to add you.</p>
              <Button as={Link} to="/groups/find">
                Find a group
              </Button>
            </EmptyState>
          ) : (
            // One bordered list rather than a stack of separate cards. Five
            // groups filled a phone screen when each was its own card with
            // its own padding and gap; as rows they read as one list and fit.
            <div className="group-list">
              {mine.map((group) => (
                <Link
                  key={group.id}
                  to={`/groups/${group.id}`}
                  className={`group-row ${group.id === currentGroupId ? 'group-row--current' : ''}`}
                  aria-current={group.id === currentGroupId ? 'true' : undefined}
                >
                  {/* min-width 0 so a long group name ellipsizes instead of
                      pushing the actions off the row. */}
                  <div className="flex-grow-1" style={{ minWidth: 0 }}>
                    <div className="d-flex align-items-center gap-2">
                      <span className="fw-semibold text-truncate">{group.name}</span>
                      {group.myRole === 'OWNER' && (
                        // Subtle, not solid primary. It labels the row; it is
                        // not more important than the name it sits beside.
                        <Badge bg="secondary-subtle" text="secondary-emphasis" className="fw-normal">
                          owner
                        </Badge>
                      )}
                    </div>
                    <div className="small text-body-secondary text-truncate">
                      {group.id === currentGroupId && (
                        <span className="text-primary fw-semibold">Current · </span>
                      )}
                      {describe(group)}
                    </div>
                  </div>

                  <div className="d-flex align-items-center gap-2 flex-shrink-0">
                    {/* The whole row is a link, so the button has to stop the
                        click reaching it - sharing a group should not also
                        navigate away from the list. */}
                    {group.shareable && (
                      <span
                        onClick={(event) => {
                          event.preventDefault();
                          event.stopPropagation();
                        }}
                      >
                        <ShareGroupButton groupId={group.id} iconOnly />
                      </span>
                    )}
                    <span className="text-body-tertiary d-flex">
                      <ChevronIcon />
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>

      </div>
    </Container>
  );
}
