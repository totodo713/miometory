"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";

export default function PendingOrganizationPage() {
  const t = useTranslations("pendingOrganization");
  const { user, isLoading: authLoading, logout } = useAuthContext();
  const { affiliationState, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (affiliationState && affiliationState !== "AFFILIATED_NO_ORG") {
      router.replace("/");
    }
  }, [user, authLoading, tenantLoading, affiliationState, router]);

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="mx-auto max-w-md rounded-lg bg-white p-8 shadow-md text-center">
        <div className="mb-6">
          <svg
            className="mx-auto h-16 w-16 text-amber-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <title>Building</title>
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
            />
          </svg>
        </div>
        <h1 className="mb-4 text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mb-8 text-gray-600">{t("message")}</p>
        <p className="mb-6 text-sm text-gray-400">{t("checking")}</p>
        <button
          type="button"
          onClick={logout}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {t("logout")}
        </button>
      </div>
    </div>
  );
}
