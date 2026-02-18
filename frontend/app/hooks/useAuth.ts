/**
 * Authentication Hook
 *
 * Thin wrapper around AuthProvider's useAuthContext().
 * Provides backward-compatible interface for existing consumers.
 */

import { useAuthContext } from "@/providers/AuthProvider";

export interface AuthUser {
  id: string;
  email?: string;
  displayName?: string;
}

export interface UseAuthResult {
  /** Current authenticated user, or null if not authenticated */
  user: AuthUser | null;
  /** Whether authentication is still loading */
  isLoading: boolean;
  /** Whether the user is authenticated */
  isAuthenticated: boolean;
  /** Current user's ID (convenience accessor) */
  userId: string | null;
}

/**
 * Hook to access current authenticated user information.
 *
 * Usage:
 * ```tsx
 * const { user, userId, isAuthenticated } = useAuth();
 * if (!isAuthenticated) return <LoginRedirect />;
 * ```
 *
 * @returns Authentication state and user information
 */
export function useAuth(): UseAuthResult {
  const { user, isLoading } = useAuthContext();

  return {
    user,
    isLoading,
    isAuthenticated: user !== null,
    userId: user?.id ?? null,
  };
}
