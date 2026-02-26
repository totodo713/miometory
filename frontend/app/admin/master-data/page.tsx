"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import { FiscalYearPresetForm } from "@/components/admin/FiscalYearPresetForm";
import { FiscalYearPresetList } from "@/components/admin/FiscalYearPresetList";
import { HolidayCalendarPresetForm } from "@/components/admin/HolidayCalendarPresetForm";
import { HolidayCalendarPresetList } from "@/components/admin/HolidayCalendarPresetList";
import { HolidayEntryForm } from "@/components/admin/HolidayEntryForm";
import type { TabType } from "@/components/admin/MasterDataTabs";
import { MasterDataTabs } from "@/components/admin/MasterDataTabs";
import { MonthlyPeriodPresetForm } from "@/components/admin/MonthlyPeriodPresetForm";
import { MonthlyPeriodPresetList } from "@/components/admin/MonthlyPeriodPresetList";
import { AccessDenied } from "@/components/shared/AccessDenied";
import { Breadcrumbs } from "@/components/shared/Breadcrumbs";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { useToast } from "@/hooks/useToast";
import { ApiError, api } from "@/services/api";
import type {
  FiscalYearPresetRow,
  HolidayCalendarPresetRow,
  HolidayEntryRow,
  MonthlyPeriodPresetRow,
} from "@/types/masterData";

type EditingItem = FiscalYearPresetRow | MonthlyPeriodPresetRow | HolidayCalendarPresetRow;

