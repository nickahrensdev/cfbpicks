import { Form } from 'react-bootstrap';

/**
 * Week picker.
 *
 * <p>Every week in the season is offered, not just the ones already
 * ingested, so members can look ahead. Selecting a week with no games loaded
 * is handled by the page, which explains the empty board.
 *
 * <p>{@code compact} drops the wrapper and the label so it can sit inline
 * with buttons - the options read "Week 3", so the control names itself.
 */
export default function WeekSelector({
  weeks,
  current,
  onChange,
  label = 'Week',
  compact = false,
}) {
  if (!weeks?.length) return null;

  const select = (
    <Form.Select
      id="week-selector"
      size={compact ? 'sm' : undefined}
      aria-label={compact ? label : undefined}
      className={compact ? 'w-auto flex-shrink-0' : undefined}
      value={current ?? ''}
      onChange={(event) => onChange(Number(event.target.value))}
    >
      {weeks.map((week) => (
        <option key={week} value={week}>
          Week {week}
        </option>
      ))}
    </Form.Select>
  );

  if (compact) {
    return select;
  }

  return (
    <Form.Group className="mb-3" style={{ maxWidth: '13rem' }}>
      <Form.Label htmlFor="week-selector" className="small fw-semibold mb-1">
        {label}
      </Form.Label>
      {select}
    </Form.Group>
  );
}
