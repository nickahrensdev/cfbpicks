import { supabase, isSupabaseConfigured } from '../lib/supabase.js';

// In dev this is empty and Vite proxies /api to :8080.
// In prod set it at build time to the deployed API origin.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

async function authHeader() {
  if (!isSupabaseConfigured) return {};
  // getSession() reads from memory and refreshes the token when it is close
  // to expiring, so this does not make a network call per request.
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(await authHeader()),
      ...options.headers,
    },
  });

  if (response.status === 204) return null;

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    // Spring returns RFC 7807 ProblemDetail. `code` is the machine-readable
    // discriminator - prefer it over matching on the message text.
    const error = new Error(body?.detail ?? `Request failed (${response.status})`);
    error.status = response.status;
    error.code = body?.code ?? null;
    error.fieldErrors = body?.errors ?? {};
    throw error;
  }

  return body;
}

const query = (params) => {
  const search = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
};

export const api = {
  me: () => request('/api/me'),
  updateDisplayName: (displayName) =>
    request('/api/me', { method: 'PUT', body: JSON.stringify({ displayName }) }),
  updateTheme: (theme, colorMode) =>
    request('/api/me/theme', { method: 'PUT', body: JSON.stringify({ theme, colorMode }) }),
  meta: () => request('/api/meta'),

  currentWeek: () => request('/api/weeks/current'),
  games: ({ season, week, conference, teamId, minSpread, maxSpread } = {}) =>
    request(`/api/games${query({ season, week, conference, teamId, minSpread, maxSpread })}`),
  gameFilters: ({ season, week } = {}) => request(`/api/games/filters${query({ season, week })}`),
  game: (id) => request(`/api/games/${id}`),

  myPicks: ({ season, week } = {}) => request(`/api/picks${query({ season, week })}`),
  // expectedSpread is the line the page was showing. The server rejects the
  // pick with LINE_MOVED if it is no longer current, so a stale tab cannot
  // commit someone to a number they never saw.
  createPick: (gameId, selection, expectedSpread) =>
    request('/api/picks', {
      method: 'POST',
      body: JSON.stringify({ gameId, selection, expectedSpread }),
    }),
  updatePick: (id, selection, expectedSpread) =>
    request(`/api/picks/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ selection, expectedSpread }),
    }),
  relockPick: (id) => request(`/api/picks/${id}/relock`, { method: 'POST' }),
  deletePick: (id) => request(`/api/picks/${id}`, { method: 'DELETE' }),
  memberPicks: (userId, { season, week } = {}) =>
    request(`/api/members/${userId}/picks${query({ season, week })}`),

  leaderboard: ({ season, week } = {}) => request(`/api/leaderboard${query({ season, week })}`),

  teams: ({ conference, search } = {}) => request(`/api/teams${query({ conference, search })}`),
  team: (id) => request(`/api/teams/${id}`),
  // Cached and refreshed server-side; safe to call every time a matchup is
  // viewed - see TeamMatchupService for the staleness rule.
  matchup: (team1Id, team2Id) => request(`/api/teams/matchup${query({ team1Id, team2Id })}`),
  athlete: (id) => request(`/api/athletes/${id}`),
  coach: (id) => request(`/api/coaches/${id}`),

  adminUsers: () => request('/api/admin/users'),
  adminSetRole: (userId, role) =>
    request(`/api/admin/users/${userId}/role`, { method: 'PUT', body: JSON.stringify({ role }) }),
  adminDeleteUser: (userId) => request(`/api/admin/users/${userId}`, { method: 'DELETE' }),
  adminActivity: ({ userId, limit } = {}) =>
    request(`/api/admin/activity${query({ userId, limit })}`),

  quota: () => request('/api/admin/quota'),
  // `parts` names which of calendar/teams/coaches to run; omitted means all.
  ingestReference: ({ season, parts } = {}) =>
    request(
      `/api/admin/ingest/reference${query({ season, parts: parts?.join(',') })}`,
      { method: 'POST' },
    ),
  ingestGames: ({ season } = {}) =>
    request(`/api/admin/ingest/games${query({ season })}`, { method: 'POST' }),
  ingestScores: ({ season } = {}) =>
    request(`/api/admin/ingest/scores${query({ season })}`, { method: 'POST' }),
  ingestRankings: ({ season } = {}) =>
    request(`/api/admin/ingest/rankings${query({ season })}`, { method: 'POST' }),
  // Every load above returns immediately (202, {logId, status: "RUNNING"})
  // and finishes in the background - this is where the Data log tab reads
  // the actual result from.
  dataLoads: () => request('/api/admin/data-loads'),

  deployBackendStatus: () => request('/api/admin/deploy-backend/status'),
  deployBackend: () => request('/api/admin/deploy-backend', { method: 'POST' }),
};
