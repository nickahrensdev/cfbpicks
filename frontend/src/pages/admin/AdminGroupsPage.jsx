import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Container, Form, InputGroup } from 'react-bootstrap';
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

  const [term, setTerm] = useState('');
  // all | leagues | personal
  const [kind, setKind] = useState('leagues');

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

  /**
   * Name or owner, plus a way to put the personal boards aside.
   *
   * <p>Filtered here rather than on the server: this endpoint already returns
   * every group in one go, so a round trip per keystroke would buy nothing.
   * If the site ever has enough groups for that to hurt, the endpoint needs
   * paging first and this becomes a query parameter.
   *
   * <p>The personal-board filter matters more than it looks. There is one per
   * account and they all share a name, so past a handful of members they are
   * most of this page - and they are the rows an admin almost never wants,
   * since there is nothing about them to administer.
   */
  const visible = useMemo(() => {
    const needle = term.trim().toLowerCase();

    return groups.filter((group) => {
      if (kind === 'leagues' && group.personal) return false;
      if (kind === 'personal' && !group.personal) return false;
      if (!needle) return true;

      // The handle without its "@" as well, so typing "nickahrens" finds a
      // group whose owner is shown as "@nickahrens".
      return group.name.toLowerCase().includes(needle)
        || (group.creatorName ?? '').toLowerCase().includes(needle);
    });
  }, [groups, term, kind]);

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
              {/* Says what is on screen against what exists, so a filtered
                  list is never mistaken for the whole site. */}
              Showing {visible.length} of {groups.length}. Creating one makes you its owner.
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

        {/* Defaults to leagues, not everything: every account has a personal
            board and they all share a name, so "all" is mostly rows nobody
            came here to look at. */}
        <InputGroup>
          <Form.Control
            type="search"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            placeholder="Search by name or owner"
            aria-label="Search groups by name or owner"
          />
          <Form.Select
            value={kind}
            onChange={(event) => setKind(event.target.value)}
            aria-label="Which groups to show"
            className="flex-grow-0 w-auto"
          >
            <option value="leagues">Leagues</option>
            <option value="personal">Personal boards</option>
            <option value="all">All</option>
          </Form.Select>
        </InputGroup>

        {groups.length === 0 ? (
          <EmptyState title="No groups yet">
            <p className="mb-0">Create the first one to start assigning members to it.</p>
          </EmptyState>
        ) : visible.length === 0 ? (
          // Distinct from having no groups at all - the difference is whether
          // there is anything to widen the search back out to.
          <EmptyState title="No groups match">
            <p className="mb-0">
              {groups.length} {groups.length === 1 ? 'group' : 'groups'} in total. Try a different
              search, or switch to All.
            </p>
          </EmptyState>
        ) : (
          // A list, not a table. Five columns did not fit a phone, so the
          // table scrolled sideways with the member count off the edge and
          // group names broken across three lines. The same row list the
          // member-facing page uses reads at any width, and the whole row is
          // the link, so the separate Manage button is not needed either.
          <div className="group-list">
            {visible.map((group) => (
              <Link
                key={group.id}
                to={`/admin/groups/${group.id}`}
                className="group-row"
              >
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <div className="d-flex align-items-center gap-2">
                    <span className="fw-semibold text-truncate">{group.name}</span>
                    {/* Under "All" these sit among the leagues and every one
                        of them is called the same thing, so the badge is what
                        tells them apart from a league someone named that. */}
                    {group.personal && (
                      <Badge
                        bg="info-subtle"
                        text="info-emphasis"
                        className="fw-normal flex-shrink-0"
                      >
                        personal
                      </Badge>
                    )}
                    {group.visibility === 'PRIVATE' && !group.personal && (
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
