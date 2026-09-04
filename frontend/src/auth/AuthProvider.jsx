import { createContext, useContext, useEffect, useMemo, useState } from 'react';

import { supabase, isSupabaseConfigured } from '../lib/supabase.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(isSupabaseConfigured);

  useEffect(() => {
    if (!isSupabaseConfigured) return undefined;

    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setLoading(false);
    });

    // Fires on sign-in, sign-out and silent token refreshes.
    const { data: subscription } = supabase.auth.onAuthStateChange((_event, next) => {
      setSession(next);
    });

    return () => subscription.subscription.unsubscribe();
  }, []);

  const value = useMemo(
    () => ({
      session,
      user: session?.user ?? null,
      loading,
      configured: isSupabaseConfigured,
      signIn: (email, password) => supabase.auth.signInWithPassword({ email, password }),
      /**
       * Both names land in Supabase user metadata; the API seeds its own row
       * from them on the first authenticated request.
       *
       * <p>{@code redirectTo} is where the confirmation link comes back to.
       * Passed explicitly rather than left to Supabase's Site URL: the site
       * lives under a base path, and a Site URL missing it sends every
       * confirmed member to the bare github.io root, which serves a 404. The
       * caller builds it with appUrl(), so the base path cannot be forgotten.
       */
      signUp: (email, password, displayName, username, redirectTo) =>
        supabase.auth.signUp({
          email,
          password,
          options: {
            data: { display_name: displayName, username },
            emailRedirectTo: redirectTo,
          },
        }),
      signOut: () => supabase.auth.signOut(),
    }),
    [session, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
