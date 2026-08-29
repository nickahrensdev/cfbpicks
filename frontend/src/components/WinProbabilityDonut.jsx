/**
 * Two-slice donut for a game's win probability.
 *
 * <p>Hand-drawn SVG rather than a charting library: two arcs on one circle is
 * about thirty lines, where a chart dependency would be the largest thing on
 * the page. Each arc is a stroke on the same circle, offset so the second
 * begins where the first ends.
 *
 * <p>Teams are drawn in their own colours where we know them, with the away
 * side falling back to a lighter tone so two similarly-coloured programs never
 * produce a donut with no visible boundary.
 */

const SIZE = 168;
const STROKE = 22;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const FALLBACK_HOME = '#468189';
const FALLBACK_AWAY = '#9DBEBB';

const percent = (value) => `${(Number(value) * 100).toFixed(1)}%`;

function Slice({ fraction, offset, color, label }) {
  return (
    <circle
      cx={SIZE / 2}
      cy={SIZE / 2}
      r={RADIUS}
      fill="none"
      stroke={color}
      strokeWidth={STROKE}
      strokeDasharray={`${fraction * CIRCUMFERENCE} ${CIRCUMFERENCE}`}
      strokeDashoffset={-offset * CIRCUMFERENCE}
    >
      <title>{label}</title>
    </circle>
  );
}

export default function WinProbabilityDonut({
  homeName,
  awayName,
  homeProbability,
  awayProbability,
  homeColor,
  awayColor,
}) {
  const home = Number(homeProbability);
  const away = Number(awayProbability);

  // The two should sum to 1, but they come from a third party - normalising
  // means a rounding artefact leaves a hairline gap rather than a wrong chart.
  const total = home + away;
  if (!Number.isFinite(total) || total <= 0) return null;

  const homeShare = home / total;
  const awayShare = away / total;

  const leader = homeShare >= awayShare ? homeName : awayName;
  const leaderShare = Math.max(homeShare, awayShare);

  const colors = {
    home: homeColor || FALLBACK_HOME,
    away: awayColor || FALLBACK_AWAY,
  };
  // Two teams in the same colour would read as one arc.
  if (colors.home.toLowerCase() === colors.away.toLowerCase()) {
    colors.away = FALLBACK_AWAY;
  }

  return (
    <div className="d-flex flex-column flex-sm-row align-items-center gap-4">
      <div style={{ position: 'relative', width: SIZE, height: SIZE, flexShrink: 0 }}>
        <svg
          width={SIZE}
          height={SIZE}
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          role="img"
          aria-label={`Win probability: ${homeName} ${percent(homeShare)}, ${awayName} ${percent(
            awayShare,
          )}`}
          // Start the first arc at twelve o'clock instead of three.
          style={{ transform: 'rotate(-90deg)' }}
        >
          <Slice
            fraction={awayShare}
            offset={0}
            color={colors.away}
            label={`${awayName} ${percent(awayShare)}`}
          />
          <Slice
            fraction={homeShare}
            offset={awayShare}
            color={colors.home}
            label={`${homeName} ${percent(homeShare)}`}
          />
        </svg>

        {/* Centred over the hole. aria-hidden because the svg's label already
            reads both numbers out. */}
        <div
          className="d-flex flex-column align-items-center justify-content-center text-center"
          style={{ position: 'absolute', inset: 0, padding: STROKE + 6 }}
          aria-hidden="true"
        >
          <div className="fs-4 fw-bold lh-1">{percent(leaderShare)}</div>
          <div className="small text-body-secondary text-truncate w-100">{leader}</div>
        </div>
      </div>

      <div className="d-grid gap-2 flex-grow-1">
        {[
          [awayName, awayShare, colors.away, 'Away'],
          [homeName, homeShare, colors.home, 'Home'],
        ].map(([name, share, color, side]) => (
          <div key={side} className="d-flex align-items-center gap-2">
            <span
              className="rounded-circle flex-shrink-0"
              style={{ width: 12, height: 12, background: color }}
              aria-hidden="true"
            />
            <span className="flex-grow-1 text-truncate">
              {name} <span className="text-body-tertiary small">· {side.toLowerCase()}</span>
            </span>
            <span className="fw-semibold" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {percent(share)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
