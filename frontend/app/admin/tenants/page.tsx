"use client";

import { useCallback, useState } from "react";
import { TenantForm } from "@/components/admin/TenantForm";
import type { TenantRow } from "@/components/admin/TenantList";
import { TenantList } from "@/components/admin/TenantList";
import { api } from "@/services/api";

export default function AdminTenantsPage() {
  const [editingTenant, setEditingTenant] = useState<TenantRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  const handleDeactivate = useCallback(
    async (id: string) => {
      if (!confirm("このテナントを無効化しますか？")) return;
      await api.admin.tenants.deactivate(id);
      refresh();
    },
    [refresh],
  );

  const handleActivate = useCallback(
    async (id: string) => {
      await api.admin.tenants.activate(id);
      refresh();
    },
    [refresh],
  );

  const handleEdit = useCallback((tenant: TenantRow) => {
    setEditingTenant(tenant);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingTenant(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingTenant(null);
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">テナント管理</h1>
        <button
          type="button"
          onClick={() => {
            setEditingTenant(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          テナント作成
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <TenantList
          onEdit={handleEdit}
          onDeactivate={handleDeactivate}
          onActivate={handleActivate}
          refreshKey={refreshKey}
        />
      </div>

      {showForm && <TenantForm tenant={editingTenant} onClose={handleClose} onSaved={handleSaved} />}
    </div>
  );
}
