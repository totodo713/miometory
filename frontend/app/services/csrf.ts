/**
 * CSRF Token Helper
 *
 * Shared utility for extracting CSRF tokens from cookies.
 * Spring Security sets XSRF-TOKEN cookie which we need to read
 * and send back in X-XSRF-TOKEN header for non-GET requests.
 */

/**
 * Get CSRF token from cookie set by Spring Security.
 *
 * Note: This only works in browser environments (client-side).
 * For server-side requests, CSRF is typically not required.
 */
export function getCsrfToken(): string | null {
  if (typeof document === "undefined") {
    // Server-side rendering - no cookies available
    return null;
  }

  const cookies = document.cookie.split(";");
  for (const cookie of cookies) {
    const trimmed = cookie.trim();
    const eqIndex = trimmed.indexOf("=");
    if (eqIndex === -1) continue;
    const name = trimmed.substring(0, eqIndex);
    const value = trimmed.substring(eqIndex + 1);
    if (name === "XSRF-TOKEN") {
      return decodeURIComponent(value);
    }
  }
  return null;
}
