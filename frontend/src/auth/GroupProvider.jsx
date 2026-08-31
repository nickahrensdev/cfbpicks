import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { useAuth } from './AuthProvider.jsx';
import { api } from '../api/client.js';

const GroupContext = createContext(null);

const STORAGE_KEY = 'nickspicks.groupId';

/**
 * Which league the member is currently looking at.
 *
 * <p>Every board is group-scoped now - the games page, the leaderboard, a
 * member's card - so the selection has to live above all of them rather than in
 * a query string each page parses for itself.
 *
 * <p>Persisted per browser so a reload does not drop you into a different
 * league than the one you were playing. The stored id is validated against the
 * groups you are actually in: leaving a group, or opening the site on a machine
 * that remembers a group you were removed from, falls back to the first one you
 * still belong to rather than sending an id the API will reject.
 */
export function GroupProvider({ children }) {
  const { session } = useAuth();
  const [groups, setGroups] = useState([]);
  const [groupId, setGroupId] = useState(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!session) {
      setGroups([]);
      setGroupId(null);
      return;
    }
    setLoading(true);
    try {
      const mine = await api.myGroups();
      setGroups(mine);

      let remembered = null;
      try {
        remembered = window.localStorage.getItem(STORAGE_KEY);
      } catch {
        // Private browsing, or site data blocked. Not worth failing over.
      }

      const stillAMember = mine.some((group) => group.id === remembered);
      setGroupId(stillAMember ? remembered : (mine[0]?.id ?? null));
    } catch {
      // A failed load should not blank the app; pages surface their own errors.
      setGroups([]);
      setGroupId(null);
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const selectGroup = useCallback((id) => {
    setGroupId(id);
    try {
      window.localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // Selection still works for this session; it just will not be remembered.
    }
  }, []);

  const value = useMemo(
    () => ({
      groups,
      groupId,
      group: groups.find((entry) => entry.id === groupId) ?? null,
      selectGroup,
      refresh,
      loading,
      // Distinct from "still loading": a member in no group at all needs to be
      // sent to find one, not shown an empty board.
      hasNoGroups: !loading && groups.length === 0,
    }),
    [groups, groupId, selectGroup, refresh, loading],
  );

  return <GroupContext.Provider value={value}>{children}</GroupContext.Provider>;
}

export function useGroup() {
  const context = useContext(GroupContext);
  if (!context) {
    throw new Error('useGroup must be used inside a GroupProvider');
  }
  return context;
}
