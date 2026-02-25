"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { ApiError, api, ForbiddenError } from "@/services/api";

interface ProjectRow {
  id: string;
  code: string;
  name: string;
  isActive: boolean;
  validFrom: string | null;
  validUntil: string | null;
  assignedMemberCount: number;
}

interface ProjectListProps {
  onEdit: (project: ProjectRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  onForbidden?: () => void;
  refreshKey: number;
}

export function ProjectList({ onEdit, onDeactivate, onActivate, onForbidden, refreshKey }: ProjectListProps) {
  const t = useTranslations("admin.projects");
  const tc = useTranslations("common");
  const [projects, setProjects] = useState<ProjectRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const isMobile = useMediaQuery("(max-width: 767px)");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: tc from useTranslations is stable
  const loadProjects = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await api.admin.projects.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        isActive: showInactive ? undefined : true,
      });
      setProjects(result.content);
      setTotalPages(result.totalPages);
    } catch (err: unknown) {
      if (err instanceof ForbiddenError) {
        onForbidden?.();
        return;
      }
      setLoadError(err instanceof ApiError ? err.message : tc("fetchError"));
    } finally {
      setIsLoading(false);
    }
  }, [page, debouncedSearch, showInactive]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadProjects();
  }, [loadProjects, refreshKey]);

  const hasFilters = !!debouncedSearch || showInactive;

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <input
          type="text"
          placeholder={t("searchPlaceholder")}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label={t("searchLabel")}
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <label className="flex items-center gap-2 text-sm text-gray-600 whitespace-nowrap">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          {t("showInactive")}
        </label>
      </div>

      {loadError ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-center">
          <p className="text-sm text-red-800">{loadError}</p>
          <button
            type="button"
            onClick={loadProjects}
            className="mt-2 text-sm text-red-600 hover:text-red-800 underline"
          >
            {tc("retry")}
          </button>
        </div>
      ) : isLoading ? (
        <Skeleton.Table rows={5} cols={6} />
      ) : projects.length === 0 ? (
        <EmptyState title={t("notFound")} description={hasFilters ? t("changeFilter") : t("noProjectsYet")} />
      ) : isMobile ? (
        <div className="space-y-3">
          {projects.map((project) => (
            <div key={project.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{project.name}</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    project.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {project.isActive ? tc("active") : tc("inactive")}
                </span>
              </div>
              <p className="text-xs text-gray-500 font-mono">{project.code}</p>
              <p className="text-xs text-gray-500">
                {project.validFrom || "—"} ~ {project.validUntil || "—"}
              </p>
              <p className="text-xs text-gray-500">{t("memberLabel", { count: project.assignedMemberCount })}</p>
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => onEdit(project)}
                  className="text-blue-600 hover:text-blue-800 text-xs"
                >
                  {tc("edit")}
                </button>
                {project.isActive ? (
                  <button
                    type="button"
                    onClick={() => onDeactivate(project.id)}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    {tc("disable")}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onActivate(project.id)}
                    className="text-green-600 hover:text-green-800 text-xs"
                  >
                    {tc("enable")}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.code")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.name")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.validPeriod")}</th>
                <th className="text-center py-3 px-4 font-medium text-gray-700">{t("table.memberCount")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.status")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{t("table.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4 font-mono text-xs">{project.code}</td>
                  <td className="py-3 px-4">{project.name}</td>
                  <td className="py-3 px-4 text-gray-600 text-xs">
                    {project.validFrom || "—"} ~ {project.validUntil || "—"}
                  </td>
                  <td className="py-3 px-4 text-center">{project.assignedMemberCount}</td>
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        project.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {project.isActive ? tc("active") : tc("inactive")}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => onEdit(project)}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        {tc("edit")}
                      </button>
                      {project.isActive ? (
                        <button
                          type="button"
                          onClick={() => onDeactivate(project.id)}
                          className="text-red-600 hover:text-red-800 text-xs"
                        >
                          {tc("disable")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onActivate(project.id)}
                          className="text-green-600 hover:text-green-800 text-xs"
                        >
                          {tc("enable")}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 text-sm border rounded disabled:opacity-50"
          >
            {tc("previous")}
          </button>
          <span className="px-3 py-1 text-sm text-gray-600">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="px-3 py-1 text-sm border rounded disabled:opacity-50"
          >
            {tc("next")}
          </button>
        </div>
      )}
    </div>
  );
}

export type { ProjectRow };
