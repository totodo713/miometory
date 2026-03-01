"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { useAuthContext } from "@/providers/AuthProvider";
import { api } from "@/services/api";
import { LocaleToggle } from "./LocaleToggle";
import { NotificationBell } from "./NotificationBell";
import { TenantSwitcher } from "./TenantSwitcher";

export function Header() {
  const { user, logout } = useAuthContext();
  const pathname = usePathname();
  const t = useTranslations("header");
  const [hasAdminAccess, setHasAdminAccess] = useState(false);
  const isMobile = useMediaQuery("(max-width: 767px)");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const drawerRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const closeDrawer = useCallback(() => {
    setDrawerOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  }, []);

  useEffect(() => {
    if (!drawerOpen) return;
    closeButtonRef.current?.focus();
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        closeDrawer();
        return;
      }
      if (e.key === "Tab" && drawerRef.current) {
        const focusable = drawerRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [drawerOpen, closeDrawer]);

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
              ref={triggerRef}
              onClick={() => setDrawerOpen(true)}
              className="p-2 text-gray-600 hover:text-gray-800"
              aria-label={t("openMenu")}
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
            <div className="fixed inset-0 bg-black/50 z-50" onClick={closeDrawer} />
            <div
              ref={drawerRef}
              className="fixed top-0 right-0 h-full w-64 bg-white shadow-lg z-50 flex flex-col"
              role="dialog"
              aria-modal="true"
              aria-label={t("navigationMenu")}
            >
              <div className="flex items-center justify-between px-4 h-14 border-b border-gray-200">
                <span className="text-sm font-medium text-gray-900">{t("menu")}</span>
                <button
                  type="button"
                  ref={closeButtonRef}
                  onClick={closeDrawer}
                  className="p-2 text-gray-500 hover:text-gray-700"
                  aria-label={t("closeMenu")}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <nav className="flex-1 px-4 py-4 space-y-1">
                <Link
                  href="/worklog"
                  onClick={closeDrawer}
                  className={`block px-3 py-2 text-sm rounded-md ${
                    !isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
                  }`}
                >
                  {t("worklog")}
                </Link>
                {hasAdminAccess && (
                  <Link
                    href="/admin"
                    onClick={closeDrawer}
                    className={`block px-3 py-2 text-sm rounded-md ${
                      isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
                    }`}
                  >
                    {t("admin")}
                  </Link>
                )}
              </nav>
              <div className="px-4 py-4 border-t border-gray-200 space-y-3">
                <TenantSwitcher />
                <LocaleToggle />
                <p className="text-sm text-gray-700">{user.displayName}</p>
                <button
                  type="button"
                  onClick={() => {
                    closeDrawer();
                    logout();
                  }}
                  className="w-full text-left text-sm text-gray-500 hover:text-gray-700"
                >
                  {t("logout")}
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
            {t("worklog")}
          </Link>
          {hasAdminAccess && (
            <Link
              href="/admin"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
                isAdminPage ? "bg-gray-100 text-gray-900 font-medium" : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              {t("admin")}
            </Link>
          )}
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <TenantSwitcher />
        <NotificationBell />
        <LocaleToggle />
        <span className="text-sm text-gray-700">{user.displayName}</span>
        <button type="button" onClick={logout} className="text-sm text-gray-500 hover:text-gray-700">
          {t("logout")}
        </button>
      </div>
    </header>
  );
}
