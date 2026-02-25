"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import { ProjectForm } from "@/components/admin/ProjectForm";
import type { ProjectRow } from "@/components/admin/ProjectList";
import { ProjectList } from "@/components/admin/ProjectList";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";

export default function AdminProjectsPage() {
  const t = useTranslations("admin.projects");
  const tc = useTranslations("common");
  const tb = useTranslations("breadcrumbs");
  const toast = useToast();
  const [editingProject, setEditingProject] = useState<ProjectRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [confirmTarget, setConfirmTarget] = useState<{ id: string; action: "deactivate" | "activate" } | null>(null);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  const handleDeactivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "deactivate" });
  }, []);

  const handleActivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "activate" });
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const executeAction = useCallback(
    async (target: { id: string; action: "deactivate" | "activate" }) => {
      try {
        if (target.action === "deactivate") {
          await api.admin.projects.deactivate(target.id);
          toast.success(t("deactivated"));
        } else {
          await api.admin.projects.activate(target.id);
          toast.success(t("activated"));
        }
        refresh();
      } catch (err: unknown) {
        toast.error(err instanceof ApiError ? err.message : tc("error"));
      }
    },
    [refresh, toast],
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
      <Breadcrumbs items={[{ label: tb("admin"), href: "/admin" }, { label: tb("projects") }]} />

      <div className="flex items-center justify-between mb-6 mt-4">
        <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
        <button
          type="button"
          onClick={() => {
            setEditingProject(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          {t("createProject")}
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

      <ConfirmDialog
        open={confirmTarget !== null}
        title={tc("confirm")}
        message={confirmTarget?.action === "deactivate" ? t("confirmDeactivate") : t("confirmActivate")}
        confirmLabel={confirmTarget?.action === "deactivate" ? tc("disable") : tc("enable")}
        variant={confirmTarget?.action === "deactivate" ? "danger" : "warning"}
        onConfirm={async () => {
          if (confirmTarget) await executeAction(confirmTarget);
          setConfirmTarget(null);
        }}
        onCancel={() => setConfirmTarget(null)}
      />
    </div>
  );
}
