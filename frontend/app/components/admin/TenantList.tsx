"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/services/api";

interface TenantRow {
  id: string;
  code: string;
  name: string;
  status: string;
  createdAt: string;
}

interface TenantListProps {
  onEdit: (tenant: TenantRow) => void;
  onDeactivate: (id: string) => void;
  onActivate: (id: string) => void;
  refreshKey: number;
}

export function TenantList({ onEdit, onDeactivate, onActivate, refreshKey }: TenantListProps) {
  const [tenants, setTenants] = useState<TenantRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  const loadTenants = useCallback(async () => {
    setIsLoading(true);
    try {
      const result = await api.admin.tenants.list({
        page,
        size: 20,
        status: statusFilter || undefined,
      });
      setTenants(result.content);
      setTotalPages(result.totalPages);
    } catch {
      // Error handled by API client
    } finally {
      setIsLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    loadTenants();
  }, [loadTenants, refreshKey]);

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
          aria-label="ステータスで絞り込み"
          className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">すべてのステータス</option>
          <option value="ACTIVE">有効</option>
          <option value="INACTIVE">無効</option>
        </select>
      </div>

      {isLoading ? (
        <div className="text-center py-8 text-gray-500">読み込み中...</div>
      ) : tenants.length === 0 ? (
        <div className="text-center py-8 text-gray-500">テナントが見つかりません</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 font-medium text-gray-700">コード</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">名前</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">作成日</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">状態</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr key={tenant.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4 font-mono text-xs">{tenant.code}</td>
                  <td className="py-3 px-4">{tenant.name}</td>
                  <td className="py-3 px-4 text-gray-600 text-xs">{new Date(tenant.createdAt).toLocaleDateString()}</td>
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        tenant.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {tenant.status === "ACTIVE" ? "有効" : "無効"}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => onEdit(tenant)}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        編集
                      </button>
                      {tenant.status === "ACTIVE" ? (
                        <button
                          type="button"
                          onClick={() => onDeactivate(tenant.id)}
                          className="text-red-600 hover:text-red-800 text-xs"
                        >
                          無効化
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onActivate(tenant.id)}
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

export type { TenantRow };
