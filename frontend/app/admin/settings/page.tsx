"use client";

import { useTranslations } from "next-intl";
import { SystemSettingsSection } from "@/components/admin/SystemSettingsSection";
import { TenantSettingsSection } from "@/components/admin/TenantSettingsSection";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { useAdminContext } from "@/providers/AdminProvider";

export default function SettingsPage() {
  const tb = useTranslations("breadcrumbs");
  const tc = useTranslations("common");
  const tAd = useTranslations("accessDenied");
  const tSys = useTranslations("admin.systemSettings");
  const tTen = useTranslations("admin.tenantSettings");
  const { hasPermission, isLoading } = useAdminContext();

  const isSystemAdmin = hasPermission("system_settings.view");
  const isTenantAdmin = hasPermission("tenant_settings.view");

  if (isLoading) {
    return (
      <div className="p-6">
        <p className="text-gray-500">{tc("loading")}</p>
      </div>
    );
  }

  if (!isSystemAdmin && !isTenantAdmin) {
    return (
      <div className="p-6 max-w-2xl">
        <Breadcrumbs items={[{ label: tb("admin"), href: "/admin" }, { label: tTen("title") }]} />
        <p className="text-gray-500 mt-4">{tAd("message")}</p>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-2xl">
      <Breadcrumbs
        items={[{ label: tb("admin"), href: "/admin" }, { label: isSystemAdmin ? tSys("title") : tTen("title") }]}
      />
      {isSystemAdmin ? <SystemSettingsSection /> : <TenantSettingsSection />}
    </div>
  );
}
