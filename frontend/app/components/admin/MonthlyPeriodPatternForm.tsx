"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { ApiError, api } from "@/services/api";

interface MonthlyPeriodPatternFormProps {
  tenantId: string;
  open: boolean;
  onClose: () => void;
  onCreated: (pattern: { id: string; name: string; startDay: number }) => void;
}

export function MonthlyPeriodPatternForm({ tenantId, open, onClose, onCreated }: MonthlyPeriodPatternFormProps) {
  const t = useTranslations("admin.monthlyPeriodPattern");
  const tc = useTranslations("common");
  const [name, setName] = useState("");
  const [startDay, setStartDay] = useState(21);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError(t("form.nameRequired"));
      return;
    }

    setIsSubmitting(true);
    try {
      const pattern = await api.admin.patterns.createMonthlyPeriodPattern(tenantId, { name, startDay });
      onCreated(pattern);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(tc("error"));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">{t("title")}</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="mp-pattern-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.name")}
            </label>
            <input
              id="mp-pattern-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="mp-pattern-start-day" className="block text-sm font-medium text-gray-700 mb-1">
              {t("form.startDay")}
            </label>
            <select
              id="mp-pattern-start-day"
              value={startDay}
              onChange={(e) => setStartDay(Number(e.target.value))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {Array.from({ length: 28 }, (_, i) => i + 1).map((d) => (
                <option key={d} value={d}>
                  {t("form.daySuffix", { day: d })}
                </option>
              ))}
            </select>
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
              {isSubmitting ? t("form.creating") : t("form.create")}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
