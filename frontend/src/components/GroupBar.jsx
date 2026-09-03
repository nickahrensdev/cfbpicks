import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { Badge, Button, Container, Form, InputGroup, Modal } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { api } from '../api/client.js';
import { useGroup } from '../auth/GroupProvider.jsx';
import { useStickyOffsets } from '../lib/stickyOffsets.js';
import { Loading, handle } from './common.jsx';

/** Below this many groups the list is short enough to read, so no search box. */
const SEARCH_FROM = 6;

const MARKETS = [
  ['moneyline', 'Moneyline'],
  ['spread', 'Spread'],
  ['total', 'Over/Under'],
];

/** Trims trailing zeroes so 1.00 reads as 1 and 0.50 as 0.5. */
const points = (value) => Number(value).toString();

/**
 * Enough to tell two groups of the same name apart. The creator comes last
 * because it is the most distinguishing and the least likely to be truncated
 * away on a narrow screen when it is the only thing that differs.
 */
function describe(entry) {
  const parts = [
    entry.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em",
    entry.cadence === 'DAILY' ? 'Daily' : 'Weekly',
    `${entry.memberCount} ${entry.memberCount === 1 ? 'member' : 'members'}`,
  ];
  if (entry.creatorName) {
    parts.push(`by ${handle(entry.creatorName)}`);
  }
  return parts.join(' · ');
}

/** Filled when this group is pinned, outline when it is not. */
function Star({ filled }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={filled ? 0 : 1.5}
      aria-hidden="true"
      focusable="false"
    >
      <path d="M8 1.6l1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.4l-3.8 2 .7-4.3-3.1-3 4.3-.6z" />
    </svg>
  );
}

/** Two-way arrows: swap this group for another. */
function SwapIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M2 5h10M9.5 2.5 12 5 9.5 7.5" />
      <path d="M14 11H4M6.5 8.5 4 11l2.5 2.5" />
    </svg>
  );
}

/** One labelled fact in the information modal. */
function Fact({ label, children }) {
  return (
    <div className="d-flex justify-content-between gap-3 py-1 border-bottom">
      <span className="text-body-secondary">{label}</span>
      <span className="text-end fw-semibold">{children}</span>
    </div>
  );
}

/**
 * Which league every board on the page is showing, pinned under the navbar.
 *
 * <p>It lives here rather than in the nav because it is not navigation - it is
 * the context everything else on the page is rendered in, and a member who
 * forgets which group they are looking at will misread every number on the
 * screen. Being always visible is the point.
 *
 * <p>Two separate actions, so two separate targets: the name tells you about
 * the group you are in, the icon swaps it for another. Rolling both into one
 * click meant guessing which the member wanted.
 */
