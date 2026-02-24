"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";
import { NotificationBell } from "./NotificationBell";

export function Header() {
  const { user, logout } = useAuthContext();
  const pathname = usePathname();
  const [hasAdminAccess, setHasAdminAccess] = useState(false);
  const isMobile = useMediaQuery("(max-width: 767px)");
  const [drawerOpen, setDrawerOpen] = useState(false);

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

  if (isMobile) {
    return (
      <>
        <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-4 fixed top-0 left-0 right-0 z-40">
          <Link href="/worklog" className="text-lg font-semibold text-gray-900">
            Miometry
          </Link>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              className="p-2 text-gray-600 hover:text-gray-800"
              aria-label="メニューを開く"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          </div>
        </header>
        {drawerOpen && (
          <>
            {/* biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click to close */}
            {/* biome-ignore lint/a11y/noStaticElementInteractions: backdrop click to close */}
            <div className="fixed inset-0 bg-black/50 z-50" onClick={() => setDrawerOpen(false)} />
            <div
              className="fixed top-0 right-0 h-full w-64 bg-white shadow-lg z-50 flex flex-col"
              role="dialog"
              aria-modal="true"
            >
              <div className="flex items-center justify-between px-4 h-14 border-b border-gray-200">
                <span className="text-sm font-medium text-gray-900">メニュー</span>
                <button
                  type="button"
                  onClick={() => setDrawerOpen(false)}
                  className="p-2 text-gray-500 hover:text-gray-700"
                  aria-label="メニューを閉じる"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <nav className="flex-1 px-4 py-4 space-y-1">
                <Link
                  href="/worklog"
                  onClick={() => setDrawerOpen(false)}
                  className={`block px-3 py-2 text-sm rounded-md ${
                    !isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
                  }`}
                >
                  勤怠
                </Link>
                {hasAdminAccess && (
                  <Link
                    href="/admin"
                    onClick={() => setDrawerOpen(false)}
                    className={`block px-3 py-2 text-sm rounded-md ${
                      isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
                    }`}
                  >
                    管理
                  </Link>
                )}
              </nav>
              <div className="px-4 py-4 border-t border-gray-200">
                <p className="text-sm text-gray-700 mb-3">{user.displayName}</p>
                <button
                  type="button"
                  onClick={() => {
                    setDrawerOpen(false);
                    logout();
                  }}
                  className="w-full text-left text-sm text-gray-500 hover:text-gray-700"
                >
                  ログアウト
                </button>
              </div>
            </div>
          </>
        )}
      </>
    );
  }

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
