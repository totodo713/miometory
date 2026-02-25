"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { ApiError, ForbiddenError, api } from "@/services/api";

interface MemberRow {
  id: string;
  email: string;
  displayName: string;
  organizationId: string | null;
  managerId: string | null;
  managerName: string | null;
  isActive: boolean;
}

interface MemberListProps {
  onEdit: (member: MemberRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  refreshKey: number;
  onForbidden?: () => void;
}

export function MemberList({ onEdit, onDeactivate, onActivate, refreshKey, onForbidden }: MemberListProps) {
  const t = useTranslations("admin.members");
  const tc = useTranslations("common");
  const [members, setMembers] = useState<MemberRow[]>([]);
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
  const loadMembers = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await api.admin.members.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        isActive: showInactive ? undefined : true,
      });
      setMembers(result.content);
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
  }, [page, debouncedSearch, showInactive, onForbidden]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadMembers();
  }, [loadMembers, refreshKey]);

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
          aria-label={t("searchPlaceholder")}
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
            onClick={loadMembers}
            className="mt-2 text-sm text-red-600 hover:text-red-800 underline"
          >
            {tc("retry")}
          </button>
        </div>
      ) : isLoading ? (
        <Skeleton.Table rows={5} cols={5} />
      ) : members.length === 0 ? (
        <EmptyState title={t("notFound")} description={hasFilters ? t("changeFilter") : t("noMembersYet")} />
      ) : isMobile ? (
        <div className="space-y-3">
          {members.map((member) => (
            <div key={member.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{member.displayName}</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    member.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {member.isActive ? tc("active") : tc("inactive")}
                </span>
              </div>
              <p className="text-xs text-gray-500">{member.email}</p>
              <p className="text-xs text-gray-500">
                {t("managerLabel")}: {member.managerName || "—"}
              </p>
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => onEdit(member)}
                  className="text-blue-600 hover:text-blue-800 text-xs"
                >
                  {tc("edit")}
                </button>
                {member.isActive ? (
                  <button
                    type="button"
                    onClick={() => onDeactivate(member.id)}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    {t("deactivate")}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onActivate(member.id)}
                    className="text-green-600 hover:text-green-800 text-xs"
                  >
                    {t("activate")}
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
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.name")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.email")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("managerLabel")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.status")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{t("table.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4">{member.displayName}</td>
                  <td className="py-3 px-4 text-gray-600">{member.email}</td>
                  <td className="py-3 px-4 text-gray-600">{member.managerName || "—"}</td>
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        member.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {member.isActive ? tc("active") : tc("inactive")}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => onEdit(member)}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        {tc("edit")}
                      </button>
                      {member.isActive ? (
                        <button
                          type="button"
                          onClick={() => onDeactivate(member.id)}
                          className="text-red-600 hover:text-red-800 text-xs"
                        >
                          {t("deactivate")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onActivate(member.id)}
                          className="text-green-600 hover:text-green-800 text-xs"
                        >
                          {t("activate")}
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

export type { MemberRow };
