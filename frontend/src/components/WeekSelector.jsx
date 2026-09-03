import { Button, Form, InputGroup } from 'react-bootstrap';

/**
 * Week picker.
 *
 * <p>Every week in the season is offered, not just the ones already
 * ingested, so members can look ahead. Selecting a week with no games loaded
 * is handled by the page, which explains the empty board.
 *
 * <p>The arrows step to the neighbouring week in the list rather than simply
 * adding one, so they cannot land on a week the season does not have - and
 * they stop at either end instead of going nowhere.
 *
 * <p>{@code compact} drops the wrapper and the label so it can sit inline
 * with buttons - the options read "Week 3", so the control names itself.
 * {@code size} is Bootstrap's input-group size; the games board asks for
 * "lg" because the period being shown is the single most important thing on
 * the page and was being read as a minor filter at "sm".
 */
export default function WeekSelector({
  weeks,
  current,
  onChange,
  label = 'Week',
  compact = false,
  size,
}) {
  if (!weeks?.length) return null;

  const ordered = [...weeks].sort((a, b) => a - b);
  const previous = [...ordered].reverse().find((week) => week < current) ?? null;
  const next = ordered.find((week) => week > current) ?? null;

  const control = (
    <InputGroup
      size={size ?? (compact ? 'sm' : undefined)}
      // flex-nowrap: Bootstrap's input-group wraps by default, which
      // breaks the arrows onto their own lines when the toolbar is
      // tight. flex-shrink-0 keeps the toolbar from squeezing it there
      // in the first place.
      className={compact ? 'w-auto flex-nowrap flex-shrink-0' : 'flex-nowrap'}
    >
      <Button
        variant="outline-secondary"
        onClick={() => previous != null && onChange(previous)}
        disabled={previous == null}
        aria-label="Previous week"
        title={previous == null ? 'First week of the season' : `Week ${previous}`}
      >
        ‹
      </Button>
      <Form.Select
        id="week-selector"
        aria-label={compact ? label : undefined}
        className="flex-grow-0 w-auto"
        value={current ?? ''}
        onChange={(event) => onChange(Number(event.target.value))}
      >
        {ordered.map((week) => (
          <option key={week} value={week}>
            Week {week}
          </option>
        ))}
      </Form.Select>
      <Button
        variant="outline-secondary"
        onClick={() => next != null && onChange(next)}
        disabled={next == null}
        aria-label="Next week"
        title={next == null ? 'Last week of the season' : `Week ${next}`}
      >
        ›
      </Button>
    </InputGroup>
  );

  if (compact) {
    return control;
  }

  return (
    <Form.Group className="mb-3">
      <Form.Label htmlFor="week-selector" className="small fw-semibold mb-1">
        {label}
      </Form.Label>
      {control}
    </Form.Group>
  );
}
