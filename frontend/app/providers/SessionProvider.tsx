"use client";

import { useState } from "react";
import { SessionTimeoutDialog } from "../components/shared/SessionTimeoutDialog";
import { useSessionTimeout } from "../hooks/useSessionTimeout";
import { useAuthContext } from "./AuthProvider";

/**
 * Session timeout provider that wraps the application.
 * Manages session timeout detection and displays warning dialog.
 *
 * Features (FR-030):
 * - Detects 30 minutes of user inactivity
 * - Shows warning at 28 minutes
 * - Displays 2-minute countdown
 * - Auto-logout on timeout
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuthContext();
  const [showDialog, setShowDialog] = useState(false);

  const handleWarning = () => {
    setShowDialog(true);
  };

  const handleTimeout = () => {
    setShowDialog(false);
    logout();
  };

  const {
    isWarning,
    remainingTime,
    extendSession,
    logout: sessionLogout,
  } = useSessionTimeout({
    onWarning: handleWarning,
    onTimeout: handleTimeout,
    enabled: user !== null,
  });

  const handleContinue = () => {
    setShowDialog(false);
    extendSession();
  };

  const handleLogout = () => {
    setShowDialog(false);
    sessionLogout();
  };

  return (
    <>
      {children}
      <SessionTimeoutDialog
        isOpen={showDialog && isWarning}
        remainingTime={remainingTime}
        onContinue={handleContinue}
        onLogout={handleLogout}
      />
    </>
  );
}
