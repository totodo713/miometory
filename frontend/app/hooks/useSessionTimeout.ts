"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Session timeout configuration based on FR-030:
 * - 30 minutes (1800000ms) of idle time triggers logout
 * - Warning shown at 28 minutes (1680000ms)
 * - 2 minutes countdown for user to extend session
 */
const IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
const WARNING_TIMEOUT_MS = 28 * 60 * 1000; // 28 minutes
const COUNTDOWN_DURATION_MS = 2 * 60 * 1000; // 2 minutes

/**
 * Events that should reset the idle timer.
 * Tracks user activity: mouse, keyboard, and touch interactions.
 */
const ACTIVITY_EVENTS = ["mousedown", "mousemove", "keypress", "scroll", "touchstart", "click"] as const;

export interface UseSessionTimeoutOptions {
  /** Called when warning should be shown (at 28 minutes) */
  onWarning?: () => void;
  /** Called when session times out (at 30 minutes) */
  onTimeout?: () => void;
  /** Enable/disable the timeout detection */
  enabled?: boolean;
}

export interface UseSessionTimeoutReturn {
  /** Whether the warning is currently active */
  isWarning: boolean;
  /** Remaining time in milliseconds before logout (only during warning) */
  remainingTime: number;
  /** Extends the session by resetting all timers */
  extendSession: () => void;
  /** Manually trigger logout */
  logout: () => void;
}

/**
 * Hook for detecting user idle state and managing session timeout.
 *
 * Features (FR-030):
 * - Detects user inactivity across mouse, keyboard, and touch events
 * - Shows warning at 28 minutes with 2-minute countdown
 * - Auto-logout at 30 minutes of inactivity
 * - Allows user to extend session by clicking "Continue"
 *
 * @example
 * ```tsx
 * const { isWarning, remainingTime, extendSession, logout } = useSessionTimeout({
 *   onWarning: () => setShowDialog(true),
 *   onTimeout: () => router.push('/logout'),
 *   enabled: true
 * });
 * ```
 */
export function useSessionTimeout(options: UseSessionTimeoutOptions = {}): UseSessionTimeoutReturn {
  const { onWarning, onTimeout, enabled = true } = options;

  const [isWarning, setIsWarning] = useState(false);
  const [remainingTime, setRemainingTime] = useState(COUNTDOWN_DURATION_MS);

  const warningTimerRef = useRef<NodeJS.Timeout | null>(null);
  const logoutTimerRef = useRef<NodeJS.Timeout | null>(null);
  const countdownIntervalRef = useRef<NodeJS.Timeout | null>(null);

  /**
   * Clears all active timers.
   */
  const clearTimers = useCallback(() => {
    if (warningTimerRef.current) {
      clearTimeout(warningTimerRef.current);
      warningTimerRef.current = null;
    }
    if (logoutTimerRef.current) {
      clearTimeout(logoutTimerRef.current);
      logoutTimerRef.current = null;
    }
    if (countdownIntervalRef.current) {
      clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = null;
    }
  }, []);

  /**
   * Starts the countdown timer for the warning dialog.
   */
  const startCountdown = useCallback(() => {
    const startTime = Date.now();
    const endTime = startTime + COUNTDOWN_DURATION_MS;

    // Update countdown every second
    countdownIntervalRef.current = setInterval(() => {
      const now = Date.now();
      const remaining = Math.max(0, endTime - now);
      setRemainingTime(remaining);

      if (remaining === 0) {
        if (countdownIntervalRef.current) {
          clearInterval(countdownIntervalRef.current);
          countdownIntervalRef.current = null;
        }
      }
    }, 1000);
  }, []);

  /**
   * Triggers the warning state at 28 minutes.
   */
  const handleWarning = useCallback(() => {
    setIsWarning(true);
    setRemainingTime(COUNTDOWN_DURATION_MS);
    startCountdown();
    onWarning?.();
  }, [onWarning, startCountdown]);

  /**
   * Triggers the timeout/logout at 30 minutes.
   */
  const handleTimeout = useCallback(() => {
    clearTimers();
    setIsWarning(false);
    onTimeout?.();
  }, [onTimeout, clearTimers]);

  /**
   * Resets all timers when user activity is detected.
   */
  const resetTimers = useCallback(() => {
    clearTimers();
    setIsWarning(false);
    setRemainingTime(COUNTDOWN_DURATION_MS);

    if (!enabled) return;

    // Set warning timer (28 minutes)
    warningTimerRef.current = setTimeout(() => {
      handleWarning();
    }, WARNING_TIMEOUT_MS);

    // Set logout timer (30 minutes)
    logoutTimerRef.current = setTimeout(() => {
      handleTimeout();
    }, IDLE_TIMEOUT_MS);
  }, [enabled, handleWarning, handleTimeout, clearTimers]);

  /**
   * Extends the session by resetting all timers.
   * Called when user clicks "Continue" in the warning dialog.
   */
  const extendSession = useCallback(() => {
    resetTimers();
  }, [resetTimers]);

  /**
   * Manually triggers logout.
   */
  const logout = useCallback(() => {
    handleTimeout();
  }, [handleTimeout]);

  /**
   * Activity event handler that resets timers.
   */
  const handleActivity = useCallback(() => {
    // Only reset if not already in warning state
    // During warning, user must explicitly click "Continue"
    if (!isWarning) {
      resetTimers();
    }
  }, [isWarning, resetTimers]);

  /**
   * Setup activity listeners and initial timers.
   */
  useEffect(() => {
    if (!enabled) {
      clearTimers();
      return;
    }

    // Start initial timers
    resetTimers();

    // Add activity event listeners
    ACTIVITY_EVENTS.forEach((event) => {
      window.addEventListener(event, handleActivity, { passive: true });
    });

    // Cleanup
    return () => {
      clearTimers();
      ACTIVITY_EVENTS.forEach((event) => {
        window.removeEventListener(event, handleActivity);
      });
    };
  }, [enabled, handleActivity, resetTimers, clearTimers]);

  return {
    isWarning,
    remainingTime,
    extendSession,
    logout,
  };
}
