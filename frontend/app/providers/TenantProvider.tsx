"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "@/services/api";
import type { TenantAffiliationState, TenantMembership } from "@/types/tenant";
import { useAuthContext } from "./AuthProvider";

const POLL_INTERVAL_MS = 30_000;

interface TenantContextType {
  affiliationState: TenantAffiliationState | null;
  memberships: TenantMembership[];
  selectedTenantId: string | null;
  selectedTenantName: string | null;
  isLoading: boolean;
  selectTenant: (tenantId: string) => Promise<void>;
  refreshStatus: () => Promise<void>;
}

const TenantContext = createContext<TenantContextType | null>(null);

export function TenantProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuthContext();
  const [affiliationState, setAffiliationState] = useState<TenantAffiliationState | null>(null);
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(null);
  const [selectedTenantName, setSelectedTenantName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchStatus = useCallback(async () => {
    try {
      const response = await api.userStatus.getStatus();
      setAffiliationState(response.state);
      setMemberships(response.memberships);
    } catch {
      // Silently fail — user might have been logged out
    }
  }, []);

  const refreshStatus = useCallback(async () => {
    await fetchStatus();
  }, [fetchStatus]);

  const selectTenant = useCallback(
    async (tenantId: string) => {
      await api.userStatus.selectTenant(tenantId);
      setSelectedTenantId(tenantId);
      const membership = memberships.find((m) => m.tenantId === tenantId);
      setSelectedTenantName(membership?.tenantName ?? null);
    },
    [memberships],
  );

  // Fetch status when user changes
  useEffect(() => {
    if (!user) {
      setAffiliationState(null);
      setMemberships([]);
      setSelectedTenantId(null);
      setSelectedTenantName(null);
      setIsLoading(false);
      return;
    }

    let cancelled = false;

    async function load() {
      try {
        const response = await api.userStatus.getStatus();
        if (!cancelled) {
          setAffiliationState(response.state);
          setMemberships(response.memberships);
        }
      } catch {
        // Silently fail
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    setIsLoading(true);
    load();

    return () => {
      cancelled = true;
    };
  }, [user]);

  // Polling: only when UNAFFILIATED or AFFILIATED_NO_ORG
  useEffect(() => {
    if (!user) return;

    const shouldPoll = affiliationState === "UNAFFILIATED" || affiliationState === "AFFILIATED_NO_ORG";
    if (!shouldPoll) return;

    const intervalId = setInterval(() => {
      fetchStatus();
    }, POLL_INTERVAL_MS);

    return () => {
      clearInterval(intervalId);
    };
  }, [user, affiliationState, fetchStatus]);

  const value = useMemo(
    () => ({
      affiliationState,
      memberships,
      selectedTenantId,
      selectedTenantName,
      isLoading,
      selectTenant,
      refreshStatus,
    }),
    [affiliationState, memberships, selectedTenantId, selectedTenantName, isLoading, selectTenant, refreshStatus],
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenantContext(): TenantContextType {
  const context = useContext(TenantContext);
  if (!context) {
    throw new Error("useTenantContext must be used within a TenantProvider");
  }
  return context;
}
