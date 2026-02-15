"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { SessionTimeoutDialog } from "../components/shared/SessionTimeoutDialog";
import { useSessionTimeout } from "../hooks/useSessionTimeout";

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
  const router = useRouter();
  const [showDialog, setShowDialog] = useState(false);

  const handleWarning = () => {
    setShowDialog(true);
  };

  const handleTimeout = () => {
    setShowDialog(false);
    // Redirect to logout endpoint
    router.push("/api/auth/logout");
  };

  const { isWarning, remainingTime, extendSession, logout } = useSessionTimeout({
    onWarning: handleWarning,
    onTimeout: handleTimeout,
    enabled: true,
  });

  const handleContinue = () => {
    setShowDialog(false);
    extendSession();
  };

  const handleLogout = () => {
    setShowDialog(false);
    logout();
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
