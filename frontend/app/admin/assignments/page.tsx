"use client";

import { useCallback, useState } from "react";
import { AssignmentManager } from "@/components/admin/AssignmentManager";

export default function AdminAssignmentsPage() {
  const [refreshKey, setRefreshKey] = useState(0);

  const handleRefresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">アサイン管理</h1>
        <p className="mt-1 text-sm text-gray-600">メンバーとプロジェクトのアサインを管理します</p>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <AssignmentManager refreshKey={refreshKey} onRefresh={handleRefresh} />
      </div>
    </div>
  );
}
