import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, Container } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { api } from '../../api/client.js';
import { ChevronIcon, EmptyState, ErrorNotice, handle, Loading } from '../../components/common.jsx';

/** "Pick'em · Weekly · Continuous · 4 members" - the dropped table columns. */
function describe(group) {
  return [
    group.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em",
    group.cadence === 'DAILY' ? 'Daily' : 'Weekly',
    group.lengthType === 'PER_YEAR' ? 'Per year' : 'Continuous',
    `${group.memberCount} ${group.memberCount === 1 ? 'member' : 'members'}`,
  ].join(' · ');
}

/**
 * Every group on the site.
 *
 * <p>Creating one lives on its own page - see AdminGroupCreatePage. It is a
 * six-step form, and unfolding it above this list meant the list it pushed
 * down was still there to scroll past.
 */
export default function AdminGroupsPage() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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

  if (loading) {
    return (
      <Container className="py-4">
        <Loading label="Loading groups" />
      </Container>
    );
  }

  return (
    <Container className="py-4 py-md-5">
      <div className="d-grid gap-3" style={{ gridTemplateColumns: 'minmax(0, 1fr)' }}>
        {/* align-items-start so the button sits level with the heading
            rather than centring against the blurb; text-nowrap because
            "New group" was breaking across two lines on a phone. */}
        <div className="d-flex justify-content-between align-items-start gap-3">
          <div style={{ minWidth: 0 }}>
            <h1 className="h4 mb-1">Groups</h1>
            <p className="text-body-secondary mb-0 small">
              Every group on the site. Creating one makes you its owner.
            </p>
          </div>
          <Button
            as={Link}
            to="/admin/groups/new"
            size="sm"
            variant="primary"
            className="flex-shrink-0 text-nowrap"
          >
            New group
          </Button>
        </div>

        <ErrorNotice error={error} onRetry={load} />

        {groups.length === 0 ? (
          <EmptyState title="No groups yet">
            <p className="mb-0">Create the first one to start assigning members to it.</p>
          </EmptyState>
        ) : (
          // A list, not a table. Five columns did not fit a phone, so the
          // table scrolled sideways with the member count off the edge and
          // group names broken across three lines. The same row list the
          // member-facing page uses reads at any width, and the whole row is
          // the link, so the separate Manage button is not needed either.
          <div className="group-list">
            {groups.map((group) => (
              <Link
                key={group.id}
                to={`/admin/groups/${group.id}`}
                className="group-row"
              >
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <div className="d-flex align-items-center gap-2">
                    <span className="fw-semibold text-truncate">{group.name}</span>
                    {group.visibility === 'PRIVATE' && (
                      <Badge
                        bg="secondary-subtle"
                        text="secondary-emphasis"
                        className="fw-normal flex-shrink-0"
                      >
                        private
                      </Badge>
                    )}
                  </div>

                  {group.description && (
                    <div className="small text-body-secondary text-truncate">
                      {group.description}
                    </div>
                  )}

                  {/* Everything the four dropped columns carried, on one
                      line. Wraps rather than truncates - on a narrow screen
                      a second line is better than losing the member count,
                      which is the number this page exists to show. */}
                  <div className="small text-body-tertiary">
                    {describe(group)}
                    {group.creatorName && ` · ${handle(group.creatorName)}`}
                  </div>
                </div>

                <span className="text-body-tertiary d-flex flex-shrink-0">
                  <ChevronIcon />
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </Container>
  );
}
