"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AUTH_UNAUTHORIZED_EVENT, api } from "@/services/api";

const STORAGE_KEY = "miometry_auth_user";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  memberId?: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  login: (email: string, password: string, rememberMe: boolean) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restore user from sessionStorage after hydration to avoid SSR/CSR mismatch
  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (stored) {
        setUser(JSON.parse(stored));
      }
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
    setIsLoading(false);
  }, []);

  // Listen for 401 unauthorized events from API client
  useEffect(() => {
    function handleUnauthorized() {
      setUser(null);
      sessionStorage.removeItem(STORAGE_KEY);
      // Navigation to /login is handled by AuthGuard on protected pages.
      // Calling router.replace here races with batched state updates,
      // causing an infinite redirect loop (login→worklog→login→…).
    }
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, []);

  const login = useCallback(async (email: string, password: string, rememberMe: boolean) => {
    const response = await api.auth.login({ email, password, rememberMe });
    const authUser: AuthUser = {
      id: response.user.id,
      email: response.user.email,
      displayName: response.user.name,
      memberId: response.user.memberId ?? undefined,
    };
    setUser(authUser);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(authUser));
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.auth.logout();
    } catch {
      // Ignore logout API errors — always clear local state
    }
    setUser(null);
    sessionStorage.removeItem(STORAGE_KEY);
    router.replace("/login");
  }, [router]);

  const value = useMemo(() => ({ user, isLoading, login, logout }), [user, isLoading, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuthContext(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuthContext must be used within an AuthProvider");
  }
  return context;
}