export default function AdminMasterDataPage() {
  const t = useTranslations("admin.masterData");
  const tc = useTranslations("common");
  const tb = useTranslations("breadcrumbs");
  const toast = useToast();

  const [activeTab, setActiveTab] = useState<TabType>("fiscal-year");
  const [editingItem, setEditingItem] = useState<EditingItem | null>(null);
  const [editingEntry, setEditingEntry] = useState<{ calendarId: string; entry: HolidayEntryRow } | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [showEntryForm, setShowEntryForm] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [confirmTarget, setConfirmTarget] = useState<{ type: "deactivate" | "activate"; item: EditingItem } | null>(
    null,
  );
  const [confirmEntryDelete, setConfirmEntryDelete] = useState<{ calendarId: string; entryId: string } | null>(null);
  const [isForbidden, setIsForbidden] = useState(false);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  // --- Deactivate / Activate ---
  const handleDeactivate = useCallback((item: EditingItem) => {
    setConfirmTarget({ type: "deactivate", item });
  }, []);

  const handleActivate = useCallback((item: EditingItem) => {
    setConfirmTarget({ type: "activate", item });
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const executeAction = useCallback(
    async (target: { type: "deactivate" | "activate"; item: EditingItem }) => {
      try {
        const apiMap = {
          "fiscal-year": api.admin.masterData.fiscalYearPresets,
          "monthly-period": api.admin.masterData.monthlyPeriodPresets,
          "holiday-calendar": api.admin.masterData.holidayCalendars,
        } as const;
        const service = apiMap[activeTab];
        if (target.type === "deactivate") {
          await service.deactivate(target.item.id);
          toast.success(t("deactivated"));
        } else {
          await service.activate(target.item.id);
          toast.success(t("activated"));
        }
        refresh();
      } catch (err: unknown) {
        toast.error(err instanceof ApiError ? err.message : tc("error"));
      }
    },
    [activeTab, refresh, toast],
  );

  // --- Edit ---
  const handleEdit = useCallback((item: EditingItem) => {
    setEditingItem(item);
    setShowForm(true);
  }, []);

  const handleSaved = useCallback(() => {
    setShowForm(false);
    setEditingItem(null);
    refresh();
  }, [refresh]);

  const handleClose = useCallback(() => {
    setShowForm(false);
    setEditingItem(null);
  }, []);

  // --- Holiday entry callbacks ---
  const handleAddEntry = useCallback((calendarId: string) => {
    setEditingEntry({ calendarId, entry: null as unknown as HolidayEntryRow });
    setShowEntryForm(true);
  }, []);

  const handleEditEntry = useCallback((calendarId: string, entry: HolidayEntryRow) => {
    setEditingEntry({ calendarId, entry });
    setShowEntryForm(true);
  }, []);

  const handleDeleteEntry = useCallback((calendarId: string, entryId: string) => {
    setConfirmEntryDelete({ calendarId, entryId });
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: t/tc from useTranslations are stable
  const executeDeleteEntry = useCallback(
    async (target: { calendarId: string; entryId: string }) => {
      try {
        await api.admin.masterData.holidayCalendars.deleteEntry(target.calendarId, target.entryId);
        toast.success(t("deleted"));
        refresh();
      } catch (err: unknown) {
        toast.error(err instanceof ApiError ? err.message : tc("error"));
      }
    },
    [refresh, toast],
  );

  const handleEntrySaved = useCallback(() => {
    setShowEntryForm(false);
    setEditingEntry(null);
    refresh();
  }, [refresh]);

  const handleEntryClose = useCallback(() => {
    setShowEntryForm(false);
    setEditingEntry(null);
  }, []);

  // --- Create button label per tab ---
  const createButtonLabel = {
    "fiscal-year": t("fiscalYear.createTitle"),
    "monthly-period": t("monthlyPeriod.createTitle"),
    "holiday-calendar": t("holidayCalendar.createTitle"),
  }[activeTab];

  if (isForbidden) {
    return <AccessDenied />;
  }

  return (
    <div>
      <Breadcrumbs items={[{ label: tb("admin"), href: "/admin" }, { label: tb("masterData") }]} />

      <div className="flex items-center justify-between mb-6 mt-4">
        <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
        <button
          type="button"
          onClick={() => {
            setEditingItem(null);
            setShowForm(true);
          }}
          className="px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
        >
          {createButtonLabel}
        </button>
      </div>

      <MasterDataTabs activeTab={activeTab} onTabChange={setActiveTab} />

      <div className="bg-white rounded-lg border border-gray-200 p-4">
        {activeTab === "fiscal-year" && (
          <FiscalYearPresetList
            onEdit={handleEdit}
            onDeactivate={handleDeactivate}
            onActivate={handleActivate}
            refreshKey={refreshKey}
            onForbidden={() => setIsForbidden(true)}
          />
        )}
        {activeTab === "monthly-period" && (
          <MonthlyPeriodPresetList
            onEdit={handleEdit}
            onDeactivate={handleDeactivate}
            onActivate={handleActivate}
            refreshKey={refreshKey}
            onForbidden={() => setIsForbidden(true)}
          />
        )}
        {activeTab === "holiday-calendar" && (
          <HolidayCalendarPresetList
            onEdit={handleEdit}
            onDeactivate={handleDeactivate}
            onActivate={handleActivate}
            onAddEntry={handleAddEntry}
            onEditEntry={handleEditEntry}
            onDeleteEntry={handleDeleteEntry}
            refreshKey={refreshKey}
            onForbidden={() => setIsForbidden(true)}
          />
        )}
      </div>

      {/* Form modals per tab */}
      {showForm && activeTab === "fiscal-year" && (
        <FiscalYearPresetForm
          preset={editingItem as FiscalYearPresetRow | null}
          onClose={handleClose}
          onSaved={handleSaved}
        />
      )}
      {showForm && activeTab === "monthly-period" && (
        <MonthlyPeriodPresetForm
          preset={editingItem as MonthlyPeriodPresetRow | null}
          onClose={handleClose}
          onSaved={handleSaved}
        />
      )}
      {showForm && activeTab === "holiday-calendar" && (
        <HolidayCalendarPresetForm
          preset={editingItem as HolidayCalendarPresetRow | null}
          onClose={handleClose}
          onSaved={handleSaved}
        />
      )}

      {/* Holiday entry form modal */}
      {showEntryForm && editingEntry && (
        <HolidayEntryForm
          calendarId={editingEntry.calendarId}
          entry={editingEntry.entry || null}
          onClose={handleEntryClose}
          onSaved={handleEntrySaved}
        />
      )}

      {/* Confirm deactivate/activate dialog */}
      <ConfirmDialog
        open={confirmTarget !== null}
        title={tc("confirm")}
        message={confirmTarget?.type === "deactivate" ? t("confirmDeactivate") : t("confirmActivate")}
        confirmLabel={confirmTarget?.type === "deactivate" ? tc("disable") : tc("enable")}
        variant={confirmTarget?.type === "deactivate" ? "danger" : "warning"}
        onConfirm={async () => {
          if (confirmTarget) await executeAction(confirmTarget);
          setConfirmTarget(null);
        }}
        onCancel={() => setConfirmTarget(null)}
      />

      {/* Confirm entry delete dialog */}
      <ConfirmDialog
        open={confirmEntryDelete !== null}
        title={tc("confirm")}
        message={t("confirmDeleteEntry")}
        confirmLabel={tc("delete")}
        variant="danger"
        onConfirm={async () => {
          if (confirmEntryDelete) await executeDeleteEntry(confirmEntryDelete);
          setConfirmEntryDelete(null);
        }}
        onCancel={() => setConfirmEntryDelete(null)}
      />
    </div>
  );
}
