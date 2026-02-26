"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { useAdminContext } from "@/providers/AdminProvider";

interface NavItem {
  href: string;
  labelKey: string;
  shortLabel: string;
  permission?: string;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/admin", labelKey: "dashboard", shortLabel: "D" },
  { href: "/admin/tenants", labelKey: "tenants", shortLabel: "T", permission: "tenant.view" },
  { href: "/admin/users", labelKey: "users", shortLabel: "U", permission: "user.view" },
  { href: "/admin/members", labelKey: "members", shortLabel: "M", permission: "member.view" },
  { href: "/admin/projects", labelKey: "projects", shortLabel: "P", permission: "project.view" },
  { href: "/admin/assignments", labelKey: "assignments", shortLabel: "A", permission: "assignment.view" },
  { href: "/admin/master-data", labelKey: "masterData", shortLabel: "MD", permission: "master_data.view" },
  { href: "/admin/organizations", labelKey: "organizations", shortLabel: "O", permission: "organization.view" },
  { href: "/admin/settings", labelKey: "settings", shortLabel: "S", permission: "system_settings.view" },
];

export function AdminNav() {
  const t = useTranslations("admin.nav");
  const { hasPermission, adminContext } = useAdminContext();
  const pathname = usePathname();
  const isMobile = useMediaQuery("(max-width: 767px)");
  const isTablet = useMediaQuery("(min-width: 768px) and (max-width: 1023px)");
  const [isOpen, setIsOpen] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const drawerRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const closeDrawer = useCallback(() => {
    setIsOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  }, []);

  useEffect(() => {
    if (!isOpen) return;
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
  }, [isOpen, closeDrawer]);

  const visibleItems = NAV_ITEMS.filter((item) => !item.permission || hasPermission(item.permission));

  const navContent = (collapsed: boolean) => (
    <>
      <div className="p-4 border-b border-gray-200">
        {collapsed ? (
          <p className="text-xs font-medium text-gray-500 text-center">{t("sectionLabels.management")}</p>
        ) : (
          <>
            <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              {t("sectionLabels.management")}
            </p>
            {adminContext && (
              <>
                <p className="mt-1 text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>
                {adminContext.tenantName && <p className="text-xs text-gray-500 truncate">{adminContext.tenantName}</p>}
              </>
            )}
          </>
        )}
      </div>
      <ul className="py-2">
        {visibleItems.map((item) => {
          const isActive = item.href === "/admin" ? pathname === "/admin" : pathname.startsWith(item.href);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                onClick={() => isMobile && setIsOpen(false)}
                className={`block px-4 py-2.5 text-sm transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-700 font-medium border-r-2 border-blue-700"
                    : "text-gray-700 hover:bg-gray-50"
                }`}
                title={collapsed ? t(item.labelKey) : undefined}
              >
                {collapsed ? (
                  <span className="flex items-center justify-center w-8 h-5 text-xs font-bold">{item.shortLabel}</span>
                ) : (
                  t(item.labelKey)
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );

  // Mobile: hamburger button + slide-in drawer
  if (isMobile) {
    return (
      <>
        <button
          type="button"
          ref={triggerRef}
          onClick={() => setIsOpen(true)}
          className="fixed top-16 left-3 z-30 p-2 bg-white border border-gray-200 rounded-md shadow-sm"
          aria-label={t("sectionLabels.management")}
        >
          <svg
            className="w-5 h-5 text-gray-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>

        {isOpen && (
          <>
            {/* biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click to close */}
            {/* biome-ignore lint/a11y/noStaticElementInteractions: backdrop click to close */}
            <div className="fixed inset-0 bg-black/50 z-40" onClick={closeDrawer} />
            <nav
              ref={drawerRef}
              className="fixed inset-y-0 left-0 w-64 bg-white shadow-xl z-50 transform transition-transform"
              role="dialog"
              aria-modal="true"
              aria-label={t("sectionLabels.management")}
            >
              <div className="flex items-center justify-between p-4 border-b border-gray-200">
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t("sectionLabels.management")}
                </p>
                <button
                  type="button"
                  ref={closeButtonRef}
                  onClick={closeDrawer}
                  className="p-1 text-gray-400 hover:text-gray-600"
                  aria-label={t("sectionLabels.management")}
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              {adminContext && (
                <div className="px-4 py-2 border-b border-gray-200">
                  <p className="text-sm text-gray-700 truncate">{adminContext.role.replace(/_/g, " ")}</p>
                  {adminContext.tenantName && (
                    <p className="text-xs text-gray-500 truncate">{adminContext.tenantName}</p>
                  )}
                </div>
              )}
              <ul className="py-2">
                {visibleItems.map((item) => {
                  const isActive = item.href === "/admin" ? pathname === "/admin" : pathname.startsWith(item.href);
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        onClick={() => setIsOpen(false)}
                        className={`block px-4 py-2.5 text-sm transition-colors ${
                          isActive
                            ? "bg-blue-50 text-blue-700 font-medium border-r-2 border-blue-700"
                            : "text-gray-700 hover:bg-gray-50"
                        }`}
                      >
                        {t(item.labelKey)}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </nav>
          </>
        )}
      </>
    );
  }

  // Tablet: collapsed sidebar with hover expand
  if (isTablet) {
    const expanded = isHovered;
    return (
      <nav
        className={`${expanded ? "w-64" : "w-16"} bg-white border-r border-gray-200 min-h-full transition-all duration-200`}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      >
        {navContent(!expanded)}
      </nav>
    );
  }

  // Desktop: full sidebar
  return <nav className="w-60 bg-white border-r border-gray-200 min-h-full">{navContent(false)}</nav>;
}
