"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";
import type { MonthlyPeriodPresetRow } from "@/types/masterData";

interface MonthlyPeriodPresetFormProps {
  preset: MonthlyPeriodPresetRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function MonthlyPeriodPresetForm({ preset, onClose, onSaved }: MonthlyPeriodPresetFormProps) {
  const t = useTranslations("admin.masterData");
  const tc = useTranslations("common");
  const isEdit = preset !== null;
  const toast = useToast();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const [name, setName] = useState(preset?.name ?? "");
  const [description, setDescription] = useState(preset?.description ?? "");
  const [startDay, setStartDay] = useState(preset?.startDay ?? 1);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError(t("monthlyPeriod.nameRequired"));
      return;
    }

    if (startDay < 1 || startDay > 28) {
      setError(t("monthlyPeriod.startDayRange"));
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEdit && preset) {
        await api.admin.masterData.monthlyPeriodPresets.update(preset.id, {
          name,
          description: description || undefined,
          startDay,
        });
      } else {
        await api.admin.masterData.monthlyPeriodPresets.create({
          name,
          description: description || undefined,
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
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {isEdit ? t("monthlyPeriod.editTitle") : t("monthlyPeriod.createTitle")}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="mp-preset-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("monthlyPeriod.name")}
            </label>
            <input
              id="mp-preset-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="mp-preset-description" className="block text-sm font-medium text-gray-700 mb-1">
              {t("monthlyPeriod.description")}
            </label>
            <input
              id="mp-preset-description"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label htmlFor="mp-preset-start-day" className="block text-sm font-medium text-gray-700 mb-1">
              {t("monthlyPeriod.startDay")}
            </label>
            <input
              id="mp-preset-start-day"
              type="number"
              min={1}
              max={28}
              value={startDay}
              onChange={(e) => setStartDay(Number(e.target.value))}
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
