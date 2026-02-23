"use client";

import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { api } from "@/services/api";

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

const statusLabels: Record<string, { label: string; className: string }> = {
  active: { label: "有効", className: "bg-green-100 text-green-800" },
  unverified: { label: "未確認", className: "bg-yellow-100 text-yellow-800" },
  locked: { label: "ロック", className: "bg-red-100 text-red-800" },
  deleted: { label: "削除済", className: "bg-gray-100 text-gray-600" },
};

export function UserList({ onChangeRole, onLock, onUnlock, onResetPassword, refreshKey }: UserListProps) {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [accountStatus, setAccountStatus] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const isMobile = useMediaQuery("(max-width: 767px)");

  const hasFilters = !!debouncedSearch || !!accountStatus;

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      const result = await api.admin.users.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        accountStatus: accountStatus || undefined,
      });
      setUsers(result.content);
      setTotalPages(result.totalPages);
    } catch {
      // Error handled by API client
    } finally {
      setIsLoading(false);
    }
  }, [page, debouncedSearch, accountStatus]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers, refreshKey]);

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <input
          type="text"
          placeholder="メールまたは名前で検索..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="メールまたは名前で検索"
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={accountStatus}
          onChange={(e) => {
            setAccountStatus(e.target.value);
            setPage(0);
          }}
          aria-label="アカウントステータスで絞り込み"
          className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">すべてのステータス</option>
          <option value="active">有効</option>
          <option value="unverified">未確認</option>
          <option value="locked">ロック</option>
        </select>
      </div>

      {isLoading ? (
        <Skeleton.Table rows={5} cols={7} />
      ) : users.length === 0 ? (
        <EmptyState
          title="ユーザーが見つかりません"
          description={hasFilters ? "検索条件を変更してください" : "まだユーザーがいません"}
        />
      ) : isMobile ? (
        <div className="space-y-3">
          {users.map((user) => {
            const statusInfo = statusLabels[user.accountStatus] ?? {
              label: user.accountStatus,
              className: "bg-gray-100 text-gray-600",
            };
            return (
              <div key={user.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-900">{user.name}</span>
                  <span
                    className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${statusInfo.className}`}
                  >
                    {statusInfo.label}
                  </span>
                </div>
                <p className="text-xs text-gray-500">{user.email}</p>
                <p className="text-xs text-gray-500">{user.roleName} / {user.tenantName ?? "—"}</p>
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => onChangeRole(user)}
                    className="text-blue-600 hover:text-blue-800 text-xs"
                  >
                    ロール変更
                  </button>
                  {user.accountStatus === "locked" ? (
                    <button
                      type="button"
                      onClick={() => onUnlock(user)}
                      className="text-green-600 hover:text-green-800 text-xs"
                    >
                      ロック解除
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => onLock(user)}
                      className="text-red-600 hover:text-red-800 text-xs"
                    >
                      ロック
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => onResetPassword(user)}
                    className="text-orange-600 hover:text-orange-800 text-xs"
                  >
                    PW初期化
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
                <th className="text-left py-3 px-4 font-medium text-gray-700">メール</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">名前</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">ロール</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">テナント</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">状態</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">最終ログイン</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const statusInfo = statusLabels[user.accountStatus] ?? {
                  label: user.accountStatus,
                  className: "bg-gray-100 text-gray-600",
                };
                return (
                  <tr key={user.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-4 text-xs">{user.email}</td>
                    <td className="py-3 px-4">{user.name}</td>
                    <td className="py-3 px-4 text-xs">{user.roleName}</td>
                    <td className="py-3 px-4 text-xs text-gray-600">{user.tenantName ?? "—"}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${statusInfo.className}`}
                      >
                        {statusInfo.label}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs text-gray-600">
                      {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : "—"}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => onChangeRole(user)}
                          className="text-blue-600 hover:text-blue-800 text-xs"
                        >
                          ロール変更
                        </button>
                        {user.accountStatus === "locked" ? (
                          <button
                            type="button"
                            onClick={() => onUnlock(user)}
                            className="text-green-600 hover:text-green-800 text-xs"
                          >
                            ロック解除
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => onLock(user)}
                            className="text-red-600 hover:text-red-800 text-xs"
                          >
                            ロック
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => onResetPassword(user)}
                          className="text-orange-600 hover:text-orange-800 text-xs"
                        >
                          PW初期化
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

export type { UserRow };
