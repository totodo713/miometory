"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";

export default function Home() {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuthContext();
  const { affiliationState, memberships, selectedTenantId, isLoading: tenantLoading } = useTenantContext();

  useEffect(() => {
    if (authLoading || tenantLoading) return;

    if (!user) {
      router.replace("/login");
      return;
    }

    switch (affiliationState) {
      case "UNAFFILIATED":
        router.replace("/waiting");
        break;
      case "AFFILIATED_NO_ORG":
        router.replace("/pending-organization");
        break;
      case "FULLY_ASSIGNED":
        if (memberships.length > 1 && !selectedTenantId) {
          router.replace("/select-tenant");
        } else {
          router.replace("/worklog");
        }
        break;
      default:
        break;
    }
  }, [user, authLoading, tenantLoading, affiliationState, memberships, selectedTenantId, router]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <LoadingSpinner />
    </div>
  );
}
