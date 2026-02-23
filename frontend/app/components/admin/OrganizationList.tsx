"use client";

import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import type { OrganizationRow } from "@/services/api";
import { api } from "@/services/api";

interface OrganizationListProps {
  onEdit: (org: OrganizationRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  refreshKey: number;
  onSelectOrg?: (org: OrganizationRow) => void;
}

export function OrganizationList({ onEdit, onDeactivate, onActivate, refreshKey, onSelectOrg }: OrganizationListProps) {
  const [organizations, setOrganizations] = useState<OrganizationRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const isMobile = useMediaQuery("(max-width: 767px)");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const loadOrganizations = useCallback(async () => {
    setIsLoading(true);
    try {
      const result = await api.admin.organizations.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        isActive: showInactive ? undefined : true,
      });
      setOrganizations(result.content);
      setTotalPages(result.totalPages);
    } catch {
      // Error handled by API client
    } finally {
      setIsLoading(false);
    }
  }, [page, debouncedSearch, showInactive]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadOrganizations();
  }, [loadOrganizations, refreshKey]);

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <input
          type="text"
          placeholder="組織名またはコードで検索..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="組織名またはコードで検索"
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <label className="flex items-center gap-2 text-sm text-gray-600 whitespace-nowrap">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          無効も表示
        </label>
      </div>

      {isLoading ? (
        <Skeleton.Table rows={5} cols={7} />
      ) : organizations.length === 0 ? (
        <EmptyState
          title="組織が見つかりません"
          description={debouncedSearch || showInactive ? "検索条件を変更してください" : "まだ組織がありません"}
        />
      ) : isMobile ? (
        <div className="space-y-3">
          {organizations.map((org) => (
            <div
              key={org.id}
              className="border border-gray-200 rounded-lg p-4 space-y-2"
              onClick={() => onSelectOrg?.(org)}
              style={{ cursor: onSelectOrg ? "pointer" : "default" }}
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{org.name}</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    org.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {org.status === "ACTIVE" ? "有効" : "無効"}
                </span>
              </div>
              <p className="text-xs text-gray-500 font-mono">{org.code}</p>
              <p className="text-xs text-gray-500">レベル: {org.level} / 親組織: {org.parentName || "—"} / メンバー: {org.memberCount}</p>
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onEdit(org);
                  }}
                  className="text-blue-600 hover:text-blue-800 text-xs"
                >
                  編集
                </button>
                {org.status === "ACTIVE" ? (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onDeactivate(org.id);
                    }}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    無効化
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onActivate(org.id);
                    }}
                    className="text-green-600 hover:text-green-800 text-xs"
                  >
                    有効化
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
                <th className="text-left py-3 px-4 font-medium text-gray-700">コード</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">名前</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">レベル</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">親組織</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">メンバー数</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">ステータス</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">アクション</th>
              </tr>
            </thead>
            <tbody>
              {organizations.map((org) => (
                <tr
                  key={org.id}
                  className="border-b border-gray-100 hover:bg-gray-50"
                  onClick={() => onSelectOrg?.(org)}
                  style={{ cursor: onSelectOrg ? "pointer" : "default" }}
                >
                  <td className="py-3 px-4 font-mono text-xs">{org.code}</td>
                  <td className="py-3 px-4">{org.name}</td>
                  <td className="py-3 px-4 text-gray-600">{org.level}</td>
                  <td className="py-3 px-4 text-gray-600">{org.parentName || "—"}</td>
                  <td className="py-3 px-4 text-gray-600">{org.memberCount}</td>
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        org.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {org.status === "ACTIVE" ? "有効" : "無効"}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onEdit(org);
                        }}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        編集
                      </button>
                      {org.status === "ACTIVE" ? (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onDeactivate(org.id);
                          }}
                          className="text-red-600 hover:text-red-800 text-xs"
                        >
                          無効化
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onActivate(org.id);
                          }}
                          className="text-green-600 hover:text-green-800 text-xs"
                        >
                          有効化
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
            前へ
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
            次へ
          </button>
        </div>
      )}
    </div>
  );
}
