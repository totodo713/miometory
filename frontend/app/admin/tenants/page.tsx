"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { FiscalYearPatternForm } from "@/components/admin/FiscalYearPatternForm";
import { MonthlyPeriodPatternForm } from "@/components/admin/MonthlyPeriodPatternForm";
import { PermissionBadge } from "@/components/admin/PermissionBadge";
import { TenantForm } from "@/components/admin/TenantForm";
import type { TenantRow } from "@/components/admin/TenantList";
import { TenantList } from "@/components/admin/TenantList";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { useToast } from "@/hooks/useToast";
import type { FiscalYearPatternOption, MonthlyPeriodPatternOption } from "@/services/api";
import { ApiError, api } from "@/services/api";

export default function AdminTenantsPage() {
  const t = useTranslations("admin.tenants");
  const td = useTranslations("admin.tenantDefaultPatterns");
  const tc = useTranslations("common");
  const tb = useTranslations("breadcrumbs");
  const toast = useToast();
  const [editingTenant, setEditingTenant] = useState<TenantRow | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [confirmTarget, setConfirmTarget] = useState<{ id: string; action: "deactivate" | "activate" } | null>(null);

  // Default patterns state
  const [selectedTenant, setSelectedTenant] = useState<TenantRow | null>(null);
  const [fiscalYearPatterns, setFiscalYearPatterns] = useState<FiscalYearPatternOption[]>([]);
  const [monthlyPeriodPatterns, setMonthlyPeriodPatterns] = useState<MonthlyPeriodPatternOption[]>([]);
  const [defaultFyPatternId, setDefaultFyPatternId] = useState<string>("");
  const [defaultMpPatternId, setDefaultMpPatternId] = useState<string>("");
  const [isPatternsLoading, setIsPatternsLoading] = useState(false);
  const [isPatternSaving, setIsPatternSaving] = useState(false);
  const [showFyForm, setShowFyForm] = useState(false);
  const [showMpForm, setShowMpForm] = useState(false);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  const handleDeactivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "deactivate" });
  }, []);

  const handleActivate = useCallback((id: string) => {
    setConfirmTarget({ id, action: "activate" });
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const executeAction = useCallback(
    async (target: { id: string; action: "deactivate" | "activate" }) => {
      try {
        if (target.action === "deactivate") {
          await api.admin.tenants.deactivate(target.id);
          toast.success(t("deactivated"));
        } else {
          await api.admin.tenants.activate(target.id);
          toast.success(t("activated"));
        }
        refresh();
      } catch (err: unknown) {
        toast.error(err instanceof ApiError ? err.message : tc("error"));
      }
    },
    [refresh, toast],
  );

  const handleEdit = useCallback((tenant: TenantRow) => {
    setEditingTenant(tenant);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingTenant(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingTenant(null);
  }, []);

  const handleSelectTenant = useCallback((tenant: TenantRow) => {
    setSelectedTenant(tenant);
  }, []);

  const handleBackToList = useCallback(() => {
    setSelectedTenant(null);
    setFiscalYearPatterns([]);
    setMonthlyPeriodPatterns([]);
    setDefaultFyPatternId("");
    setDefaultMpPatternId("");
  }, []);

  // Load patterns and default settings when tenant is selected
  useEffect(() => {
    if (!selectedTenant) return;
    let cancelled = false;

    const loadData = async () => {
      setIsPatternsLoading(true);
      try {
        const [fyPatterns, mpPatterns, defaults] = await Promise.all([
          api.admin.patterns.listFiscalYearPatterns(selectedTenant.id),
          api.admin.patterns.listMonthlyPeriodPatterns(selectedTenant.id),
          api.admin.tenants.getDefaultPatterns(selectedTenant.id),
        ]);
        if (cancelled) return;
        setFiscalYearPatterns(fyPatterns);
        setMonthlyPeriodPatterns(mpPatterns);
        setDefaultFyPatternId(defaults.defaultFiscalYearPatternId ?? "");
        setDefaultMpPatternId(defaults.defaultMonthlyPeriodPatternId ?? "");
      } catch {
        if (cancelled) return;
        setFiscalYearPatterns([]);
        setMonthlyPeriodPatterns([]);
      } finally {
        if (!cancelled) setIsPatternsLoading(false);
      }
    };

    loadData();
    return () => {
      cancelled = true;
    };
  }, [selectedTenant]);

  const handleFyPatternCreated = useCallback(
    (pattern: { id: string; name: string; startMonth: number; startDay: number }) => {
      setFiscalYearPatterns((prev) => [...prev, { ...pattern, tenantId: selectedTenant?.id ?? "" }]);
      setShowFyForm(false);
    },
    [selectedTenant],
  );

  const handleMpPatternCreated = useCallback(
    (pattern: { id: string; name: string; startDay: number }) => {
      setMonthlyPeriodPatterns((prev) => [...prev, { ...pattern, tenantId: selectedTenant?.id ?? "" }]);
      setShowMpForm(false);
    },
    [selectedTenant],
  );

  // biome-ignore lint/correctness/useExhaustiveDependencies: td/tc from useTranslations are stable
  const handleDefaultPatternSave = useCallback(async () => {
    if (!selectedTenant) return;
    setIsPatternSaving(true);
    try {
      await api.admin.tenants.updateDefaultPatterns(selectedTenant.id, {
        defaultFiscalYearPatternId: defaultFyPatternId || null,
        defaultMonthlyPeriodPatternId: defaultMpPatternId || null,
      });
      toast.success(td("saved"));
    } catch (err: unknown) {
      toast.error(err instanceof ApiError ? err.message : td("saveError"));
    } finally {
      setIsPatternSaving(false);
    }
  }, [selectedTenant, defaultFyPatternId, defaultMpPatternId, toast]);

  return (
    <div>
      <Breadcrumbs items={[{ label: tb("admin"), href: "/admin" }, { label: tb("tenants") }]} />

      <div className="flex items-center justify-between mb-6 mt-4">
        <div className="flex items-center gap-3">
          {selectedTenant && (
            <button
              type="button"
              onClick={handleBackToList}
              className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50"
            >
              {tc("back")}
            </button>
          )}
          <h1 className="text-2xl font-bold text-gray-900">{selectedTenant ? selectedTenant.name : t("title")}</h1>
          {!selectedTenant && <PermissionBadge editPermission="tenant.update" />}
        </div>
        {!selectedTenant && (
          <button
            type="button"
            onClick={() => {
              setEditingTenant(null);
              setShowForm(true);
            }}
            className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
          >
            {t("addTenant")}
          </button>
        )}
      </div>

      {!selectedTenant ? (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <TenantList
            onEdit={handleEdit}
            onDeactivate={handleDeactivate}
            onActivate={handleActivate}
            onSelectTenant={handleSelectTenant}
            refreshKey={refreshKey}
          />
        </div>
      ) : (
        <div className="space-y-4">
          {/* Default patterns section */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">{td("title")}</h2>
            <p className="text-sm text-gray-600 mb-4">{td("description")}</p>
            {isPatternsLoading ? (
              <div className="text-sm text-gray-500">{tc("loading")}</div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="default-fy-pattern" className="block text-sm font-medium text-gray-700 mb-1">
                      {td("fiscalYearPattern")}
                    </label>
                    <select
                      id="default-fy-pattern"
                      value={defaultFyPatternId}
                      onChange={(e) => setDefaultFyPatternId(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">{td("noPattern")}</option>
                      {fiscalYearPatterns.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} ({p.startMonth}/{p.startDay})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="default-mp-pattern" className="block text-sm font-medium text-gray-700 mb-1">
                      {td("monthlyPeriodPattern")}
                    </label>
                    <select
                      id="default-mp-pattern"
                      value={defaultMpPatternId}
                      onChange={(e) => setDefaultMpPatternId(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">{td("noPattern")}</option>
                      {monthlyPeriodPatterns.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} ({p.startDay})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={handleDefaultPatternSave}
                    disabled={isPatternSaving}
                    className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
                  >
                    {isPatternSaving ? tc("saving") : tc("save")}
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Pattern creation section */}
          {!isPatternsLoading && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-md font-semibold text-gray-900">{t("form.fiscalYearPattern")}</h3>
                  <button
                    type="button"
                    onClick={() => setShowFyForm(true)}
                    className="px-3 py-1.5 text-xs text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50"
                  >
                    {tc("create")}
                  </button>
                </div>
                {fiscalYearPatterns.length === 0 ? (
                  <p className="text-sm text-gray-500">{tc("noData")}</p>
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

              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-md font-semibold text-gray-900">{t("form.monthlyPeriodPattern")}</h3>
                  <button
                    type="button"
                    onClick={() => setShowMpForm(true)}
                    className="px-3 py-1.5 text-xs text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50"
                  >
                    {tc("create")}
                  </button>
                </div>
                {monthlyPeriodPatterns.length === 0 ? (
                  <p className="text-sm text-gray-500">{tc("noData")}</p>
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
          )}
        </div>
      )}

      {showForm && <TenantForm tenant={editingTenant} onClose={handleClose} onSaved={handleSaved} />}

      {selectedTenant && (
        <>
          <FiscalYearPatternForm
            tenantId={selectedTenant.id}
            open={showFyForm}
            onClose={() => setShowFyForm(false)}
            onCreated={handleFyPatternCreated}
          />
          <MonthlyPeriodPatternForm
            tenantId={selectedTenant.id}
            open={showMpForm}
            onClose={() => setShowMpForm(false)}
            onCreated={handleMpPatternCreated}
          />
        </>
      )}

      <ConfirmDialog
        open={confirmTarget !== null}
        title={tc("confirm")}
        message={confirmTarget?.action === "deactivate" ? t("confirmDeactivate") : t("confirmActivate")}
        confirmLabel={confirmTarget?.action === "deactivate" ? tc("disable") : tc("enable")}
        variant={confirmTarget?.action === "deactivate" ? "danger" : "warning"}
        onConfirm={async () => {
          if (confirmTarget) await executeAction(confirmTarget);
          setConfirmTarget(null);
        }}
        onCancel={() => setConfirmTarget(null)}
      />
    </div>
  );
}
