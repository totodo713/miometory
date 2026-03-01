"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTenantContext } from "@/providers/TenantProvider";

export function TenantSwitcher() {
  const t = useTranslations("header");
  const { memberships, selectedTenantId, selectedTenantName, selectTenant } = useTenantContext();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
      setIsOpen(false);
    }
  }, []);

  const handleEscape = useCallback((e: KeyboardEvent) => {
    if (e.key === "Escape") setIsOpen(false);
  }, []);

  useEffect(() => {
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleEscape);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isOpen, handleClickOutside, handleEscape]);

  // Only render for multi-tenant users
  if (memberships.length <= 1) return null;

  const handleSelect = async (tenantId: string) => {
    setIsOpen(false);
    if (tenantId === selectedTenantId) return;
    await selectTenant(tenantId);
    window.location.reload();
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-1 rounded-md px-2 py-1 text-sm text-gray-700 hover:bg-gray-100"
        aria-label={t("switchTenant")}
      >
        <span className="max-w-[120px] truncate">{selectedTenantName}</span>
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {isOpen && (
        <div className="absolute right-0 z-50 mt-1 w-56 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5">
          <div className="py-1" role="menu">
            {memberships.map((m) => (
              <button
                key={m.tenantId}
                type="button"
                role="menuitem"
                onClick={() => handleSelect(m.tenantId)}
                className={`w-full px-4 py-2 text-left text-sm hover:bg-gray-100 ${
                  m.tenantId === selectedTenantId ? "bg-blue-50 font-medium text-blue-700" : "text-gray-700"
                }`}
              >
                {m.tenantName}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
