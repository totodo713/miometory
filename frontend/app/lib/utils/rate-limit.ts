/**
 * Client-side rate limiting utility for password reset requests
 * 
 * Implements sliding window algorithm with localStorage persistence.
 * Prevents abuse by limiting password reset requests to 3 per 5 minutes.
 * 
 * Storage Strategy:
 * - Uses localStorage with key 'password_reset_rate_limit'
 * - Stores array of attempt timestamps (milliseconds since epoch)
 * - Syncs across tabs via Storage Event API
 * 
 * Algorithm:
 * - Sliding window: removes attempts older than 5 minutes
 * - Limit: 3 requests per 5 minutes (300,000ms)
 * - Clock adjustment detection: ignores future timestamps
 * 
 * @see specs/005-password-reset-frontend/research.md (Question 2)
 */

import type { RateLimitState } from '../types/password-reset';

/** Storage key for rate limit data */
const STORAGE_KEY = 'password_reset_rate_limit';

/** Maximum allowed requests per time window */
const MAX_REQUESTS = 3;

/** Time window in milliseconds (5 minutes) */
const TIME_WINDOW_MS = 5 * 60 * 1000; // 300,000ms

/**
 * Check if user is allowed to make a password reset request
 * 
 * @returns Rate limit state with isAllowed flag and remaining attempts
 * 
 * @example
 * ```ts
 * const state = checkRateLimit();
 * if (!state.isAllowed) {
 *   alert(`Too many requests. Try again in ${state.resetTime}`);
 *   return;
 * }
 * // Proceed with API request
 * ```
 */
export function checkRateLimit(): RateLimitState {
  // SSR safety check
  if (typeof window === 'undefined') {
    return {
      attempts: [],
      isAllowed: true,
      remainingAttempts: MAX_REQUESTS,
      resetTime: null,
    };
  }

  const now = Date.now();
  const attempts = getAttempts();

  // Remove expired attempts (sliding window)
  const validAttempts = attempts.filter((timestamp) => {
    // Ignore future timestamps (clock adjustment detection)
    if (timestamp > now) {
      return false;
    }
    return now - timestamp < TIME_WINDOW_MS;
  });

  // Save cleaned attempts
  saveAttempts(validAttempts);

  const remainingAttempts = Math.max(0, MAX_REQUESTS - validAttempts.length);
  const isAllowed = validAttempts.length < MAX_REQUESTS;

  // Calculate reset time (oldest attempt + time window)
  let resetTime: number | null = null;
  if (!isAllowed && validAttempts.length > 0) {
    const oldestAttempt = Math.min(...validAttempts);
    resetTime = oldestAttempt + TIME_WINDOW_MS;
  }

  return {
    attempts: validAttempts,
    isAllowed,
    remainingAttempts,
    resetTime,
  };
}

/**
 * Record a new password reset request attempt
 * 
 * Call this function after successfully submitting a password reset request.
 * Does not check if allowed - call checkRateLimit() first.
 * 
 * @example
 * ```ts
 * const state = checkRateLimit();
 * if (state.isAllowed) {
 *   await api.requestPasswordReset(email);
 *   recordAttempt(); // Record after successful request
 * }
 * ```
 */
export function recordAttempt(): void {
  // SSR safety check
  if (typeof window === 'undefined') {
    return;
  }

  const now = Date.now();
  const attempts = getAttempts();
  attempts.push(now);
  saveAttempts(attempts);
}

/**
 * Get all recorded attempts from localStorage
 * 
 * @returns Array of attempt timestamps (milliseconds since epoch)
 */
function getAttempts(): number[] {
  try {
    const data = localStorage.getItem(STORAGE_KEY);
    if (!data) {
      return [];
    }
    const parsed = JSON.parse(data);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((item) => typeof item === 'number');
  } catch (error) {
    // Invalid JSON or quota exceeded
    console.error('Failed to read rate limit data:', error);
    return [];
  }
}

/**
 * Save attempts to localStorage
 * 
 * @param attempts - Array of attempt timestamps
 */
function saveAttempts(attempts: number[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(attempts));
  } catch (error) {
    // Quota exceeded or storage disabled
    console.error('Failed to save rate limit data:', error);
  }
}

/**
 * Clear all recorded attempts (for testing/debugging)
 * 
 * @example
 * ```ts
 * // In dev tools console:
 * import { clearAttempts } from '@/lib/utils/rate-limit';
 * clearAttempts();
 * ```
 */
export function clearAttempts(): void {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch (error) {
    console.error('Failed to clear rate limit data:', error);
  }
}

/**
 * Calculate minutes until rate limit resets
 * 
 * @param resetTime - Timestamp when rate limit resets (milliseconds since epoch)
 * @returns Number of minutes until reset (rounded up)
 * 
 * @example
 * ```ts
 * const state = checkRateLimit();
 * if (!state.isAllowed && state.resetTime) {
 *   const minutes = getMinutesUntilReset(state.resetTime);
 *   alert(`Try again in ${minutes} minute(s)`);
 * }
 * ```
 */
export function getMinutesUntilReset(resetTime: number): number {
  const now = Date.now();
  const diffMs = Math.max(0, resetTime - now);
  return Math.ceil(diffMs / 60000); // Round up to nearest minute
}

/**
 * Set up cross-tab synchronization for rate limit state
 * 
 * Call this in a React useEffect to listen for storage changes from other tabs.
 * 
 * @param onUpdate - Callback when rate limit state changes in another tab
 * @returns Cleanup function to remove event listener
 * 
 * @example
 * ```ts
 * useEffect(() => {
 *   const cleanup = setupStorageListener(() => {
 *     // Re-check rate limit when storage changes
 *     const newState = checkRateLimit();
 *     setRateLimitState(newState);
 *   });
 *   return cleanup;
 * }, []);
 * ```
 */
export function setupStorageListener(onUpdate: () => void): () => void {
  // SSR safety check
  if (typeof window === 'undefined') {
    return () => {};
  }

  const handleStorageChange = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) {
      onUpdate();
    }
  };

  window.addEventListener('storage', handleStorageChange);

  return () => {
    window.removeEventListener('storage', handleStorageChange);
  };
}
