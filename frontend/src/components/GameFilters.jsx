import { useState } from 'react';
import { Button, Col, Collapse, Form, Row } from 'react-bootstrap';

import RangeSlider from './RangeSlider.jsx';

/** Funnel. Decorative - the button around it carries the accessible name. */
function FunnelIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M1.5 1.5A.5.5 0 0 1 2 1h12a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-.128.334L10 8.692V13.5a.5.5 0 0 1-.342.474l-3 1A.5.5 0 0 1 6 14.5V8.692L1.628 3.834A.5.5 0 0 1 1.5 3.5v-2z" />
    </svg>
  );
}

/**
 * Checkmark, shown only while "My picks" is active. Solid vs. outline alone
 * is too subtle a difference to read at a glance in every color theme -
 * this makes the on state unmistakable regardless of palette or contrast.
 */
function CheckIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className="me-1"
    >
      <path d="M13.485 1.929a1 1 0 0 1 .143 1.407l-7 8.5a1 1 0 0 1-1.49.083L2.153 8.933a1 1 0 1 1 1.394-1.433l2.21 2.152 6.34-7.7a1 1 0 0 1 1.388-.023z" />
    </svg>
  );
}

/** Refresh arrows. Decorative - the button around it carries the accessible name. */
function RefreshIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M8 3a5 5 0 1 0 4.546 2.914.5.5 0 0 1 .908-.417A6 6 0 1 1 8 2z" />
      <path d="M8 4.466V.534a.25.25 0 0 1 .41-.192l2.36 1.966c.12.1.12.284 0 .384L8.41 4.658A.25.25 0 0 1 8 4.466" />
    </svg>
  );
}

/**
 * Conference / team / spread-size filters, collapsed by default so the board
 * is the first thing on screen.
 *
 * <p>"My picks" stays outside the collapse: it is a view switch people reach
 * for constantly, and burying the most-used control behind a disclosure
 * would cost more than the space it saves. The count of active filters is
 * shown on the toggle so a collapsed panel can never hide the fact that the
 * list is filtered.
 */
