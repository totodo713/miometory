"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import { AssignmentManager } from "@/components/admin/AssignmentManager";
import { PermissionBadge } from "@/components/admin/PermissionBadge";

export default function AdminAssignmentsPage() {
  const t = useTranslations("admin.assignments");
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  return (
    <div>
      <div className="mb-6">
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
          <PermissionBadge editPermission="assignment.create" />
        </div>
        <p className="mt-1 text-sm text-gray-600">{t("description")}</p>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <AssignmentManager refreshKey={refreshKey} onRefresh={handleRefresh} />
      </div>
    </div>
  );
}
