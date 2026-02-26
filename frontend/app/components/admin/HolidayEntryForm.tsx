"use client";

import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";
import type { HolidayEntryRow } from "@/types/masterData";

interface HolidayEntryFormProps {
  calendarId: string;
  entry: HolidayEntryRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function HolidayEntryForm({ calendarId, entry, onClose, onSaved }: HolidayEntryFormProps) {
  const t = useTranslations("admin.masterData");
  const tc = useTranslations("common");
  const tMonth = useTranslations("admin.masterData.fiscalYear.months");
  const tNth = useTranslations("admin.masterData.holidayCalendar.nthLabels");
  const tWeekday = useTranslations("admin.masterData.holidayCalendar.weekdays");
  const isEdit = entry !== null;
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

  const [name, setName] = useState(entry?.name ?? "");
  const [entryType, setEntryType] = useState<"FIXED" | "NTH_WEEKDAY">(entry?.entryType ?? "FIXED");
  const [month, setMonth] = useState(entry?.month ?? 1);
  const [day, setDay] = useState(entry?.day ?? 1);
  const [nthOccurrence, setNthOccurrence] = useState(entry?.nthOccurrence ?? 1);
  const [dayOfWeek, setDayOfWeek] = useState(entry?.dayOfWeek ?? 1);
  const [specificYear, setSpecificYear] = useState<string>(entry?.specificYear?.toString() ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError(t("holidayCalendar.nameRequired"));
      return;
    }

    setIsSubmitting(true);
    try {
      const data = {
        name,
        entryType,
        month,
        ...(entryType === "FIXED" ? { day } : { nthOccurrence, dayOfWeek }),
        ...(specificYear ? { specificYear: Number(specificYear) } : {}),
      };

      if (isEdit && entry) {
        await api.admin.masterData.holidayCalendars.updateEntry(calendarId, entry.id, data);
      } else {
        await api.admin.masterData.holidayCalendars.addEntry(calendarId, data);
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
          {isEdit ? t("holidayCalendar.editEntry") : t("holidayCalendar.createEntry")}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="he-name" className="block text-sm font-medium text-gray-700 mb-1">
              {t("holidayCalendar.entryName")}
            </label>
            <input
              id="he-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <span className="block text-sm font-medium text-gray-700 mb-2">{t("holidayCalendar.entryType")}</span>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name="entryType"
                  value="FIXED"
                  checked={entryType === "FIXED"}
                  onChange={() => setEntryType("FIXED")}
                />
                {t("holidayCalendar.entryFixed")}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name="entryType"
                  value="NTH_WEEKDAY"
                  checked={entryType === "NTH_WEEKDAY"}
                  onChange={() => setEntryType("NTH_WEEKDAY")}
                />
                {t("holidayCalendar.entryNthWeekday")}
              </label>
            </div>
          </div>

          <div>
            <label htmlFor="he-month" className="block text-sm font-medium text-gray-700 mb-1">
              {t("holidayCalendar.month")}
            </label>
            <select
              id="he-month"
              value={month}
              onChange={(e) => setMonth(Number(e.target.value))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                <option key={m} value={m}>
                  {tMonth(String(m))}
                </option>
              ))}
            </select>
          </div>

          {entryType === "FIXED" ? (
            <div>
              <label htmlFor="he-day" className="block text-sm font-medium text-gray-700 mb-1">
                {t("holidayCalendar.day")}
              </label>
              <input
                id="he-day"
                type="number"
                min={1}
                max={31}
                value={day}
                onChange={(e) => setDay(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="he-nth" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("holidayCalendar.nthOccurrence")}
                </label>
                <select
                  id="he-nth"
                  value={nthOccurrence}
                  onChange={(e) => setNthOccurrence(Number(e.target.value))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {Array.from({ length: 5 }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>
                      {tNth(String(n))}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="he-dow" className="block text-sm font-medium text-gray-700 mb-1">
                  {t("holidayCalendar.dayOfWeek")}
                </label>
                <select
                  id="he-dow"
                  value={dayOfWeek}
                  onChange={(e) => setDayOfWeek(Number(e.target.value))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {Array.from({ length: 7 }, (_, i) => i + 1).map((d) => (
                    <option key={d} value={d}>
                      {tWeekday(String(d))}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          <div>
            <label htmlFor="he-year" className="block text-sm font-medium text-gray-700 mb-1">
              {t("holidayCalendar.specificYear")}
            </label>
            <input
              id="he-year"
              type="number"
              min={2000}
              max={2100}
              value={specificYear}
              onChange={(e) => setSpecificYear(e.target.value)}
              placeholder=""
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
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
