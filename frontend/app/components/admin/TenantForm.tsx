"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";
import type { TenantRow } from "./TenantList";

interface TenantFormProps {
  tenant: TenantRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function TenantForm({ tenant, onClose, onSaved }: TenantFormProps) {
  const t = useTranslations("admin.tenants");
  const tc = useTranslations("common");
  const isEdit = tenant !== null;
  const toast = useToast();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const [code, setCode] = useState(tenant?.code ?? "");
  const [name, setName] = useState(tenant?.name ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!code.trim() || !name.trim()) {
      setError(t("codeAndNameRequired"));
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && tenant) {
        await api.admin.tenants.update(tenant.id, { name });
      } else {
        await api.admin.tenants.create({ code, name });
      }
      toast.success(isEdit ? t("updated") : t("created"));
      onSaved();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(tc("error"));
      }
      toast.error(err instanceof ApiError ? err.message : t("saveFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{isEdit ? t("editTenant") : t("addTenant")}</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="tenant-code" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.code")}
            </label>
            <input
              id="tenant-code"
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              disabled={isEdit}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>

          <div>
            <label htmlFor="tenant-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.name")}
            </label>
            <input
              id="tenant-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
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
              {tc("cancel")}
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {isSubmitting ? tc("saving") : isEdit ? tc("update") : tc("create")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
