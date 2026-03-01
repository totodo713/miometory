"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { FiscalYearPatternForm } from "@/components/admin/FiscalYearPatternForm";
import { MonthlyPeriodPatternForm } from "@/components/admin/MonthlyPeriodPatternForm";
import { useToast } from "@/hooks/useToast";
import { useAdminContext } from "@/providers/AdminProvider";
import type { FiscalYearPatternOption, MonthlyPeriodPatternOption } from "@/services/api";
import { ApiError, api } from "@/services/api";

export function TenantSettingsSection() {
  const t = useTranslations("admin.tenantSettings");
  const tc = useTranslations("common");
  const toast = useToast();
  const { adminContext } = useAdminContext();
  const tenantId = adminContext?.tenantId;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [defaultFyPatternId, setDefaultFyPatternId] = useState<string>("");
  const [defaultMpPatternId, setDefaultMpPatternId] = useState<string>("");
  const [fiscalYearPatterns, setFiscalYearPatterns] = useState<FiscalYearPatternOption[]>([]);
  const [monthlyPeriodPatterns, setMonthlyPeriodPatterns] = useState<MonthlyPeriodPatternOption[]>([]);
  const [showFyForm, setShowFyForm] = useState(false);
  const [showMpForm, setShowMpForm] = useState(false);

  useEffect(() => {
    if (!tenantId) return;
    let cancelled = false;

    const loadData = async () => {
      setLoading(true);
      try {
        const [fyPatterns, mpPatterns, defaults] = await Promise.all([
          api.admin.patterns.listFiscalYearPatterns(tenantId),
          api.admin.patterns.listMonthlyPeriodPatterns(tenantId),
          api.admin.tenantSettings.getDefaultPatterns(),
        ]);
        if (cancelled) return;
        setFiscalYearPatterns(fyPatterns);
        setMonthlyPeriodPatterns(mpPatterns);
        setDefaultFyPatternId(defaults.defaultFiscalYearPatternId ?? "");
        setDefaultMpPatternId(defaults.defaultMonthlyPeriodPatternId ?? "");
      } catch (err: unknown) {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 403) {
          toast.error(t("loadForbidden"));
        } else {
          toast.error(t("loadError"));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadData();
    return () => {
      cancelled = true;
    };
  }, [tenantId, toast, t]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t from useTranslations is stable
  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      await api.admin.tenantSettings.updateDefaultPatterns({
        defaultFiscalYearPatternId: defaultFyPatternId || null,
        defaultMonthlyPeriodPatternId: defaultMpPatternId || null,
      });
      toast.success(t("defaultPatterns.saved"));
    } catch (err: unknown) {
      toast.error(err instanceof ApiError ? err.message : t("defaultPatterns.saveError"));
    } finally {
      setSaving(false);
    }
  }, [defaultFyPatternId, defaultMpPatternId, toast]);

  const handleFyPatternCreated = useCallback(
    (pattern: { id: string; name: string; startMonth: number; startDay: number }) => {
      setFiscalYearPatterns((prev) => [...prev, { ...pattern, tenantId: tenantId ?? "" }]);
      setShowFyForm(false);
      toast.success(t("patterns.fiscalYear.created"));
    },
    [tenantId, toast, t],
  );

  const handleMpPatternCreated = useCallback(
    (pattern: { id: string; name: string; startDay: number }) => {
      setMonthlyPeriodPatterns((prev) => [...prev, { ...pattern, tenantId: tenantId ?? "" }]);
      setShowMpForm(false);
      toast.success(t("patterns.monthlyPeriod.created"));
    },
    [tenantId, toast, t],
  );

  if (loading) {
    return <p className="text-gray-500">{tc("loading")}</p>;
  }

  return (
    <>
      <h1 className="text-2xl font-bold text-gray-900 mt-4 mb-2">{t("title")}</h1>
      <p className="text-sm text-gray-600 mb-6">{t("description")}</p>

      <div className="space-y-6">
        {/* Default patterns card */}
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-lg font-semibold text-gray-800 mb-2">{t("defaultPatterns.title")}</h2>
          <p className="text-sm text-gray-600 mb-4">{t("defaultPatterns.description")}</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="ts-default-fy" className="block text-sm font-medium text-gray-700 mb-1">
                {t("defaultPatterns.fiscalYearPattern")}
              </label>
              <select
                id="ts-default-fy"
                value={defaultFyPatternId}
                onChange={(e) => setDefaultFyPatternId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t("defaultPatterns.noPattern")}</option>
                {fiscalYearPatterns.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.startMonth}/{p.startDay})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="ts-default-mp" className="block text-sm font-medium text-gray-700 mb-1">
                {t("defaultPatterns.monthlyPeriodPattern")}
              </label>
              <select
                id="ts-default-mp"
                value={defaultMpPatternId}
                onChange={(e) => setDefaultMpPatternId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">{t("defaultPatterns.noPattern")}</option>
                {monthlyPeriodPatterns.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.startDay})
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex justify-end mt-4">
            <button
              type="button"
              onClick={handleSave}
              disabled={saving}
              className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {saving ? tc("saving") : tc("save")}
            </button>
          </div>
        </div>

        {/* Pattern lists */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-md font-semibold text-gray-900">{t("patterns.fiscalYear.title")}</h3>
              <button
                type="button"
                onClick={() => setShowFyForm(true)}
                className="px-3 py-1.5 text-xs text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50"
              >
                {t("patterns.fiscalYear.createNew")}
              </button>
            </div>
            {fiscalYearPatterns.length === 0 ? (
              <p className="text-sm text-gray-500">{t("patterns.fiscalYear.empty")}</p>
            ) : (
              <ul className="space-y-1">
                {fiscalYearPatterns.map((p) => (
                  <li key={p.id} className="text-sm text-gray-700 py-1 border-b border-gray-100 last:border-0">
                    {p.name} ({p.startMonth}/{p.startDay})
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-md font-semibold text-gray-900">{t("patterns.monthlyPeriod.title")}</h3>
              <button
                type="button"
                onClick={() => setShowMpForm(true)}
                className="px-3 py-1.5 text-xs text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50"
              >
                {t("patterns.monthlyPeriod.createNew")}
              </button>
            </div>
            {monthlyPeriodPatterns.length === 0 ? (
              <p className="text-sm text-gray-500">{t("patterns.monthlyPeriod.empty")}</p>
            ) : (
              <ul className="space-y-1">
                {monthlyPeriodPatterns.map((p) => (
                  <li key={p.id} className="text-sm text-gray-700 py-1 border-b border-gray-100 last:border-0">
                    {p.name} ({p.startDay})
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>

      {tenantId && (
        <>
          <FiscalYearPatternForm
            tenantId={tenantId}
            open={showFyForm}
            onClose={() => setShowFyForm(false)}
            onCreated={handleFyPatternCreated}
          />
          <MonthlyPeriodPatternForm
            tenantId={tenantId}
            open={showMpForm}
            onClose={() => setShowMpForm(false)}
            onCreated={handleMpPatternCreated}
          />
        </>
      )}
    </>
  );
}
