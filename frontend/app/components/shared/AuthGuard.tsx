"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { LoadingSpinner } from "./LoadingSpinner";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuthContext();
  const { affiliationState, memberships, selectedTenantId, isLoading: tenantLoading } = useTenantContext();

  useEffect(() => {
    if (authLoading || tenantLoading) return;

    if (!user) {
      router.replace("/login");
      return;
    }

    if (affiliationState === "UNAFFILIATED") {
      router.replace("/waiting");
      return;
    }

    if (affiliationState === "AFFILIATED_NO_ORG") {
      router.replace("/pending-organization");
      return;
    }

    if (affiliationState === "FULLY_ASSIGNED" && memberships.length > 1 && !selectedTenantId) {
      router.replace("/select-tenant");
      return;
    }
  }, [user, authLoading, tenantLoading, affiliationState, memberships, selectedTenantId, router]);

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  if (!user || affiliationState !== "FULLY_ASSIGNED") {
    return <LoadingSpinner />;
  }

  if (memberships.length > 1 && !selectedTenantId) {
    return <LoadingSpinner />;
  }

  return <>{children}</>;
}
