"use client";

import { useEffect, useState } from "react";
import { ApiError, api } from "@/services/api";
import { useToast } from "@/hooks/useToast";
import type { ProjectRow } from "./ProjectList";

interface ProjectFormProps {
  project: ProjectRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function ProjectForm({ project, onClose, onSaved }: ProjectFormProps) {
  const isEdit = project !== null;
  const toast = useToast();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const [code, setCode] = useState(project?.code ?? "");
  const [name, setName] = useState(project?.name ?? "");
  const [validFrom, setValidFrom] = useState(project?.validFrom ?? "");
  const [validUntil, setValidUntil] = useState(project?.validUntil ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!code.trim() || !name.trim()) {
      setError("コードと名前は必須です");
      return;
    }

    if (validFrom && validUntil && validFrom > validUntil) {
      setError("終了日は開始日以降にしてください");
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && project) {
        await api.admin.projects.update(project.id, {
          name,
          validFrom: validFrom || undefined,
          validUntil: validUntil || undefined,
        });
      } else {
        await api.admin.projects.create({
          code,
          name,
          validFrom: validFrom || undefined,
          validUntil: validUntil || undefined,
        });
      }
      toast.success(isEdit ? "プロジェクトを更新しました" : "プロジェクトを作成しました");
      onSaved();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
      toast.error(err instanceof ApiError ? err.message : "保存に失敗しました");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{isEdit ? "プロジェクト編集" : "プロジェクト作成"}</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="project-code" className="block text-sm font-medium text-gray-700 mb-1">
              コード
            </label>
            <input
              id="project-code"
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              disabled={isEdit}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>

          <div>
            <label htmlFor="project-name" className="block text-sm font-medium text-gray-700 mb-1">
              名前
            </label>
            <input
              id="project-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="project-valid-from" className="block text-sm font-medium text-gray-700 mb-1">
                開始日
              </label>
              <input
                id="project-valid-from"
                type="date"
                value={validFrom}
                onChange={(e) => setValidFrom(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label htmlFor="project-valid-until" className="block text-sm font-medium text-gray-700 mb-1">
                終了日
              </label>
              <input
                id="project-valid-until"
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
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
              {isSubmitting ? "保存中..." : isEdit ? "更新" : "作成"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