export default function GroupBar() {
  const { groups, groupId, group, selectGroup, refresh } = useGroup();

  // Measured here rather than in App: this is the component that appears and
  // disappears with the selected group, and App does not consume the group
  // context, so it never re-renders when the bar shows up. Measuring there
  // left --group-bar-height at 0 and parked the picks bar behind this one.
  useStickyOffsets();

  const [picking, setPicking] = useState(false);
  const [info, setInfo] = useState(false);
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState(null);

  // The bar only has the summary; the full settings are fetched when someone
  // actually asks to see them, rather than on every page load.
  const [detail, setDetail] = useState(null);

  const loadDetail = useCallback(async () => {
    if (!groupId) return;
    setDetail(null);
    try {
      setDetail(await api.group(groupId));
    } catch {
      // The modal falls back to what the summary already knows.
    }
  }, [groupId]);

  useEffect(() => {
    if (info) loadDetail();
  }, [info, loadDetail]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    const matched = term
      ? groups.filter((entry) => entry.name.toLowerCase().includes(term))
      : groups;

    // The group you are in, then favourites, then the rest alphabetically.
    // Current goes first because it is the fixed point you read the list
    // against - "which am I in, and what else is there".
    return [...matched].sort((a, b) => {
      if ((a.id === groupId) !== (b.id === groupId)) return a.id === groupId ? -1 : 1;
      if (a.favorite !== b.favorite) return a.favorite ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  }, [groups, groupId, search]);

  // After the hooks, never before: an early return above one would change how
  // many hooks run between renders.
  if (!group) return null;

  const settings = detail?.settings ?? null;

  // The row the "Other groups" label goes above. Null when the only match is
  // the current group, so the label never appears with nothing under it.
  const firstOtherId = visible.find((entry) => entry.id !== groupId)?.id ?? null;

  const choose = (id) => {
    selectGroup(id);
    setPicking(false);
    setSearch('');
  };

  const toggleFavorite = async (entry) => {
    setBusyId(entry.id);
    try {
      await api.favoriteGroup(entry.id, !entry.favorite);
      await refresh();
    } catch {
      // A failed pin is not worth interrupting anyone over; the star just
      // stays as it was.
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <div className="group-bar border-bottom" data-sticky="group">
        <Container className="py-2 d-flex justify-content-between align-items-center gap-2">
          {/* The name on its own read as a heading rather than as a
              selection. Naming what it is above it says "this is the one you
              are in", which is the whole point of the bar. */}
          <Button
            variant="link"
            className="p-0 text-body text-decoration-none text-start"
            style={{ minWidth: 0 }}
            onClick={() => setInfo(true)}
            aria-haspopup="dialog"
            aria-label={`About ${group.name}`}
          >
            <span className="d-block group-subline text-body-secondary fw-normal">
              Current Group
            </span>
            <span className="d-block fw-semibold text-truncate">{group.name}</span>
          </Button>

          {/* The swap arrows alone were guessable at best - the word says what
              the icon only hints at, and gives the control a bigger target. */}
          <Button
            variant="link"
            className="p-0 text-body-secondary flex-shrink-0 d-flex align-items-center gap-1"
            onClick={() => setPicking(true)}
            aria-haspopup="dialog"
            aria-label="Change group"
            title="Change group"
          >
            <span className="small">Change</span>
            <SwapIcon />
          </Button>
        </Container>
      </div>

      {/* ------------------------------------------------ group information */}
      <Modal show={info} onHide={() => setInfo(false)} centered scrollable>
        <Modal.Header closeButton>
          <Modal.Title className="h5">{group.name}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {group.description && <p>{group.description}</p>}

          <div className="d-flex flex-wrap gap-2 mb-3">
            <Badge bg="secondary-subtle" text="secondary-emphasis">
              {group.groupType === 'ELIMINATION' ? 'Elimination' : "Pick'em"}
            </Badge>
            <Badge bg="secondary-subtle" text="secondary-emphasis">
              {group.cadence === 'DAILY' ? 'Daily' : 'Weekly'}
            </Badge>
            <Badge bg="secondary-subtle" text="secondary-emphasis">
              {group.lengthType === 'PER_YEAR' ? 'Per year' : 'Continuous'}
            </Badge>
            <Badge bg="secondary-subtle" text="secondary-emphasis">
              {group.visibility === 'PRIVATE' ? 'Private' : 'Public'}
            </Badge>
          </div>

          <div className="small mb-3">
            <Fact label="Members">{group.memberCount}</Fact>
            {group.creatorName && <Fact label="Created by">{handle(group.creatorName)}</Fact>}
            <Fact label="First season">{group.startSeason}</Fact>
            {group.myRole && (
              <Fact label="You are">{group.myRole === 'OWNER' ? 'an owner' : 'a member'}</Fact>
            )}
          </div>

          {detail === null ? (
            <Loading label="Loading group settings" />
          ) : (
            settings && (
              <>
                <h3 className="h6 text-uppercase text-body-secondary mb-2">Scoring</h3>
                <div className="small mb-3">
                  {MARKETS.filter(([key]) => settings[`${key}Enabled`]).map(([key, label]) => (
                    <Fact key={key} label={label}>
                      {points(settings[`${key}WinPoints`])} / {points(settings[`${key}LossPoints`])}{' '}
                      / {points(settings[`${key}PushPoints`])}
                      <span className="text-body-secondary fw-normal"> W/L/P</span>
                    </Fact>
                  ))}
                </div>

                <h3 className="h6 text-uppercase text-body-secondary mb-2">Rules</h3>
                <div className="small">
                  <Fact label="Picks close">
                    {settings.lockLeadMinutes} min before kickoff
                  </Fact>
                  <Fact label={`Most picks per ${settings.cadence === 'DAILY' ? 'day' : 'week'}`}>
                    {settings.maxPicksPerCadence ?? 'No limit'}
                  </Fact>
                  <Fact label="Two markets on one game">
                    {settings.multiplePicksPerGame ? 'Allowed' : 'Not allowed'}
                  </Fact>
                  {settings.teamPickLimit != null && (
                    <Fact label="Times a team can be picked">
                      {settings.teamPickLimit} ({settings.teamPickLimitScope?.toLowerCase()})
                    </Fact>
                  )}
                  {settings.groupType === 'ELIMINATION' && (
                    <>
                      <Fact label="Wrong picks allowed">{settings.strikesAllowed}</Fact>
                      <Fact
                        label={`Fewest picks per ${settings.cadence === 'DAILY' ? 'day' : 'week'}`}
                      >
                        {settings.minPicksPerCadence}
                      </Fact>
                    </>
                  )}
                  <Fact label="Joining">
                    {settings.requireApproval ? 'Needs an owner’s approval' : 'Open'}
                  </Fact>
                </div>
              </>
            )
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button
            as={Link}
            to={`/groups/${group.id}`}
            variant="link"
            className="text-decoration-none"
            onClick={() => setInfo(false)}
          >
            Open group page
          </Button>
        </Modal.Footer>
      </Modal>

      {/* ----------------------------------------------------- change group */}
      <Modal show={picking} onHide={() => setPicking(false)} centered scrollable>
        <Modal.Header closeButton>
          <Modal.Title className="h5">Choose a group</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {/* One list, not two. Favourites used to be repeated as a block of
              large buttons above the same rows again below, which is what made
              this feel heavy - sorting them to the top says "first" without
              showing anyone their own groups twice. */}
          {groups.length > SEARCH_FROM && (
            <InputGroup className="mb-2" size="sm">
              <Form.Control
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search your groups"
                aria-label="Search your groups"
                autoFocus
              />
            </InputGroup>
          )}

          {visible.length === 0 ? (
            <p className="text-body-secondary small mb-0">No groups match that search.</p>
          ) : (
            <div className="d-grid">
              {visible.map((entry) => (
                // The row you are already in is outlined rather than badged -
                // it marks the whole row instead of adding another thing to
                // read at the end of it. It sorts first, so a small heading
                // and a gap after it separate "where you are" from "where you
                // could go" without a second list.
                <Fragment key={entry.id}>
                  {entry.id === groupId && (
                    <span className="text-body-secondary group-subline mb-1">Current</span>
                  )}
                  {/* Labels the rest of the list, once, at whichever row is
                      the first that is not the current one. */}
                  {entry.id !== groupId && entry.id === firstOtherId && (
                    <span className="text-body-secondary group-subline mb-1">Other groups</span>
                  )}

                  <div
                    className={`d-flex align-items-center gap-2 rounded px-2 py-1 border ${
                      entry.id === groupId ? 'border-primary mb-3' : 'border-transparent'
                    }`}
                    aria-current={entry.id === groupId ? 'true' : undefined}
                  >
                    {/* Its own target so clicking it pins rather than switching. */}
                    <Button
                      variant="link"
                      className="p-0 text-warning flex-shrink-0 d-flex"
                      disabled={busyId === entry.id}
                      aria-label={entry.favorite ? 'Remove favourite' : 'Add favourite'}
                      title={entry.favorite ? 'Remove favourite' : 'Add favourite'}
                      onClick={() => toggleFavorite(entry)}
                    >
                      <Star filled={entry.favorite} />
                    </Button>

                    {/* Group names are not unique - two sets of friends can both
                        call theirs "The Office" - so the name alone cannot
                        identify one. The sub-line is what tells them apart. */}
                    <Button
                      variant="link"
                      className="p-0 flex-grow-1 text-start text-body text-decoration-none"
                      // A flex child will not shrink below its content without
                      // this, so the truncation never kicks in.
                      style={{ minWidth: 0 }}
                      onClick={() => choose(entry.id)}
                    >
                      <span className="d-block text-truncate">{entry.name}</span>
                      <span className="d-block text-truncate text-body-secondary group-subline">
                        {describe(entry)}
                      </span>
                    </Button>
                  </div>
                </Fragment>
              ))}
            </div>
          )}

          <Link
            to="/groups"
            className="small d-inline-block mt-3"
            onClick={() => setPicking(false)}
          >
            Find another group
          </Link>
        </Modal.Body>
      </Modal>
    </>
  );
}
