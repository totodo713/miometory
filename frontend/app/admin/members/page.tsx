"use client";

import { useCallback, useState } from "react";
import { MemberForm } from "@/components/admin/MemberForm";
import type { MemberRow } from "@/components/admin/MemberList";
import { MemberList } from "@/components/admin/MemberList";
import { ApiError, api } from "@/services/api";

export default function AdminMembersPage() {
  const [editingMember, setEditingMember] = useState<MemberRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  const handleDeactivate = useCallback(
    async (id: string) => {
      if (!confirm("このメンバーを無効化しますか？")) return;
      try {
        await api.admin.members.deactivate(id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );

  const handleActivate = useCallback(
    async (id: string) => {
      try {
        await api.admin.members.activate(id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );

  const handleEdit = useCallback((member: MemberRow) => {
    setEditingMember(member);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingMember(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingMember(null);
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">メンバー管理</h1>
        <button
          type="button"
          onClick={() => {
            setEditingMember(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          メンバー招待
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <MemberList
          onEdit={handleEdit}
          onDeactivate={handleDeactivate}
          onActivate={handleActivate}
          refreshKey={refreshKey}
        />
      </div>

      {showForm && <MemberForm member={editingMember} onClose={handleClose} onSaved={handleSaved} />}
    </div>
  );
}
