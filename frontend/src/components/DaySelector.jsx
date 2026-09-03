import { Button, Form, InputGroup } from 'react-bootstrap';

/** ISO date (yyyy-mm-dd) shifted by whole days, without touching UTC. */
function shift(iso, days) {
  // Parsed as local noon rather than midnight: a date-only string parses as
  // UTC, and midnight UTC is the previous evening in the Americas, so a naive
  // shift can land a day off depending on the browser's zone.
  const date = new Date(`${iso}T12:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

/** "Sat, Sep 5" - the label the buttons step through. */
export function formatDay(iso) {
  if (!iso) return '';
  return new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
}

/**
 * Day picker for a group that picks daily.
 *
 * <p>A week dropdown is the wrong control for a daily allowance: a week holds
 * several of them, so it cannot say which day's quota you are spending.
 *
 * <p>The arrows move to the next and previous days that actually have games.
 * College football clusters on Saturdays, so stepping one calendar day at a
 * time would mean clicking through several empty boards to get anywhere - the
 * arrows exist to save exactly that. The date field is still there for jumping
 * somewhere specific, including a day with nothing on.
 */
export default function DaySelector({ value, onChange, days = [], compact = false, size }) {
  if (!value) return null;

  // The list is sorted, so the neighbours are the first either side of today's
  // value. Falls back to a plain calendar step when no schedule is loaded, so
  // the control is never dead.
  const previous = [...days].reverse().find((day) => day < value) ?? (days.length ? null : shift(value, -1));
  const next = days.find((day) => day > value) ?? (days.length ? null : shift(value, 1));

  const control = (
    <InputGroup
      // See WeekSelector: undefined is Bootstrap's medium, and `compact` no
      // longer forces a size of its own.
      size={size}
      // flex-nowrap: Bootstrap's input-group wraps by default, which
      // breaks the arrows onto their own lines when the toolbar is
      // tight. flex-shrink-0 keeps the toolbar from squeezing it there
      // in the first place.
      className={compact ? 'w-auto flex-nowrap flex-shrink-0' : 'flex-nowrap'}
    >
      <Button
        variant="outline-secondary"
        onClick={() => previous && onChange(previous)}
        disabled={!previous}
        aria-label="Previous day with games"
        title={previous ? `Previous games: ${formatDay(previous)}` : 'No earlier games'}
      >
        ‹
      </Button>
      <Form.Control
        id="day-selector"
        type="date"
        value={value}
        min={days[0] ?? undefined}
        max={days[days.length - 1] ?? undefined}
        onChange={(event) => event.target.value && onChange(event.target.value)}
        aria-label="Game day"
        // Wide enough for the date at the larger size the games board uses -
        // at 10rem the native picker's own icon crowded the digits out.
        style={{ maxWidth: size === "lg" ? "13rem" : "11rem" }}
      />
      <Button
        variant="outline-secondary"
        onClick={() => next && onChange(next)}
        disabled={!next}
        aria-label="Next day with games"
        title={next ? `Next games: ${formatDay(next)}` : 'No later games'}
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
      <Form.Label htmlFor="day-selector" className="small fw-semibold mb-1">
        Day
      </Form.Label>
      {control}
    </Form.Group>
  );
}
