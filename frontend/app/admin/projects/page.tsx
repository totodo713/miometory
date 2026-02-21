"use client";

import { useCallback, useState } from "react";
import { ProjectForm } from "@/components/admin/ProjectForm";
import type { ProjectRow } from "@/components/admin/ProjectList";
import { ProjectList } from "@/components/admin/ProjectList";
import { ApiError, api } from "@/services/api";

export default function AdminProjectsPage() {
  const [editingProject, setEditingProject] = useState<ProjectRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  const handleDeactivate = useCallback(
    async (id: string) => {
      if (!confirm("このプロジェクトを無効化しますか？")) return;
      try {
        await api.admin.projects.deactivate(id);
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
        await api.admin.projects.activate(id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );

  const handleEdit = useCallback((project: ProjectRow) => {
    setEditingProject(project);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingProject(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingProject(null);
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">プロジェクト管理</h1>
        <button
          type="button"
          onClick={() => {
            setEditingProject(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          プロジェクト作成
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <ProjectList
          onEdit={handleEdit}
          onDeactivate={handleDeactivate}
          onActivate={handleActivate}
          refreshKey={refreshKey}
        />
      </div>

      {showForm && <ProjectForm project={editingProject} onClose={handleClose} onSaved={handleSaved} />}
    </div>
  );
}
