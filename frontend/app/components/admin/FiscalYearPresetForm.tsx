"use client";

import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";
import type { FiscalYearPresetRow } from "@/types/masterData";

interface FiscalYearPresetFormProps {
  preset: FiscalYearPresetRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function FiscalYearPresetForm({ preset, onClose, onSaved }: FiscalYearPresetFormProps) {
  const t = useTranslations("admin.masterData");
  const tc = useTranslations("common");
  const isEdit = preset !== null;
  const toast = useToast();
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const firstInput = dialog.querySelector<HTMLElement>("input, select, textarea");
    firstInput?.focus();

    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key === "Tab" && dialog) {
        const focusable = dialog.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  const [name, setName] = useState(preset?.name ?? "");
  const [description, setDescription] = useState(preset?.description ?? "");
  const [startMonth, setStartMonth] = useState(preset?.startMonth ?? 4);
  const [startDay, setStartDay] = useState(preset?.startDay ?? 1);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError(t("fiscalYear.nameRequired"));
      return;
    }

    if (startDay < 1 || startDay > 31) {
      setError(t("fiscalYear.startDayRange"));
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && preset) {
        await api.admin.masterData.fiscalYearPresets.update(preset.id, {
          name,
          description: description || undefined,
          startMonth,
          startDay,
        });
      } else {
        await api.admin.masterData.fiscalYearPresets.create({
          name,
          description: description || undefined,
          startMonth,
          startDay,
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
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="bg-white rounded-lg shadow-xl w-full max-w-md p-6"
      >
        <h2 id={titleId} className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? t("fiscalYear.editTitle") : t("fiscalYear.createTitle")}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="fy-preset-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("fiscalYear.name")}
            </label>
            <input
              id="fy-preset-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="fy-preset-description" className="block text-sm font-medium text-gray-700 mb-1">
              {t("fiscalYear.description")}
            </label>
            <input
              id="fy-preset-description"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="fy-preset-start-month" className="block text-sm font-medium text-gray-700 mb-1">
                {t("fiscalYear.startMonth")}
              </label>
              <select
                id="fy-preset-start-month"
                value={startMonth}
                onChange={(e) => setStartMonth(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                  <option key={m} value={m}>
                    {t(`fiscalYear.months.${m}`)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="fy-preset-start-day" className="block text-sm font-medium text-gray-700 mb-1">
                {t("fiscalYear.startDay")}
              </label>
              <input
                id="fy-preset-start-day"
                type="number"
                min={1}
                max={31}
                value={startDay}
                onChange={(e) => setStartDay(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}

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
