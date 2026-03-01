"use client";

import { useTranslations } from "next-intl";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { useAdminContext } from "@/providers/AdminProvider";
import { SystemSettingsSection } from "@/components/admin/SystemSettingsSection";
import { TenantSettingsSection } from "@/components/admin/TenantSettingsSection";

export default function SettingsPage() {
  const tb = useTranslations("breadcrumbs");
  const tc = useTranslations("common");
  const { hasPermission, isLoading } = useAdminContext();

  const isSystemAdmin = hasPermission("system_settings.view");

  if (isLoading) {
    return (
      <div className="p-6">
        <p className="text-gray-500">{tc("loading")}</p>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-2xl">
      <Breadcrumbs
        items={[
          { label: tb("admin"), href: "/admin" },
          { label: isSystemAdmin ? "System Settings" : "Tenant Settings" },
        ]}
      />
      {isSystemAdmin ? <SystemSettingsSection /> : <TenantSettingsSection />}
    </div>
  );
}
