"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api } from "@/services/api";

export interface AdminContext {
  role: string;
  permissions: string[];
  tenantId: string | null;
  tenantName: string | null;
  memberId: string | null;
}

interface AdminContextValue {
  adminContext: AdminContext | null;
  isLoading: boolean;
  hasPermission: (permission: string) => boolean;
}

const AdminCtx = createContext<AdminContextValue | null>(null);

export function AdminProvider({ children }: { children: React.ReactNode }) {
  const [adminContext, setAdminContext] = useState<AdminContext | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.admin
      .getContext()
      .then((ctx) => {
        if (!cancelled) {
          setAdminContext(ctx);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAdminContext(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const hasPermission = useMemo(() => {
    const permSet = new Set(adminContext?.permissions ?? []);
    return (permission: string) => permSet.has(permission);
  }, [adminContext]);

  const value = useMemo(() => ({ adminContext, isLoading, hasPermission }), [adminContext, isLoading, hasPermission]);

  return <AdminCtx.Provider value={value}>{children}</AdminCtx.Provider>;
}

export function useAdminContext(): AdminContextValue {
  const context = useContext(AdminCtx);
  if (!context) {
    throw new Error("useAdminContext must be used within an AdminProvider");
  }
  return context;
}
