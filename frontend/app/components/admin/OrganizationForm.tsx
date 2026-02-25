"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { useToast } from "@/hooks/useToast";
import type { OrganizationRow } from "@/services/api";
import { ApiError, api } from "@/services/api";

interface OrganizationFormProps {
  organization: OrganizationRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function OrganizationForm({ organization, onClose, onSaved }: OrganizationFormProps) {
  const t = useTranslations("admin.organizations");
  const tc = useTranslations("common");
  const isEdit = organization !== null;
  const toast = useToast();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const [code, setCode] = useState(organization?.code ?? "");
  const [name, setName] = useState(organization?.name ?? "");
  const [parentId, setParentId] = useState(organization?.parentId ?? "");
  const [parentOrganizations, setParentOrganizations] = useState<OrganizationRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Load active organizations for parent dropdown (only in create mode)
  useEffect(() => {
    if (!isEdit) {
      api.admin.organizations
        .list({ isActive: true, size: 1000 })
        .then((result) => {
          setParentOrganizations(result.content);
        })
        .catch(() => {
          // Error handled by API client
        });
    }
  }, [isEdit]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!isEdit && !code.trim()) {
      setError(t("codeRequired"));
      return;
    }

    if (!name.trim()) {
      setError(t("nameRequired"));
      return;
    }

    // Validate code pattern (only in create mode)
    if (!isEdit) {
      const codePattern = /^[A-Za-z0-9_]+$/;
      if (!codePattern.test(code)) {
        setError(t("codeAlphanumeric"));
        return;
      }
      if (code.length > 32) {
        setError(t("codeMaxLength"));
        return;
      }
    }

    if (name.length > 256) {
      setError(t("nameMaxLength"));
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && organization) {
        await api.admin.organizations.update(organization.id, { name });
      } else {
        await api.admin.organizations.create({
          code,
          name,
          parentId: parentId || undefined,
        });
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
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? t("editOrganization") : t("addOrganization")}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="org-code" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.code")}
            </label>
            <input
              id="org-code"
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
              disabled={isEdit}
              maxLength={32}
              pattern="[A-Za-z0-9_]+"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
              placeholder={t("codePlaceholder")}
            />
            <p className="mt-1 text-xs text-gray-500">{t("codeHint")}</p>
          </div>

          <div>
            <label htmlFor="org-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.name")}
            </label>
            <input
              id="org-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={256}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder={t("namePlaceholder")}
            />
            <p className="mt-1 text-xs text-gray-500">{t("nameHint")}</p>
          </div>

          {!isEdit && (
            <div>
              <label htmlFor="org-parent" className="block text-sm font-medium text-gray-700 mb-1">
                {t("form.parent")}
              </label>
              <select
                id="org-parent"
                value={parentId}
                onChange={(e) => setParentId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t("form.noParent")}</option>
                {parentOrganizations.map((org) => (
                  <option key={org.id} value={org.id}>
                    {t("form.parentOption", { code: org.code, name: org.name, level: org.level })}
                  </option>
                ))}
              </select>
            </div>
          )}

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
