"use client";

import { useTranslations } from "next-intl";
import { MemberCsvImport } from "@/components/admin/MemberCsvImport";
import { AccessDenied } from "@/components/shared/AccessDenied";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { useAdminContext } from "@/providers/AdminProvider";

export default function MemberCsvImportPage() {
  const { hasPermission, isLoading } = useAdminContext();
  const tb = useTranslations("breadcrumbs");
  const t = useTranslations("admin.members.csvImport");

  if (isLoading) return null;

  if (!hasPermission("member.create")) {
    return <AccessDenied />;
  }

  return (
    <div>
      <Breadcrumbs
        items={[
          { label: tb("admin"), href: "/admin" },
          { label: tb("members"), href: "/admin/members" },
          { label: tb("csvImport") },
        ]}
      />
      <div className="mt-4">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">{t("title")}</h1>
        <MemberCsvImport />
      </div>
    </div>
  );
}
