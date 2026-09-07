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
      /**
       * A fresh confirmation link.
       *
       * <p>Links expire, and until now the only way to get another was to
       * sign up again with the same address - which does resend, but nothing
       * said so, and the form told people to sign in instead. That advice
       * fails for an unconfirmed account.
       *
       * <p>redirectTo has to be passed here too: this mints a new link, and a
       * link built without the base path lands on a 404 exactly like the
       * original did.
       */
      resendConfirmation: (email, redirectTo) =>
        supabase.auth.resend({
          type: 'signup',
          email,
          options: { emailRedirectTo: redirectTo },
        }),
      /**
       * Starts a password reset - sends the recovery email.
       *
       * <p>{@code redirectTo} has to be passed for the same reason it does
       * everywhere else here: the site lives under a base path, and a link
       * built from Supabase's Site URL alone drops it and lands on a 404.
       * Callers build it with appUrl().
       *
       * <p>Supabase answers the same way whether or not the address has an
       * account. That is deliberate on their side - it stops the form being
       * used to find out who is registered - so the caller must not report
       * "no such account" either, and cannot know it.
       */
      requestPasswordReset: (email, redirectTo) =>
        supabase.auth.resetPasswordForEmail(email, { redirectTo }),
      /**
       * Sets a new password for whoever is currently signed in.
       *
       * <p>Used after a recovery link has been exchanged for a session, which
       * is what makes this authorised - the link is the proof of identity, so
       * no current password is asked for. It also works for a signed-in member
       * changing their own password.
       */
      updatePassword: (password) => supabase.auth.updateUser({ password }),
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
