import { Link } from 'react-router-dom';

/**
 * Every team, athlete and coach the site renders goes through one of these,
 * so a name is a link in one place and a link everywhere. If you find
 * yourself writing a bare team name in JSX, use TeamLink instead.
 */

/**
 * A player's portrait, or a placeholder when there is none.
 *
 * <p>ESPN has a headshot for nearly every player but not quite all - one in a
 * sampled squad of 120 had none - so the fallback is a real case rather than
 * defensive padding. It is a neutral silhouette rather than initials: a roster
 * is scanned by face, and letters in a circle read as a different kind of
 * thing entirely.
 *
 * <p>The image is cropped to a circle and top-anchored, because these portraits
 * are head-and-shoulders and centring them cuts off the chin.
 */
export function AthleteHeadshot({ url, size = 44, className = '' }) {
  const shared = {
    width: size,
    height: size,
    className: `rounded-circle flex-shrink-0 bg-secondary-subtle ${className}`,
  };

  if (!url) {
    return (
      <span
        {...shared}
        className={`${shared.className} d-inline-flex align-items-center justify-content-center text-secondary-emphasis`}
        style={{ width: size, height: size }}
        aria-hidden="true"
      >
        {/* Bootstrap Icons is not a dependency here, so the silhouette is
            inline SVG rather than a font glyph. */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width={size * 0.58}
          height={size * 0.58}
          viewBox="0 0 16 16"
          fill="currentColor"
        >
          <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm2 1H6a4 4 0 0 0-4 4 2 2 0 0 0 2 2h8a2 2 0 0 0 2-2 4 4 0 0 0-4-4Z" />
        </svg>
      </span>
    );
  }

  return (
    <img
      {...shared}
      src={url}
      alt=""
      loading="lazy"
      style={{ width: size, height: size, objectFit: 'cover', objectPosition: 'top center' }}
    />
  );
}

export function TeamLogo({ team, size = 24, className = '' }) {
  if (!team?.logoUrl) {
    return (
      <span
        className={`d-inline-flex align-items-center justify-content-center rounded-circle bg-secondary-subtle text-secondary-emphasis ${className}`}
        style={{ width: size, height: size, fontSize: size * 0.4 }}
        aria-hidden="true"
      >
        {team?.abbreviation?.slice(0, 3) ?? '?'}
      </span>
    );
  }

  return (
    <img
      src={team.logoUrl}
      alt=""
      width={size}
      height={size}
      loading="lazy"
      className={className}
      style={{ objectFit: 'contain' }}
    />
  );
}

/**
 * A team's poll position, shown before the name as "#1".
 *
 * <p>The rank arrives on the team summary already resolved to the right week
 * and the right poll, so every screen agrees on it. Unranked teams carry
 * null and render nothing.
 */
export function TeamRank({ rank, className = '' }) {
  if (rank == null) return null;
  return (
    <span className={`team-rank ${className}`} title={`Ranked #${rank}`}>
      #{rank}
    </span>
  );
}

export function TeamLink({ team, name, logo = true, logoSize = 22, className = '' }) {
  const label = team?.school ?? name;

  // Non-FBS opponents come through with a name but no team record, so there
  // is nothing to link to. Render plain text rather than a dead link.
  if (!team?.id) {
    return <span className={className}>{label}</span>;
  }

  return (
    // min-width 0 on the link and text-truncate on the name let a long school
    // ellipsize when its column is squeezed, rather than being clipped
    // mid-letter. Both are inert wherever the container gives it room, which is
    // everywhere except the game card's team rows.
    <Link
      to={`/teams/${team.id}`}
      className={`text-decoration-none link-body-emphasis d-inline-flex align-items-center gap-2 ${className}`}
      style={{ minWidth: 0 }}
    >
      {logo && <TeamLogo team={team} size={logoSize} />}
      <span className="text-truncate">
        <TeamRank rank={team.rank} className="me-1" />
        {label}
      </span>
    </Link>
  );
}

export function AthleteLink({ athlete, className = '' }) {
  const label = [athlete?.firstName, athlete?.lastName].filter(Boolean).join(' ');

  if (!athlete?.id) {
    return <span className={className}>{label}</span>;
  }

  return (
    <Link to={`/athletes/${athlete.id}`} className={`text-decoration-none ${className}`}>
      {label}
    </Link>
  );
}

export function CoachLink({ coach, className = '' }) {
  const label = [coach?.firstName, coach?.lastName].filter(Boolean).join(' ');

  if (!coach?.id) {
    return <span className={className}>{label}</span>;
  }

  return (
    <Link to={`/coaches/${coach.id}`} className={`text-decoration-none ${className}`}>
      {label}
    </Link>
  );
}
