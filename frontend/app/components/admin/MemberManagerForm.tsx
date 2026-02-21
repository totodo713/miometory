"use client";

import { useCallback, useEffect, useState } from "react";
import type { OrganizationMemberRow, OrganizationRow } from "@/services/api";
import { ApiError, api } from "@/services/api";

type FormMode = "assignManager" | "transferOrg" | "createMember";

interface MemberManagerFormProps {
  mode: FormMode;
  organizationId: string;
  /** Target member for assignManager / transferOrg modes */
  member?: OrganizationMemberRow;
  onClose: () => void;
  onSaved: () => void;
}

export function MemberManagerForm({ mode, organizationId, member, onClose, onSaved }: MemberManagerFormProps) {
  // ESC key handler
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  // Shared state
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // assignManager state
  const [managerId, setManagerId] = useState("");
  const [availableMembers, setAvailableMembers] = useState<
    Array<{ id: string; email: string; displayName: string; isActive: boolean }>
  >([]);

  // transferOrg state
  const [targetOrgId, setTargetOrgId] = useState("");
  const [availableOrgs, setAvailableOrgs] = useState<OrganizationRow[]>([]);

  // createMember state
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [newMemberManagerId, setNewMemberManagerId] = useState("");

  // Load available members for manager selection
  const loadMembers = useCallback(async () => {
    try {
      const result = await api.admin.members.list({ isActive: true, size: 1000 });
      setAvailableMembers(
        result.content
          .filter((m) => m.id !== member?.id)
          .map((m) => ({
            id: m.id,
            email: m.email,
            displayName: m.displayName,
            isActive: true,
          })),
      );
    } catch {
      // Error handled by API client
    }
  }, [member?.id]);

  // Load available organizations for transfer
  const loadOrgs = useCallback(async () => {
    try {
      const result = await api.admin.organizations.list({ isActive: true, size: 1000 });
      setAvailableOrgs(result.content.filter((o) => o.id !== organizationId));
    } catch {
      // Error handled by API client
    }
  }, [organizationId]);

  useEffect(() => {
    if (mode === "assignManager") {
      loadMembers();
    } else if (mode === "transferOrg") {
      loadOrgs();
    } else if (mode === "createMember") {
      loadMembers();
    }
  }, [mode, loadMembers, loadOrgs]);

  const handleAssignManager = async () => {
    if (!member || !managerId) {
      setError("マネージャーを選択してください");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await api.admin.members.assignManager(member.id, managerId);
      onSaved();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleTransferOrg = async () => {
    if (!member || !targetOrgId) {
      setError("移動先の組織を選択してください");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await api.admin.members.transferMember(member.id, targetOrgId);
      onSaved();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCreateMember = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!email.trim() || !displayName.trim()) {
      setError("メールアドレスと表示名は必須です");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await api.admin.members.create({
        email,
        displayName,
        organizationId,
        managerId: newMemberManagerId || undefined,
      });
      onSaved();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (mode === "assignManager") {
      handleAssignManager();
    } else if (mode === "transferOrg") {
      handleTransferOrg();
    } else if (mode === "createMember") {
      handleCreateMember(e);
    }
  };

  const getTitle = () => {
    switch (mode) {
      case "assignManager":
        return "マネージャー割り当て";
      case "transferOrg":
        return "組織異動";
      case "createMember":
        return "メンバー作成";
    }
  };

  const getSubmitLabel = () => {
    if (isSubmitting) return "処理中...";
    switch (mode) {
      case "assignManager":
        return "割り当て";
      case "transferOrg":
        return "異動";
      case "createMember":
        return "作成";
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{getTitle()}</h2>

        {member && mode !== "createMember" && (
          <div className="mb-4 p-3 bg-gray-50 rounded-md">
            <p className="text-sm text-gray-600">
              対象メンバー: <span className="font-medium text-gray-900">{member.displayName}</span>
            </p>
            <p className="text-xs text-gray-500">{member.email}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {mode === "assignManager" && (
            <div>
              <label htmlFor="manager-select" className="block text-sm font-medium text-gray-700 mb-1">
                マネージャー
              </label>
              <select
                id="manager-select"
                value={managerId}
                onChange={(e) => setManagerId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">マネージャーを選択...</option>
                {availableMembers.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.displayName} ({m.email})
                  </option>
                ))}
              </select>
              {member?.managerDisplayName && (
                <p className="mt-1 text-xs text-gray-500">現在のマネージャー: {member.managerDisplayName}</p>
              )}
            </div>
          )}

          {mode === "transferOrg" && (
            <div>
              <label htmlFor="org-select" className="block text-sm font-medium text-gray-700 mb-1">
                移動先組織
              </label>
              <select
                id="org-select"
                value={targetOrgId}
                onChange={(e) => setTargetOrgId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">組織を選択...</option>
                {availableOrgs.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.code} - {o.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {mode === "createMember" && (
            <>
              <div>
                <label htmlFor="new-member-email" className="block text-sm font-medium text-gray-700 mb-1">
                  メールアドレス
                </label>
                <input
                  id="new-member-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="example@company.com"
                />
              </div>

              <div>
                <label htmlFor="new-member-name" className="block text-sm font-medium text-gray-700 mb-1">
                  表示名
                </label>
                <input
                  id="new-member-name"
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="山田 太郎"
                />
              </div>

              <div>
                <label htmlFor="new-member-manager" className="block text-sm font-medium text-gray-700 mb-1">
                  マネージャー (任意)
                </label>
                <select
                  id="new-member-manager"
                  value={newMemberManagerId}
                  onChange={(e) => setNewMemberManagerId(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">なし</option>
                  {availableMembers.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.displayName} ({m.email})
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
            >
              キャンセル
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {getSubmitLabel()}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
