"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";

export default function SelectTenantPage() {
  const t = useTranslations("selectTenant");
  const { user, isLoading: authLoading } = useAuthContext();
  const { memberships, selectTenant, isLoading: tenantLoading } = useTenantContext();
  const router = useRouter();
  const [selecting, setSelecting] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading || tenantLoading) return;
    if (!user) {
      router.replace("/login");
    }
  }, [user, authLoading, tenantLoading, router]);

  const handleSelect = async (tenantId: string) => {
    setSelecting(tenantId);
    try {
      await selectTenant(tenantId);
      router.replace("/worklog");
    } catch {
      setSelecting(null);
    }
  };

  if (authLoading || tenantLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="mx-auto w-full max-w-lg rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-2 text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mb-6 text-gray-600">{t("message")}</p>
        <div className="space-y-3">
          {memberships.map((m) => (
            <button
              key={m.tenantId}
              type="button"
              disabled={selecting !== null}
              onClick={() => handleSelect(m.tenantId)}
              className="w-full rounded-lg border border-gray-200 p-4 text-left transition hover:border-blue-500 hover:bg-blue-50 disabled:opacity-50"
            >
              <div className="font-medium text-gray-900">{m.tenantName}</div>
              <div className="mt-1 text-sm text-gray-500">
                {m.organizationName ? `${t("organization")}: ${m.organizationName}` : t("noOrganization")}
              </div>
              {selecting === m.tenantId && (
                <div className="mt-2">
                  <LoadingSpinner size="sm" />
                </div>
              )}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
