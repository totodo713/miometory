"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";

export default function WaitingPage() {
  const t = useTranslations("waiting");
  const { user, isLoading: authLoading, logout } = useAuthContext();
  const { affiliationState, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (affiliationState && affiliationState !== "UNAFFILIATED") {
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
            className="mx-auto h-16 w-16 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            aria-hidden="true"
          >
            <title>Clock</title>
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
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
