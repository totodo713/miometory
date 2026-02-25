"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { ApiError, api } from "@/services/api";

interface UserRow {
  id: string;
  email: string;
  name: string;
  roleName: string;
  tenantName: string | null;
  accountStatus: string;
  lastLoginAt: string | null;
}

interface UserListProps {
  onChangeRole: (user: UserRow) => void;
  onLock: (user: UserRow) => void;
  onUnlock: (user: UserRow) => void;
  onResetPassword: (user: UserRow) => void;
  refreshKey: number;
}

const statusClassNames: Record<string, string> = {
  active: "bg-green-100 text-green-800",
  unverified: "bg-yellow-100 text-yellow-800",
  locked: "bg-red-100 text-red-800",
  deleted: "bg-gray-100 text-gray-600",
};

export function UserList({ onChangeRole, onLock, onUnlock, onResetPassword, refreshKey }: UserListProps) {
  const format = useFormatter();
  const t = useTranslations("admin.users");
  const tc = useTranslations("common");
  const [users, setUsers] = useState<UserRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [accountStatus, setAccountStatus] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const isMobile = useMediaQuery("(max-width: 767px)");

  const hasFilters = !!debouncedSearch || !!accountStatus;

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: tc from useTranslations is stable
  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await api.admin.users.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        accountStatus: accountStatus || undefined,
      });
      setUsers(result.content);
      setTotalPages(result.totalPages);
    } catch (err: unknown) {
      setLoadError(err instanceof ApiError ? err.message : tc("fetchError"));
    } finally {
      setIsLoading(false);
    }
  }, [page, debouncedSearch, accountStatus]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadUsers();
  }, [loadUsers, refreshKey]);

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
        <select
          value={accountStatus}
          onChange={(e) => {
            setAccountStatus(e.target.value);
            setPage(0);
          }}
          aria-label={t("filterByStatus")}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">{t("allStatuses")}</option>
          <option value="active">{t("statusLabels.active")}</option>
          <option value="unverified">{t("statusLabels.unverified")}</option>
          <option value="locked">{t("statusLabels.locked")}</option>
        </select>
      </div>

      {loadError ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-center">
          <p className="text-sm text-red-800">{loadError}</p>
          <button type="button" onClick={loadUsers} className="mt-2 text-sm text-red-600 hover:text-red-800 underline">
            {tc("retry")}
          </button>
        </div>
      ) : isLoading ? (
        <Skeleton.Table rows={5} cols={7} />
      ) : users.length === 0 ? (
        <EmptyState title={t("notFound")} description={hasFilters ? t("changeFilter") : t("noUsersYet")} />
      ) : isMobile ? (
        <div className="space-y-3">
          {users.map((user) => {
            const statusClassName = statusClassNames[user.accountStatus] ?? "bg-gray-100 text-gray-600";
            const statusLabel = t(`statusLabels.${user.accountStatus}` as Parameters<typeof t>[0]);
            return (
              <div key={user.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-900">{user.name}</span>
                  <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${statusClassName}`}>
                    {statusLabel}
                  </span>
                </div>
                <p className="text-xs text-gray-500">{user.email}</p>
                <p className="text-xs text-gray-500">
                  {user.roleName} / {user.tenantName ?? "—"}
                </p>
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => onChangeRole(user)}
                    className="text-blue-600 hover:text-blue-800 text-xs"
                  >
                    {t("changeRole")}
                  </button>
                  {user.accountStatus === "locked" ? (
                    <button
                      type="button"
                      onClick={() => onUnlock(user)}
                      className="text-green-600 hover:text-green-800 text-xs"
                    >
                      {t("unlock")}
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => onLock(user)}
                      className="text-red-600 hover:text-red-800 text-xs"
                    >
                      {t("lock")}
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => onResetPassword(user)}
                    className="text-orange-600 hover:text-orange-800 text-xs"
                  >
                    {t("resetPassword")}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.email")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.name")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.role")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.tenant")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.status")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("table.lastLogin")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{t("table.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const statusClassName = statusClassNames[user.accountStatus] ?? "bg-gray-100 text-gray-600";
                const statusLabel = t(`statusLabels.${user.accountStatus}` as Parameters<typeof t>[0]);
                return (
                  <tr key={user.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-4 text-xs">{user.email}</td>
                    <td className="py-3 px-4">{user.name}</td>
                    <td className="py-3 px-4 text-xs">{user.roleName}</td>
                    <td className="py-3 px-4 text-xs text-gray-600">{user.tenantName ?? "—"}</td>
                    <td className="py-3 px-4">
                      <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${statusClassName}`}>
                        {statusLabel}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs text-gray-600">
                      {user.lastLoginAt
                        ? format.dateTime(new Date(user.lastLoginAt), {
                            year: "numeric",
                            month: "short",
                            day: "numeric",
                            hour: "2-digit",
                            minute: "2-digit",
                          })
                        : "—"}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => onChangeRole(user)}
                          className="text-blue-600 hover:text-blue-800 text-xs"
                        >
                          {t("changeRole")}
                        </button>
                        {user.accountStatus === "locked" ? (
                          <button
                            type="button"
                            onClick={() => onUnlock(user)}
                            className="text-green-600 hover:text-green-800 text-xs"
                          >
                            {t("unlock")}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => onLock(user)}
                            className="text-red-600 hover:text-red-800 text-xs"
                          >
                            {t("lock")}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => onResetPassword(user)}
                          className="text-orange-600 hover:text-orange-800 text-xs"
                        >
                          {t("resetPassword")}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
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

export type { UserRow };
