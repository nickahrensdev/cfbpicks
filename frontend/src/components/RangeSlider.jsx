/**
 * Two-thumb range slider.
 *
 * <p>There is no native two-handle input, so this stacks two range inputs on
 * one track. The inputs themselves are transparent and ignore pointer events
 * except on their thumbs, which is what lets the lower thumb stay grabbable
 * where the two overlap - otherwise whichever input is painted last would
 * swallow every drag.
 */
export default function RangeSlider({
  min,
  max,
  step = 1,
  value,
  onChange,
  minLabel = 'Minimum',
  maxLabel = 'Maximum',
}) {
  const [low, high] = value;
  const span = max - min || 1;

  const percent = (n) => ((n - min) / span) * 100;

  // Thumbs must not cross: each one clamps against the other rather than
  // swapping, so a drag never flips which handle you are holding.
  const changeLow = (next) => onChange([Math.min(next, high), high]);
  const changeHigh = (next) => onChange([low, Math.max(next, low)]);

  return (
    <div className="range-slider">
      <div className="range-slider__track" />
      <div
        className="range-slider__fill"
        style={{ left: `${percent(low)}%`, right: `${100 - percent(high)}%` }}
      />

      <input
        type="range"
        className="range-slider__input"
        min={min}
        max={max}
        step={step}
        value={low}
        aria-label={minLabel}
        onChange={(event) => changeLow(Number(event.target.value))}
        // At the top of the range the lower thumb would sit under the upper
        // one with nothing to its right to grab; lift it so it stays usable.
        style={{ zIndex: low >= max - (max - min) * 0.05 ? 4 : 3 }}
      />
      <input
        type="range"
        className="range-slider__input"
        min={min}
        max={max}
        step={step}
        value={high}
        aria-label={maxLabel}
        onChange={(event) => changeHigh(Number(event.target.value))}
      />
    </div>
  );
}
