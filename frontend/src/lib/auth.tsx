'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, onSessionChange, refreshSession, setAccessToken } from './api';
import type { LoginResponse, Me } from './types';

type AuthState = {
  user: Me | null;
  ready: boolean;
  /** Call with a LOGGED_IN response from /auth/google, /auth/otp/verify or /auth/signup/complete. */
  signedIn: (r: LoginResponse) => void;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const off = onSessionChange((r) => setUser(r && r.status === 'LOGGED_IN' ? r.user : null));
    refreshSession().finally(() => setReady(true));
    return () => {
      off();
    };
  }, []);

  const signedIn = useCallback((r: LoginResponse) => {
    if (r.status === 'LOGGED_IN') {
      setAccessToken(r.accessToken);
      setUser(r.user);
      window.dispatchEvent(new Event('cart:changed'));
    }
  }, []);

  const logout = useCallback(async () => {
    await api('/auth/logout', { method: 'POST' }).catch(() => undefined);
    setAccessToken(null);
    setUser(null);
    window.dispatchEvent(new Event('cart:changed'));
  }, []);

  const reload = useCallback(async () => {
    setUser(await api<Me>('/me'));
  }, []);

  return <AuthContext.Provider value={{ user, ready, signedIn, logout, reload }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
