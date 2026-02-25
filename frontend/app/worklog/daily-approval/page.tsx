"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import { DailyApprovalDashboard } from "@/components/admin/DailyApprovalDashboard";

export default function DailyApprovalPage() {
  const t = useTranslations("admin.dailyApproval");
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
        <p className="mt-1 text-sm text-gray-600">{t("title")}</p>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <DailyApprovalDashboard refreshKey={refreshKey} onRefresh={handleRefresh} />
      </div>
    </div>
  );
}
