"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";
import { NotificationBell } from "./NotificationBell";

export function Header() {
  const { user, logout } = useAuthContext();
  const pathname = usePathname();
  const [hasAdminAccess, setHasAdminAccess] = useState(false);

  useEffect(() => {
    if (!user) {
      setHasAdminAccess(false);
      return;
    }
    api.admin
      .getContext()
      .then(() => setHasAdminAccess(true))
      .catch(() => setHasAdminAccess(false));
  }, [user]);

  if (!user) {
    return null;
  }

  const isAdminPage = pathname.startsWith("/admin");

  return (
    <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6">
      <div className="flex items-center gap-6">
        <Link href="/worklog" className="text-lg font-semibold text-gray-900 hover:text-gray-700">
          Miometry
        </Link>
        <nav className="flex items-center gap-1">
          <Link
            href="/worklog"
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              !isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
            }`}
          >
            勤怠
          </Link>
          {hasAdminAccess && (
            <Link
              href="/admin"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
                isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              管理
            </Link>
          )}
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <NotificationBell />
        <span className="text-sm text-gray-700">{user.displayName}</span>
        <button type="button" onClick={logout} className="text-sm text-gray-500 hover:text-gray-700">
          ログアウト
        </button>
      </div>
    </header>
  );
}
