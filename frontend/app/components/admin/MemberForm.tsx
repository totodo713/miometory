"use client";

import { useEffect, useState } from "react";
import { ApiError, api } from "@/services/api";
import type { MemberRow } from "./MemberList";

interface MemberFormProps {
  member: MemberRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function MemberForm({ member, onClose, onSaved }: MemberFormProps) {
  const isEdit = member !== null;

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const [email, setEmail] = useState(member?.email ?? "");
  const [displayName, setDisplayName] = useState(member?.displayName ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !displayName.trim()) {
      setError("メールと表示名は必須です");
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && member) {
        await api.admin.members.update(member.id, { email, displayName });
      } else {
        await api.admin.members.create({ email, displayName });
      }
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

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{isEdit ? "メンバー編集" : "メンバー招待"}</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="member-email" className="block text-sm font-medium text-gray-700 mb-1">
              メールアドレス
            </label>
            <input
              id="member-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="member-name" className="block text-sm font-medium text-gray-700 mb-1">
              表示名
            </label>
            <input
              id="member-name"
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

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
              {isSubmitting ? "保存中..." : isEdit ? "更新" : "招待"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
