/**
 * Authentication Hook
 *
 * Provides access to the current authenticated user's information.
 * This is a placeholder implementation that should be replaced with
 * actual authentication integration (e.g., NextAuth, Auth0, etc.)
 * when authentication is fully implemented.
 *
 * IMPORTANT: The MOCK_USER_ID is for development only and MUST be
 * replaced with real authentication before production deployment.
 *
 * Future integration options:
 * - NextAuth: import { useSession } from "next-auth/react"
 * - Auth0: import { useAuth0 } from "@auth0/auth0-react"
 * - Custom JWT: decode from cookie/localStorage
 */

import { useMemo } from "react";

// Development-only mock user ID
// TODO: SECURITY - Replace with real authentication before production
// This constant exists to make the mock user explicit and easily searchable
const DEV_MOCK_USER_ID = "00000000-0000-0000-0000-000000000001";

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
  // TODO: Replace this mock implementation with real authentication
  // Example with NextAuth:
  // const { data: session, status } = useSession();
  // return {
  //   user: session?.user ? { id: session.user.id, email: session.user.email } : null,
  //   isLoading: status === "loading",
  //   isAuthenticated: status === "authenticated",
  //   userId: session?.user?.id ?? null,
  // };

  // Development mock implementation
  // WARNING: This bypasses authentication - for development only
  const mockUser = useMemo<AuthUser>(
    () => ({
      id: DEV_MOCK_USER_ID,
      email: "dev@example.com",
      displayName: "Development User",
    }),
    [],
  );

  return {
    user: mockUser,
    isLoading: false,
    isAuthenticated: true,
    userId: mockUser.id,
  };
}
