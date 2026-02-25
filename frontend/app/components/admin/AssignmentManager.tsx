"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { useAdminContext } from "@/providers/AdminProvider";
import { ApiError, api } from "@/services/api";

interface AssignmentRow {
  id: string;
  memberId: string;
  memberName: string;
  memberEmail: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  isActive: boolean;
  assignedAt: string;
}

interface MemberOption {
  id: string;
  displayName: string;
  email: string;
}

interface ProjectOption {
  id: string;
  code: string;
  name: string;
}

type ViewMode = "by-member" | "by-project";

interface AssignmentManagerProps {
  refreshKey: number;
  onRefresh: () => void;
}

export function AssignmentManager({ refreshKey, onRefresh }: AssignmentManagerProps) {
  const t = useTranslations("admin.assignments");
  const tc = useTranslations("common");
  const { adminContext } = useAdminContext();
  const isSupervisor = adminContext?.role === "SUPERVISOR";

  const [viewMode, setViewMode] = useState<ViewMode>("by-member");
  const [members, setMembers] = useState<MemberOption[]>([]);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [selectedMemberId, setSelectedMemberId] = useState<string>("");
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [assignments, setAssignments] = useState<AssignmentRow[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Add assignment form state
  const [addMemberId, setAddMemberId] = useState("");
  const [addProjectId, setAddProjectId] = useState("");
  const [isAdding, setIsAdding] = useState(false);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    api.admin.members
      .list({ size: 1000, isActive: true })
      .then((result) => {
        setMembers(result.content.map((m) => ({ id: m.id, displayName: m.displayName, email: m.email })));
      })
      .catch(() => {});
  }, [refreshKey]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    api.admin.projects
      .list({ size: 1000, isActive: true })
      .then((result) => {
        setProjects(result.content.map((p) => ({ id: p.id, code: p.code, name: p.name })));
      })
      .catch(() => {});
  }, [refreshKey]);

  // Load assignments when selection changes
  const loadAssignments = useCallback(async () => {
    if (viewMode === "by-member" && !selectedMemberId) {
      setAssignments([]);
      return;
    }
    if (viewMode === "by-project" && !selectedProjectId) {
      setAssignments([]);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      if (viewMode === "by-member") {
        const result = await api.admin.assignments.listByMember(selectedMemberId);
        setAssignments(result);
      } else {
        const result = await api.admin.assignments.listByProject(selectedProjectId);
        setAssignments(result);
      }
    } catch {
      setAssignments([]);
    } finally {
      setIsLoading(false);
    }
  }, [viewMode, selectedMemberId, selectedProjectId]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadAssignments();
  }, [loadAssignments, refreshKey]);

  const handleAdd = async () => {
    const memberId = viewMode === "by-member" ? selectedMemberId : addMemberId;
    const projectId = viewMode === "by-project" ? selectedProjectId : addProjectId;

    if (!memberId || !projectId) return;

    setIsAdding(true);
    setError(null);
    try {
      await api.admin.assignments.create({ memberId, projectId });
      setAddMemberId("");
      setAddProjectId("");
      onRefresh();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(tc("error"));
      }
    } finally {
      setIsAdding(false);
    }
  };

  const handleToggle = async (assignment: AssignmentRow) => {
    setError(null);
    try {
      if (assignment.isActive) {
        await api.admin.assignments.deactivate(assignment.id);
      } else {
        await api.admin.assignments.activate(assignment.id);
      }
      onRefresh();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(tc("error"));
      }
    }
  };

  return (
    <div className="space-y-4">
      {/* View mode toggle */}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setViewMode("by-member")}
          className={`px-4 py-2 text-sm rounded-md ${
            viewMode === "by-member"
              ? "bg-blue-600 text-white"
              : "bg-white text-gray-700 border border-gray-300 hover:bg-gray-50"
          }`}
        >
          {t("byMember")}
        </button>
        {!isSupervisor && (
          <button
            type="button"
            onClick={() => setViewMode("by-project")}
            className={`px-4 py-2 text-sm rounded-md ${
              viewMode === "by-project"
                ? "bg-blue-600 text-white"
                : "bg-white text-gray-700 border border-gray-300 hover:bg-gray-50"
            }`}
          >
            {t("byProject")}
          </button>
        )}
      </div>

      {/* Selection dropdown */}
      <div className="flex items-end gap-4">
        {viewMode === "by-member" ? (
          <div className="flex-1">
            <label htmlFor="select-member" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.selectMember")}
            </label>
            <select
              id="select-member"
              value={selectedMemberId}
              onChange={(e) => setSelectedMemberId(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">{t("selectLabel")}</option>
              {members.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName} ({m.email})
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div className="flex-1">
            <label htmlFor="select-project" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.selectProject")}
            </label>
            <select
              id="select-project"
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">{t("selectLabel")}</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.code} - {p.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Add assignment form */}
      {((viewMode === "by-member" && selectedMemberId) || (viewMode === "by-project" && selectedProjectId)) && (
        <div className="flex items-end gap-3 p-3 bg-gray-50 rounded-md">
          {viewMode === "by-member" ? (
            <div className="flex-1">
              <label htmlFor="add-project" className="block text-sm font-medium text-gray-700 mb-1">
                {t("addProject")}
              </label>
              <select
                id="add-project"
                value={addProjectId}
                onChange={(e) => setAddProjectId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t("form.selectProject")}</option>
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.code} - {p.name}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="flex-1">
              <label htmlFor="add-member" className="block text-sm font-medium text-gray-700 mb-1">
                {t("addMember")}
              </label>
              <select
                id="add-member"
                value={addMemberId}
                onChange={(e) => setAddMemberId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t("form.selectMember")}</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.displayName} ({m.email})
                  </option>
                ))}
              </select>
            </div>
          )}
          <button
            type="button"
            onClick={handleAdd}
            disabled={isAdding}
            className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {isAdding ? t("adding") : t("add")}
          </button>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {/* Assignments table */}
      {isLoading ? (
        <div className="text-center py-8 text-gray-500">{tc("loading")}</div>
      ) : assignments.length === 0 ? (
        (selectedMemberId || selectedProjectId) && (
          <div className="text-center py-8 text-gray-500">{t("noAssignments")}</div>
        )
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                {viewMode === "by-project" && (
                  <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.member")}</th>
                )}
                {viewMode === "by-member" && (
                  <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.project")}</th>
                )}
                <th className="text-left py-3 px-4 font-medium text-gray-700">{tc("status")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{tc("actions")}</th>
              </tr>
            </thead>
            <tbody>
              {assignments.map((a) => (
                <tr key={a.id} className="border-b border-gray-100 hover:bg-gray-50">
                  {viewMode === "by-project" && (
                    <td className="py-3 px-4">
                      {a.memberName} <span className="text-xs text-gray-500">({a.memberEmail})</span>
                    </td>
                  )}
                  {viewMode === "by-member" && (
                    <td className="py-3 px-4">
                      <span className="font-mono text-xs">{a.projectCode}</span> {a.projectName}
                    </td>
                  )}
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        a.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {a.isActive ? tc("active") : tc("inactive")}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    {a.isActive ? (
                      <button
                        type="button"
                        onClick={() => handleToggle(a)}
                        className="text-red-600 hover:text-red-800 text-xs"
                      >
                        {tc("disable")}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleToggle(a)}
                        className="text-green-600 hover:text-green-800 text-xs"
                      >
                        {tc("enable")}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
