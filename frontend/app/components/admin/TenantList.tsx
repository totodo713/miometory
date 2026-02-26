"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { ApiError, api } from "@/services/api";

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
  onSelectTenant?: (tenant: TenantRow) => void;
  refreshKey: number;
}

export function TenantList({ onEdit, onDeactivate, onActivate, onSelectTenant, refreshKey }: TenantListProps) {
  const format = useFormatter();
  const t = useTranslations("admin.tenants");
  const tc = useTranslations("common");
  const [tenants, setTenants] = useState<TenantRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const isMobile = useMediaQuery("(max-width: 767px)");

  // biome-ignore lint/correctness/useExhaustiveDependencies: tc from useTranslations is stable
  const loadTenants = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await api.admin.tenants.list({
        page,
        size: 20,
        status: statusFilter || undefined,
      });
      setTenants(result.content);
      setTotalPages(result.totalPages);
    } catch (err: unknown) {
      setLoadError(err instanceof ApiError ? err.message : tc("fetchError"));
    } finally {
      setIsLoading(false);
    }
  }, [page, statusFilter]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
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
          aria-label={t("filterByStatus")}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">{t("allStatuses")}</option>
          <option value="ACTIVE">{tc("active")}</option>
          <option value="INACTIVE">{tc("inactive")}</option>
        </select>
      </div>

      {loadError ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-center">
          <p className="text-sm text-red-800">{loadError}</p>
          <button
            type="button"
            onClick={loadTenants}
            className="mt-2 text-sm text-red-600 hover:text-red-800 underline"
          >
            {tc("retry")}
          </button>
        </div>
      ) : isLoading ? (
        <Skeleton.Table rows={5} cols={5} />
      ) : tenants.length === 0 ? (
        <EmptyState title={t("notFound")} description={statusFilter ? t("changeFilter") : t("noTenantsYet")} />
      ) : isMobile ? (
        <div className="space-y-3">
          {tenants.map((tenant) => (
            <div key={tenant.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{tenant.name}</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    tenant.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {tenant.status === "ACTIVE" ? tc("active") : tc("inactive")}
                </span>
              </div>
              <p className="text-xs text-gray-500 font-mono">{tenant.code}</p>
              <p className="text-xs text-gray-500">
                {format.dateTime(new Date(tenant.createdAt), { year: "numeric", month: "short", day: "numeric" })}
              </p>
              <div className="flex gap-2 pt-1">
                {onSelectTenant && (
                  <button
                    type="button"
                    onClick={() => onSelectTenant(tenant)}
                    className="text-indigo-600 hover:text-indigo-800 text-xs"
                  >
                    {tc("details")}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => onEdit(tenant)}
                  className="text-blue-600 hover:text-blue-800 text-xs"
                >
                  {tc("edit")}
                </button>
                {tenant.status === "ACTIVE" ? (
                  <button
                    type="button"
                    onClick={() => onDeactivate(tenant.id)}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    {tc("disable")}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onActivate(tenant.id)}
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
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("createdDate")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.status")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{t("table.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr key={tenant.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="py-3 px-4 font-mono text-xs">{tenant.code}</td>
                  <td className="py-3 px-4">{tenant.name}</td>
                  <td className="py-3 px-4 text-gray-600 text-xs">
                    {format.dateTime(new Date(tenant.createdAt), { year: "numeric", month: "short", day: "numeric" })}
                  </td>
                  <td className="py-3 px-4">
                    <span
                      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                        tenant.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {tenant.status === "ACTIVE" ? tc("active") : tc("inactive")}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      {onSelectTenant && (
                        <button
                          type="button"
                          onClick={() => onSelectTenant(tenant)}
                          className="text-indigo-600 hover:text-indigo-800 text-xs"
                        >
                          {tc("details")}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onEdit(tenant)}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        {tc("edit")}
                      </button>
                      {tenant.status === "ACTIVE" ? (
                        <button
                          type="button"
                          onClick={() => onDeactivate(tenant.id)}
                          className="text-red-600 hover:text-red-800 text-xs"
                        >
                          {tc("disable")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => onActivate(tenant.id)}
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

export type { TenantRow };