export default function GameFilters({
  options,
  value,
  onChange,
  resultCount,
  totalCount,
  /** Rendered first in the control row - the week picker on the games board. */
  weekSelector = null,
  /** Manual re-fetch of the current board, next to the filter toggle. */
  onRefresh = null,
  refreshing = false,
}) {
  const [open, setOpen] = useState(false);

  const update = (patch) => onChange({ ...value, ...patch });

  const ceiling = Math.max(Math.ceil(options?.maxSpread ?? 0), 1);

  // The band is only a filter when it is narrower than the full range.
  const low = value.minSpread ?? 0;
  const high = value.maxSpread ?? ceiling;
  const spreadNarrowed = low > 0 || high < ceiling;

  const activeCount =
    (value.conference ? 1 : 0)
    + (value.teamId ? 1 : 0)
    + (spreadNarrowed ? 1 : 0)
    + (value.pickableOnly ? 1 : 0)
    + (value.todayOnly ? 1 : 0);
  const filtered = activeCount > 0 || value.mine;

  return (
    <div className="bg-body-tertiary rounded-3 p-3 mb-4">
      {/* One row, never wrapping: week on the left, actions on the right.
          The counts sit underneath so the controls keep their places as the
          text beside them changes length. */}
      <div className="d-flex align-items-center gap-2 flex-nowrap">
        {weekSelector}

        <div className="ms-auto d-flex align-items-center gap-2 flex-nowrap">
          <Button
            size="sm"
            className="control-btn"
            variant={value.mine ? 'primary' : 'outline-primary'}
            onClick={() => update({ mine: !value.mine })}
            aria-pressed={value.mine}
          >
            {value.mine && <CheckIcon />}
            My picks
          </Button>

          {/* Icon only, so it needs an explicit name. The count stays visible
              because a collapsed panel must never hide that the list is
              filtered, and the solid variant shows it is open. */}
          <Button
            size="sm"
            className="control-btn"
            variant={open || activeCount > 0 ? 'secondary' : 'outline-secondary'}
            onClick={() => setOpen((current) => !current)}
            aria-expanded={open}
            aria-controls="game-filter-panel"
            aria-label={activeCount > 0 ? `Filters, ${activeCount} active` : 'Filters'}
            title="Filters"
          >
            <FunnelIcon />
            {activeCount > 0 && (
              <span className="ms-1 fw-semibold" aria-hidden="true">
                {activeCount}
              </span>
            )}
          </Button>

          {onRefresh && (
            <Button
              size="sm"
              className="control-btn"
              variant="outline-secondary"
              onClick={onRefresh}
              disabled={refreshing}
              aria-label="Refresh games"
              title="Refresh"
            >
              <RefreshIcon />
            </Button>
          )}
        </div>
      </div>

      <div className="d-flex align-items-center justify-content-between gap-2 mt-2">
        <span className="small text-body-secondary">
          {filtered ? `${resultCount} of ${totalCount} games` : `${totalCount} games`}
        </span>

        {filtered && (
          <Button
            variant="link"
            size="sm"
            className="p-0"
            onClick={() =>
              onChange({
                conference: null,
                teamId: null,
                minSpread: null,
                maxSpread: null,
                mine: false,
                pickableOnly: false,
                todayOnly: false,
              })
            }
          >
            Clear
          </Button>
        )}
      </div>

      <Collapse in={open}>
        <div id="game-filter-panel">
          <Row className="g-3 align-items-end pt-3">
            <Col xs={12} md={4}>
              <Form.Label htmlFor="filter-conference" className="small fw-semibold mb-1">
                Conference
              </Form.Label>
              <Form.Select
                id="filter-conference"
                size="sm"
                value={value.conference ?? ''}
                onChange={(e) => update({ conference: e.target.value || null })}
              >
                <option value="">All conferences</option>
                {(options?.conferences ?? []).map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </Form.Select>
            </Col>

            <Col xs={12} md={4}>
              <Form.Label htmlFor="filter-team" className="small fw-semibold mb-1">
                Team
              </Form.Label>
              <Form.Select
                id="filter-team"
                size="sm"
                value={value.teamId ?? ''}
                onChange={(e) => update({ teamId: e.target.value || null })}
              >
                <option value="">All teams</option>
                {(options?.teams ?? []).map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.school}
                  </option>
                ))}
              </Form.Select>
            </Col>

            <Col xs={12} md={4}>
              <div className="d-flex justify-content-between align-items-baseline mb-1">
                <span className="small fw-semibold">Spread size</span>
                <span className="small text-body-secondary">
                  {spreadNarrowed ? `${low} – ${high} pts` : 'any'}
                </span>
              </div>
              <RangeSlider
                min={0}
                max={ceiling}
                step={0.5}
                value={[low, high]}
                minLabel="Smallest spread"
                maxLabel="Largest spread"
                onChange={([nextLow, nextHigh]) =>
                  // A bound at the end of the range means "no limit" rather
                  // than a filter that happens to match everything.
                  update({
                    minSpread: nextLow <= 0 ? null : nextLow,
                    maxSpread: nextHigh >= ceiling ? null : nextHigh,
                  })
                }
              />
              <div className="d-flex justify-content-between small text-body-tertiary">
                <span>0</span>
                <span>{ceiling}+</span>
              </div>
            </Col>

            <Col xs={12}>
              <Form.Check
                type="switch"
                id="filter-pickable"
                checked={Boolean(value.pickableOnly)}
                onChange={(e) => update({ pickableOnly: e.target.checked })}
                label="Only games I can still pick"
              />
              <div className="small text-body-tertiary">
                Hides games that have locked, kicked off or have no line posted.
              </div>
            </Col>

            <Col xs={12}>
              <Form.Check
                type="switch"
                id="filter-today"
                checked={Boolean(value.todayOnly)}
                onChange={(e) => update({ todayOnly: e.target.checked })}
                label="Today's games"
              />
              <div className="small text-body-tertiary">
                Only games kicking off today, your local time.
              </div>
            </Col>
          </Row>
        </div>
      </Collapse>
    </div>
  );
}
