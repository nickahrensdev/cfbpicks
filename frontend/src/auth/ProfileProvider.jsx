import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { useAuth } from './AuthProvider.jsx';
import { api } from '../api/client.js';

const ProfileContext = createContext(null);

/**
 * The member row behind the Supabase session - display name and role.
 *
 * <p>Fetched once per sign-in rather than per page, because the nav needs the
 * role on every render and /api/me also provisions the row on first sight.
 */
export function ProfileProvider({ children }) {
  const { session } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!session) {
      setProfile(null);
      return null;
    }
    setLoading(true);
    try {
      const next = await api.me();
      setProfile(next);
      return next;
    } catch {
      // A failed profile load should not blank the app; pages surface their
      // own errors.
      setProfile(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value = useMemo(
    () => ({
      profile,
      loading,
      refresh,
      isAdmin: profile?.role === 'ADMIN',
    }),
    [profile, loading, refresh],
  );

  return <ProfileContext.Provider value={value}>{children}</ProfileContext.Provider>;
}

export function useProfile() {
  const context = useContext(ProfileContext);
  if (!context) {
    throw new Error('useProfile must be used inside a ProfileProvider');
  }
  return context;
}
