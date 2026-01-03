"use client";

import { useEffect, useState } from "react";

export interface SessionTimeoutDialogProps {
  /** Whether the dialog is visible */
  isOpen: boolean;
  /** Remaining time in milliseconds */
  remainingTime: number;
  /** Called when user clicks "Continue" to extend session */
  onContinue: () => void;
  /** Called when user clicks "Logout" or time runs out */
  onLogout: () => void;
}

/**
 * Session timeout warning dialog (FR-030).
 *
 * Displays at 28 minutes of inactivity with a 2-minute countdown.
 * User can extend session or logout immediately.
 *
 * Requirements:
 * - Show countdown timer in MM:SS format
 * - "Continue" button to extend session
 * - "Logout" button for immediate logout
 * - Auto-logout when countdown reaches 0
 */
export function SessionTimeoutDialog({
  isOpen,
  remainingTime,
  onContinue,
  onLogout,
}: SessionTimeoutDialogProps) {
  const [displayTime, setDisplayTime] = useState("");

  // Format remaining time as MM:SS
  useEffect(() => {
    const totalSeconds = Math.ceil(remainingTime / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    setDisplayTime(`${minutes}:${seconds.toString().padStart(2, "0")}`);

    // Auto-logout when time runs out
    if (remainingTime === 0) {
      onLogout();
    }
  }, [remainingTime, onLogout]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
        {/* Header */}
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-shrink-0 w-12 h-12 rounded-full bg-yellow-100 flex items-center justify-center">
            <svg
              className="w-6 h-6 text-yellow-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-label="Warning icon"
            >
              <title>Warning</title>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          </div>
          <div>
            <h2 className="text-xl font-semibold text-gray-900">
              Session Timeout Warning
            </h2>
            <p className="text-sm text-gray-500">
              Your session is about to expire
            </p>
          </div>
        </div>

        {/* Countdown */}
        <div className="mb-6 text-center">
          <p className="text-gray-700 mb-2">
            Your session will expire due to inactivity.
          </p>
          <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
            <p className="text-sm text-gray-600 mb-1">Time remaining:</p>
            <p className="text-4xl font-bold text-gray-900 tabular-nums">
              {displayTime}
            </p>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-3">
          <button
            type="button"
            onClick={onLogout}
            className="flex-1 px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-500 transition-colors"
          >
            Logout
          </button>
          <button
            type="button"
            onClick={onContinue}
            className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors font-medium"
          >
            Continue Session
          </button>
        </div>

        {/* Additional info */}
        <p className="mt-4 text-xs text-gray-500 text-center">
          Click "Continue Session" to extend your session, or "Logout" to sign
          out now.
        </p>
      </div>
    </div>
  );
}
