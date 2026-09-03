/**
 * Where this app lives, and how to build a link into it.
 *
 * <p>The site is served from a sub-path on GitHub Pages - VITE_BASE_PATH is
 * {@code /cfbpicks/} there - and the router is mounted with that as its
 * basename. So a route path like {@code /join/abc} is only half a URL: the
 * whole one needs the base in front of it.
 *
 * <p>This exists because getting that wrong is silent. A share link built from
 * {@code window.location.origin} alone works perfectly in development, where
 * the base is "/", and lands on GitHub's own 404 page in production - which is
 * exactly what happened to every invitation the app has handed out.
 */

/** No trailing slash, so joining is always base + "/" + path. */
export const BASENAME = (import.meta.env.VITE_BASE_PATH || '/').replace(/\/$/, '');

/**
 * An absolute URL for a route in this app, safe to paste into a message.
 *
 * @param path a router path, with or without its leading slash
 */
export function appUrl(path) {
  const clean = path.startsWith('/') ? path : `/${path}`;
  return `${window.location.origin}${BASENAME}${clean}`;
}
