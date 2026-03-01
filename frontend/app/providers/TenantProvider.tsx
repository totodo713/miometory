"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "@/services/api";
import type { TenantAffiliationState, TenantMembership } from "@/types/tenant";
import { useAuthContext } from "./AuthProvider";

const POLL_INTERVAL_MS = 30_000;
const STORAGE_KEY_TENANT_ID = "selectedTenantId";
const STORAGE_KEY_TENANT_NAME = "selectedTenantName";

function loadFromStorage(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function saveToStorage(key: string, value: string | null): void {
  try {
    if (value) {
      sessionStorage.setItem(key, value);
    } else {
      sessionStorage.removeItem(key);
    }
  } catch {
    // sessionStorage unavailable (SSR, private browsing)
  }
}

interface TenantContextType {
  affiliationState: TenantAffiliationState | null;
  memberships: TenantMembership[];
  selectedTenantId: string | null;
  selectedTenantName: string | null;
  isLoading: boolean;
  error: boolean;
  selectTenant: (tenantId: string) => Promise<void>;
  refreshStatus: () => Promise<void>;
}

const TenantContext = createContext<TenantContextType | null>(null);

export function TenantProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuthContext();
  const [affiliationState, setAffiliationState] = useState<TenantAffiliationState | null>(null);
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(() => loadFromStorage(STORAGE_KEY_TENANT_ID));
  const [selectedTenantName, setSelectedTenantName] = useState<string | null>(() =>
    loadFromStorage(STORAGE_KEY_TENANT_NAME),
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchStatus = useCallback(async () => {
    try {
      const response = await api.userStatus.getStatus();
      setAffiliationState(response.state);
      setMemberships(response.memberships);
      setError(false);
    } catch (e) {
      const status = (e as { status?: number }).status;
      if (status !== 401) {
        setError(true);
      }
    }
  }, []);

  const refreshStatus = useCallback(async () => {
    await fetchStatus();
  }, [fetchStatus]);

  const selectTenant = useCallback(
    async (tenantId: string) => {
      await api.userStatus.selectTenant(tenantId);
      const membership = memberships.find((m) => m.tenantId === tenantId);
      const tenantName = membership?.tenantName ?? null;
      setSelectedTenantId(tenantId);
      setSelectedTenantName(tenantName);
      saveToStorage(STORAGE_KEY_TENANT_ID, tenantId);
      saveToStorage(STORAGE_KEY_TENANT_NAME, tenantName);
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
      saveToStorage(STORAGE_KEY_TENANT_ID, null);
      saveToStorage(STORAGE_KEY_TENANT_NAME, null);
      setIsLoading(false);
      setError(false);
      return;
    }

    let cancelled = false;

    async function load() {
      try {
        const response = await api.userStatus.getStatus();
        if (!cancelled) {
          setAffiliationState(response.state);
          setMemberships(response.memberships);
          setError(false);
          // Auto-select for single-tenant users
          if (response.state === "FULLY_ASSIGNED" && response.memberships.length === 1) {
            const m = response.memberships[0];
            setSelectedTenantId(m.tenantId);
            setSelectedTenantName(m.tenantName);
            saveToStorage(STORAGE_KEY_TENANT_ID, m.tenantId);
            saveToStorage(STORAGE_KEY_TENANT_NAME, m.tenantName);
          }
        }
      } catch (e) {
        if (!cancelled) {
          const status = (e as { status?: number }).status;
          if (status !== 401) {
            setError(true);
          }
        }
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
      error,
      selectTenant,
      refreshStatus,
    }),
    [
      affiliationState,
      memberships,
      selectedTenantId,
      selectedTenantName,
      isLoading,
      error,
      selectTenant,
      refreshStatus,
    ],
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
