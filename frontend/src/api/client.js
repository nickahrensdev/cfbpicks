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
  updateUsername: (username) =>
    request('/api/me/username', { method: 'PUT', body: JSON.stringify({ username }) }),
  updateTheme: (theme, colorMode) =>
    request('/api/me/theme', { method: 'PUT', body: JSON.stringify({ theme, colorMode }) }),
  meta: () => request('/api/meta'),
  // Not under /api: it is Spring's own actuator endpoint. The dev server
  // proxies /actuator through to the backend and the API allows it CORS, so
  // the same call works locally and against the deployed origin.
  actuatorInfo: () => request('/actuator/info'),

  currentWeek: () => request('/api/weeks/current'),
  // Every board is scoped to a group: your picks on it, and that group's lock
  // times. The server rejects a group you are not a member of.
  // `date` is for a group that picks daily; it takes precedence over `week`,
  // because a week would mix several days' allowances onto one board.
  games: ({ groupId, season, week, date, conference, teamId, minSpread, maxSpread } = {}) =>
    request(
      `/api/games${query({
        groupId, season, week, date, conference, teamId, minSpread, maxSpread,
      })}`,
    ),
  gameDays: ({ season } = {}) => request(`/api/games/days${query({ season })}`),
  gameFilters: ({ season, week, date } = {}) =>
    request(`/api/games/filters${query({ season, week, date })}`),
  game: (id, { groupId } = {}) => request(`/api/games/${id}${query({ groupId })}`),

  // `date` is for a group that picks daily: a week holds seven of those
  // groups' allowances, so only a named day has a countdown to give.
  myPicks: ({ groupId, season, week, date } = {}) =>
    request(`/api/picks${query({ groupId, season, week, date })}`),
  // expectedSpread is the line the page was showing. The server rejects the
  // pick with LINE_MOVED if it is no longer current, so a stale tab cannot
  // commit someone to a number they never saw.
  createPick: (groupId, gameId, selection, expectedSpread) =>
    request(`/api/picks${query({ groupId })}`, {
      method: 'POST',
      body: JSON.stringify({ gameId, selection, expectedSpread }),
    }),
  updatePick: (groupId, id, selection, expectedSpread) =>
    request(`/api/picks/${id}${query({ groupId })}`, {
      method: 'PUT',
      body: JSON.stringify({ selection, expectedSpread }),
    }),
  relockPick: (groupId, id) =>
    request(`/api/picks/${id}/relock${query({ groupId })}`, { method: 'POST' }),
  deletePick: (groupId, id) =>
    request(`/api/picks/${id}${query({ groupId })}`, { method: 'DELETE' }),
  memberPicks: (userId, { groupId, season, week } = {}) =>
    request(`/api/members/${userId}/picks${query({ groupId, season, week })}`),
  // Only the groups the viewer is also in - see MemberGroupsController.
  memberGroups: (userId) => request(`/api/members/${userId}/groups`),

  leaderboard: ({ groupId, season, week } = {}) =>
    request(`/api/leaderboard${query({ groupId, season, week })}`),

  teams: ({ conference, search } = {}) => request(`/api/teams${query({ conference, search })}`),
  // groupId is optional here: a team page is reachable from any team name on
  // the site, and without a group it just renders the schedule unmarked.
  team: (id, { groupId } = {}) => request(`/api/teams/${id}${query({ groupId })}`),
  // Cached and refreshed server-side; safe to call every time a matchup is
  // viewed - see TeamMatchupService for the staleness rule.
  matchup: (team1Id, team2Id) => request(`/api/teams/matchup${query({ team1Id, team2Id })}`),
  athlete: (id) => request(`/api/athletes/${id}`),
  coach: (id) => request(`/api/coaches/${id}`),

  // Groups. Creation is admin-only for now, so it sits under /api/admin;
  // everything an owner or member does is on /api/groups.
  myGroups: () => request('/api/groups/mine'),
  searchGroups: ({ q } = {}) => request(`/api/groups/search${query({ q })}`),
  group: (id) => request(`/api/groups/${id}`),
  groupMembers: (id) => request(`/api/groups/${id}/members`),
  // Omitted password is a real case - an open group needs none, and the server
  // answers GROUP_PASSWORD_REQUIRED if it turns out one was needed.
  joinGroup: (id, password) =>
    request(`/api/groups/${id}/join`, { method: 'POST', body: JSON.stringify({ password }) }),
  favoriteGroup: (id, favorite) =>
    request(`/api/groups/${id}/favorite`, {
      method: 'PUT',
      body: JSON.stringify({ favorite }),
    }),
  // Returns the same token every time - a link already sent has to keep
  // working, so pressing Share again never mints a new one.
  shareGroup: (id) => request(`/api/groups/${id}/share`, { method: 'POST' }),
  // The only group call that works signed out: someone deciding whether to
  // make an account has to see what they are being invited to first.
  shareInvite: (token) => request(`/api/share/${token}`),
  claimShare: (token) => request(`/api/share/${token}/claim`, { method: 'POST' }),
  joinByShare: (token, password) =>
    request(`/api/share/${token}/join`, { method: 'POST', body: JSON.stringify({ password }) }),
  groupRequests: (id) => request(`/api/groups/${id}/requests`),
  approveGroupRequest: (id, userId) =>
    request(`/api/groups/${id}/requests/${userId}/approve`, { method: 'POST' }),
  denyGroupRequest: (id, userId) =>
    request(`/api/groups/${id}/requests/${userId}/deny`, { method: 'POST' }),
  setGroupMemberRole: (id, userId, role) =>
    request(`/api/groups/${id}/members/${userId}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    }),
  updateGroup: (id, settings) =>
    request(`/api/groups/${id}`, { method: 'PUT', body: JSON.stringify(settings) }),
  deleteGroup: (id) => request(`/api/groups/${id}`, { method: 'DELETE' }),
  leaveGroup: (id, userId) => request(`/api/groups/${id}/members/${userId}`, { method: 'DELETE' }),

  adminGroups: () => request('/api/admin/groups'),
  adminCreateGroup: (settings) =>
    request('/api/admin/groups', { method: 'POST', body: JSON.stringify(settings) }),
  adminGroup: (id) => request(`/api/admin/groups/${id}`),
  adminUpdateGroup: (id, settings) =>
    request(`/api/admin/groups/${id}`, { method: 'PUT', body: JSON.stringify(settings) }),
  adminDeleteGroup: (id) => request(`/api/admin/groups/${id}`, { method: 'DELETE' }),
  adminGroupMembers: (id) => request(`/api/admin/groups/${id}/members`),
  // No adminAddGroupMember: nobody is put into a group they did not choose.
  // Removing one is still an owner's call.
  adminRemoveGroupMember: (id, userId) =>
    request(`/api/admin/groups/${id}/members/${userId}`, { method: 'DELETE' }),

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
  ingestAts: ({ season } = {}) =>
    request(`/api/admin/ingest/ats${query({ season })}`, { method: 'POST' }),
  // Every load above returns immediately (202, {logId, status: "RUNNING"})
  // and finishes in the background - this is where the Data log tab reads
  // the actual result from.
  // Closes out every finished period that has not been charged yet. The
  // hourly job does this anyway; the button is for a group whose minimums were
  // set after a period had already closed.
  settlePeriods: ({ season } = {}) =>
    request(`/api/admin/settle${query({ season })}`, { method: 'POST' }),
  dataLoads: () => request('/api/admin/data-loads'),

  // Scheduled jobs. The schedule itself lives in Supabase pg_cron; these only
  // say whether the app acts when it is called - see V26.
  cronJobs: () => request('/api/admin/cron'),
  setCronJob: (name, enabled) =>
    request(`/api/admin/cron/${name}`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
  setAllCronJobs: (enabled) =>
    request('/api/admin/cron', { method: 'PUT', body: JSON.stringify({ enabled }) }),

  // Member-facing: when the board's lines were last refreshed, and when next.
  lineRefresh: () => request('/api/line-refresh'),

};
