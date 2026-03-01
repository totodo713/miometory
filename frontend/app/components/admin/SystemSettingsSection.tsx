"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";

export function SystemSettingsSection() {
  const t = useTranslations("admin.systemSettings");
  const tc = useTranslations("common");
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [fyStartMonth, setFyStartMonth] = useState(4);
  const [fyStartDay, setFyStartDay] = useState(1);
  const [mpStartDay, setMpStartDay] = useState(1);

  // biome-ignore lint/correctness/useExhaustiveDependencies: load once on mount
  const loadSettings = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.admin.system.getPatterns();
      setFyStartMonth(data.fiscalYearStartMonth);
      setFyStartDay(data.fiscalYearStartDay);
      setMpStartDay(data.monthlyPeriodStartDay);
    } catch (err: unknown) {
      toast.error(err instanceof ApiError ? err.message : t("loadError"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  const handleSave = async () => {
    try {
      setSaving(true);
      await api.admin.system.updatePatterns({
        fiscalYearStartMonth: fyStartMonth,
        fiscalYearStartDay: fyStartDay,
        monthlyPeriodStartDay: mpStartDay,
      });
      toast.success(t("saved"));
    } catch (err: unknown) {
      toast.error(err instanceof ApiError ? err.message : t("saveError"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <p className="text-gray-500">{tc("loading")}</p>;
  }

  return (
    <>
      <h1 className="text-2xl font-bold text-gray-900 mt-4 mb-2">{t("title")}</h1>
      <p className="text-sm text-gray-600 mb-6">{t("description")}</p>

      <div className="space-y-6">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">{t("fiscalYear.title")}</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="fyStartMonth" className="block text-sm font-medium text-gray-700 mb-1">
                {t("fiscalYear.startMonth")}
              </label>
              <select
                id="fyStartMonth"
                value={fyStartMonth}
                onChange={(e) => setFyStartMonth(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="fyStartDay" className="block text-sm font-medium text-gray-700 mb-1">
                {t("fiscalYear.startDay")}
              </label>
              <select
                id="fyStartDay"
                value={fyStartDay}
                onChange={(e) => setFyStartDay(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              >
                {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">{t("monthlyPeriod.title")}</h2>
          <div>
            <label htmlFor="mpStartDay" className="block text-sm font-medium text-gray-700 mb-1">
              {t("monthlyPeriod.startDay")}
            </label>
            <select
              id="mpStartDay"
              value={mpStartDay}
              onChange={(e) => setMpStartDay(Number(e.target.value))}
              className="w-full max-w-xs border border-gray-300 rounded-md px-3 py-2 text-sm"
            >
              {Array.from({ length: 28 }, (_, i) => i + 1).map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </div>
        </div>

        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {saving ? t("saving") : tc("save")}
        </button>
      </div>
    </>
  );
}
