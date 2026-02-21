"use client";

import { useCallback, useState } from "react";
import { DailyApprovalDashboard } from "@/components/admin/DailyApprovalDashboard";

export default function DailyApprovalPage() {
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">日次承認</h1>
        <p className="mt-1 text-sm text-gray-600">チームメンバーの日次記録を承認・差戻します</p>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <DailyApprovalDashboard refreshKey={refreshKey} onRefresh={handleRefresh} />
      </div>
    </div>
  );
}
