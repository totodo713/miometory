"use client";

import { useCallback, useEffect, useState } from "react";
import type { UserRow } from "@/components/admin/UserList";
import { UserList } from "@/components/admin/UserList";
import { ApiError, api } from "@/services/api";

export default function AdminUsersPage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const [roleDialogUser, setRoleDialogUser] = useState<UserRow | null>(null);
  const [lockDialogUser, setLockDialogUser] = useState<UserRow | null>(null);
  const [newRoleId, setNewRoleId] = useState("");
  const [lockDuration, setLockDuration] = useState("60");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (roleDialogUser) setRoleDialogUser(null);
        if (lockDialogUser) setLockDialogUser(null);
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [roleDialogUser, lockDialogUser]);

  const handleChangeRole = useCallback((user: UserRow) => {
    setRoleDialogUser(user);
    setNewRoleId("");
    setError(null);
  }, []);

  const handleLock = useCallback((user: UserRow) => {
    setLockDialogUser(user);
    setLockDuration("60");
    setError(null);
  }, []);

  const handleUnlock = useCallback(
    async (user: UserRow) => {
      if (!confirm(`${user.name} のロックを解除しますか？`)) return;
      try {
        await api.admin.users.unlock(user.id);
        refresh();
      } catch (err: unknown) {
        alert(err instanceof ApiError ? err.message : "エラーが発生しました");
      }
    },
    [refresh],
  );

  const handleResetPassword = useCallback(async (user: UserRow) => {
    if (!confirm(`${user.name} のパスワードを初期化しますか？`)) return;
    try {
      await api.admin.users.resetPassword(user.id);
      alert("パスワードが初期化されました");
    } catch (err: unknown) {
      alert(err instanceof ApiError ? err.message : "エラーが発生しました");
    }
  }, []);

  const submitChangeRole = async () => {
    if (!roleDialogUser || !newRoleId.trim()) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await api.admin.users.changeRole(roleDialogUser.id, { roleId: newRoleId });
      setRoleDialogUser(null);
      refresh();
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "エラーが発生しました");
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitLock = async () => {
    if (!lockDialogUser) return;
    const minutes = Number.parseInt(lockDuration, 10);
    if (Number.isNaN(minutes) || minutes <= 0) {
      setError("有効な時間（分）を入力してください");
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      await api.admin.users.lock(lockDialogUser.id, { durationMinutes: minutes });
      setLockDialogUser(null);
      refresh();
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : "エラーが発生しました");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">ユーザー管理</h1>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <UserList
          onChangeRole={handleChangeRole}
          onLock={handleLock}
          onUnlock={handleUnlock}
          onResetPassword={handleResetPassword}
          refreshKey={refreshKey}
        />
      </div>

      {/* Change Role Dialog */}
      {roleDialogUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">ロール変更</h2>
            <p className="text-sm text-gray-600 mb-4">
              {roleDialogUser.name} ({roleDialogUser.email})
            </p>
            <p className="text-sm text-gray-600 mb-2">現在のロール: {roleDialogUser.roleName}</p>
            <div className="mb-4">
              <label htmlFor="new-role-id" className="block text-sm font-medium text-gray-700 mb-1">
                新しいロールID
              </label>
              <input
                id="new-role-id"
                type="text"
                value={newRoleId}
                onChange={(e) => setNewRoleId(e.target.value)}
                placeholder="ロールのUUIDを入力..."
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            {error && <p className="text-sm text-red-600 mb-4">{error}</p>}
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setRoleDialogUser(null)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={submitChangeRole}
                disabled={isSubmitting || !newRoleId.trim()}
                className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
              >
                {isSubmitting ? "変更中..." : "変更"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Lock Dialog */}
      {lockDialogUser && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">アカウントロック</h2>
            <p className="text-sm text-gray-600 mb-4">
              {lockDialogUser.name} ({lockDialogUser.email})
            </p>
            <div className="mb-4">
              <label htmlFor="lock-duration" className="block text-sm font-medium text-gray-700 mb-1">
                ロック期間（分）
              </label>
              <input
                id="lock-duration"
                type="number"
                value={lockDuration}
                onChange={(e) => setLockDuration(e.target.value)}
                min="1"
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            {error && <p className="text-sm text-red-600 mb-4">{error}</p>}
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setLockDialogUser(null)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={submitLock}
                disabled={isSubmitting}
                className="px-4 py-2 text-sm text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50"
              >
                {isSubmitting ? "ロック中..." : "ロック"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
