"use client";

import { useTranslations } from "next-intl";
import { Fragment, useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { ApiError, api, ForbiddenError } from "@/services/api";
import type { HolidayCalendarPresetRow, HolidayEntryRow } from "@/types/masterData";

interface HolidayCalendarPresetListProps {
  onEdit: (calendar: HolidayCalendarPresetRow) => void;
  onDeactivate: (calendar: HolidayCalendarPresetRow) => void;
  onActivate: (calendar: HolidayCalendarPresetRow) => void;
  onForbidden: () => void;
  onAddEntry: (calendarId: string) => void;
  onEditEntry: (calendarId: string, entry: HolidayEntryRow) => void;
  onDeleteEntry: (calendarId: string, entryId: string) => void;
  refreshKey: number;
}

function formatEntryDate(
  entry: HolidayEntryRow,
  t: (key: string, values?: Record<string, string>) => string,
  tMonth: (key: string) => string,
  tNth: (key: string) => string,
  tWeekday: (key: string) => string,
): string {
  if (entry.entryType === "FIXED") {
    return `${tMonth(String(entry.month))} ${entry.day}`;
  }
  const nth = tNth(String(entry.nthOccurrence));
  const weekday = tWeekday(String(entry.dayOfWeek));
  const month = tMonth(String(entry.month));
  return t("holidayCalendar.nthWeekdayFormat", { nth, weekday, month });
}

export function HolidayCalendarPresetList({
  onEdit,
  onDeactivate,
  onActivate,
  onForbidden,
  onAddEntry,
  onEditEntry,
  onDeleteEntry,
  refreshKey,
}: HolidayCalendarPresetListProps) {
  const t = useTranslations("admin.masterData");
  const tc = useTranslations("common");
  const tMonth = useTranslations("admin.masterData.fiscalYear.months");
  const tNth = useTranslations("admin.masterData.holidayCalendar.nthLabels");
  const tWeekday = useTranslations("admin.masterData.holidayCalendar.weekdays");
  const [items, setItems] = useState<HolidayCalendarPresetRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expandedCalendarId, setExpandedCalendarId] = useState<string | null>(null);
  const [entries, setEntries] = useState<HolidayEntryRow[]>([]);
  const [entriesLoading, setEntriesLoading] = useState(false);
  const isMobile = useMediaQuery("(max-width: 767px)");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: tc from useTranslations is stable
  const loadItems = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await api.admin.masterData.holidayCalendars.list({
        page,
        size: 20,
        search: debouncedSearch || undefined,
        isActive: showInactive ? undefined : true,
      });
      setItems(result.content);
      setTotalPages(result.totalPages);
    } catch (err: unknown) {
      if (err instanceof ForbiddenError) {
        onForbidden();
        return;
      }
      setLoadError(err instanceof ApiError ? err.message : tc("fetchError"));
    } finally {
      setIsLoading(false);
    }
  }, [page, debouncedSearch, showInactive, onForbidden]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey is needed to trigger refresh
  useEffect(() => {
    loadItems();
  }, [loadItems, refreshKey]);

  const loadEntries = useCallback(async (calendarId: string) => {
    setEntriesLoading(true);
    try {
      const result = await api.admin.masterData.holidayCalendars.listEntries(calendarId);
      setEntries(result);
    } catch {
      setEntries([]);
    } finally {
      setEntriesLoading(false);
    }
  }, []);

  const handleToggleExpand = useCallback(
    (calendarId: string) => {
      if (expandedCalendarId === calendarId) {
        setExpandedCalendarId(null);
        setEntries([]);
      } else {
        setExpandedCalendarId(calendarId);
        loadEntries(calendarId);
      }
    },
    [expandedCalendarId, loadEntries],
  );

  // Reload entries when refreshKey changes and a calendar is expanded
  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey triggers entry reload
  useEffect(() => {
    if (expandedCalendarId) {
      loadEntries(expandedCalendarId);
    }
  }, [refreshKey, expandedCalendarId, loadEntries]);

  const hasFilters = !!debouncedSearch || showInactive;

  const renderEntryTypeBadge = (entryType: "FIXED" | "NTH_WEEKDAY") => (
    <span
      className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
        entryType === "FIXED" ? "bg-blue-100 text-blue-800" : "bg-purple-100 text-purple-800"
      }`}
    >
      {entryType === "FIXED" ? t("holidayCalendar.entryFixed") : t("holidayCalendar.entryNthWeekday")}
    </span>
  );

  const renderEntriesTable = (calendarId: string) => {
    if (entriesLoading) {
      return (
        <div className="px-8 py-4">
          <Skeleton.Table rows={3} cols={4} />
        </div>
      );
    }

    return (
      <div className="px-8 py-4 bg-gray-50">
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-sm font-medium text-gray-700">{t("holidayCalendar.entries")}</h4>
          <button
            type="button"
            onClick={() => onAddEntry(calendarId)}
            className="text-blue-600 hover:text-blue-800 text-xs font-medium"
          >
            + {t("holidayCalendar.addEntry")}
          </button>
        </div>
        {entries.length === 0 ? (
          <p className="text-sm text-gray-500 py-2">{t("noItemsYet")}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-2 px-3 font-medium text-gray-600 text-xs">
                  {t("holidayCalendar.entryName")}
                </th>
                <th className="text-left py-2 px-3 font-medium text-gray-600 text-xs">
                  {t("holidayCalendar.entryType")}
                </th>
                <th className="text-left py-2 px-3 font-medium text-gray-600 text-xs">{t("holidayCalendar.day")}</th>
                <th className="text-right py-2 px-3 font-medium text-gray-600 text-xs">
                  {t("holidayCalendar.actions")}
                </th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id} className="border-b border-gray-100">
                  <td className="py-2 px-3">{entry.name}</td>
                  <td className="py-2 px-3">{renderEntryTypeBadge(entry.entryType)}</td>
                  <td className="py-2 px-3 text-gray-600">
                    {formatEntryDate(entry, t, tMonth, tNth, tWeekday)}
                    {entry.specificYear ? ` (${entry.specificYear})` : ""}
                  </td>
                  <td className="py-2 px-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => onEditEntry(calendarId, entry)}
                        className="text-blue-600 hover:text-blue-800 text-xs"
                      >
                        {tc("edit")}
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteEntry(calendarId, entry.id)}
                        className="text-red-600 hover:text-red-800 text-xs"
                      >
                        {tc("delete")}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    );
  };

  const renderMobileEntries = (calendarId: string) => {
    if (entriesLoading) {
      return (
        <div className="mt-3 pt-3 border-t border-gray-200">
          <Skeleton.Table rows={2} cols={2} />
        </div>
      );
    }

    return (
      <div className="mt-3 pt-3 border-t border-gray-200">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs font-medium text-gray-700">{t("holidayCalendar.entries")}</span>
          <button
            type="button"
            onClick={() => onAddEntry(calendarId)}
            className="text-blue-600 hover:text-blue-800 text-xs font-medium"
          >
            + {t("holidayCalendar.addEntry")}
          </button>
        </div>
        {entries.length === 0 ? (
          <p className="text-xs text-gray-500">{t("noItemsYet")}</p>
        ) : (
          <div className="space-y-2">
            {entries.map((entry) => (
              <div key={entry.id} className="bg-white border border-gray-100 rounded p-3 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{entry.name}</span>
                  {renderEntryTypeBadge(entry.entryType)}
                </div>
                <p className="text-xs text-gray-600">
                  {formatEntryDate(entry, t, tMonth, tNth, tWeekday)}
                  {entry.specificYear ? ` (${entry.specificYear})` : ""}
                </p>
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => onEditEntry(calendarId, entry)}
                    className="text-blue-600 hover:text-blue-800 text-xs"
                  >
                    {tc("edit")}
                  </button>
                  <button
                    type="button"
                    onClick={() => onDeleteEntry(calendarId, entry.id)}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    {tc("delete")}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <input
          type="text"
          placeholder={t("searchPlaceholder")}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label={t("searchLabel")}
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <label className="flex items-center gap-2 text-sm text-gray-600 whitespace-nowrap">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          {t("showInactive")}
        </label>
      </div>

      {loadError ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-center">
          <p className="text-sm text-red-800">{loadError}</p>
          <button type="button" onClick={loadItems} className="mt-2 text-sm text-red-600 hover:text-red-800 underline">
            {tc("retry")}
          </button>
        </div>
      ) : isLoading ? (
        <Skeleton.Table rows={5} cols={7} />
      ) : items.length === 0 ? (
        <EmptyState title={t("notFound")} description={hasFilters ? t("changeFilter") : t("noItemsYet")} />
      ) : isMobile ? (
        <div className="space-y-3">
          {items.map((calendar) => (
            <div key={calendar.id} className="border border-gray-200 rounded-lg p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{calendar.name}</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    calendar.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {calendar.isActive ? tc("active") : tc("inactive")}
                </span>
              </div>
              {calendar.description && <p className="text-xs text-gray-500 truncate">{calendar.description}</p>}
              <p className="text-xs text-gray-500">
                {t("holidayCalendar.country")}: {calendar.country || "\u2014"}
              </p>
              <p className="text-xs text-gray-500">
                {t("holidayCalendar.entryCount")}: {calendar.entryCount}
              </p>
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => handleToggleExpand(calendar.id)}
                  className="text-gray-600 hover:text-gray-800 text-xs"
                >
                  {expandedCalendarId === calendar.id ? "\u25B2" : "\u25BC"} {t("holidayCalendar.entries")}
                </button>
                <button
                  type="button"
                  onClick={() => onEdit(calendar)}
                  className="text-blue-600 hover:text-blue-800 text-xs"
                >
                  {tc("edit")}
                </button>
                {calendar.isActive ? (
                  <button
                    type="button"
                    onClick={() => onDeactivate(calendar)}
                    className="text-red-600 hover:text-red-800 text-xs"
                  >
                    {tc("disable")}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onActivate(calendar)}
                    className="text-green-600 hover:text-green-800 text-xs"
                  >
                    {tc("enable")}
                  </button>
                )}
              </div>
              {expandedCalendarId === calendar.id && renderMobileEntries(calendar.id)}
            </div>
          ))}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="w-8 py-3 px-2" />
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.name")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.description")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.country")}</th>
                <th className="text-center py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.entryCount")}</th>
                <th className="text-left py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.status")}</th>
                <th className="text-right py-3 px-4 font-medium text-gray-700">{t("holidayCalendar.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((calendar) => (
                <Fragment key={calendar.id}>
                  <tr className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="py-3 px-2 text-center">
                      <button
                        type="button"
                        onClick={() => handleToggleExpand(calendar.id)}
                        className="text-gray-400 hover:text-gray-600 text-xs"
                        aria-label={
                          expandedCalendarId === calendar.id
                            ? t("holidayCalendar.collapseEntries")
                            : t("holidayCalendar.expandEntries")
                        }
                      >
                        {expandedCalendarId === calendar.id ? "\u25B2" : "\u25BC"}
                      </button>
                    </td>
                    <td className="py-3 px-4">{calendar.name}</td>
                    <td className="py-3 px-4 text-gray-600 text-xs max-w-[200px] truncate">
                      {calendar.description || "\u2014"}
                    </td>
                    <td className="py-3 px-4">{calendar.country || "\u2014"}</td>
                    <td className="py-3 px-4 text-center">{calendar.entryCount}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                          calendar.isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {calendar.isActive ? tc("active") : tc("inactive")}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => onEdit(calendar)}
                          className="text-blue-600 hover:text-blue-800 text-xs"
                        >
                          {tc("edit")}
                        </button>
                        {calendar.isActive ? (
                          <button
                            type="button"
                            onClick={() => onDeactivate(calendar)}
                            className="text-red-600 hover:text-red-800 text-xs"
                          >
                            {tc("disable")}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => onActivate(calendar)}
                            className="text-green-600 hover:text-green-800 text-xs"
                          >
                            {tc("enable")}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {expandedCalendarId === calendar.id && (
                    <tr>
                      <td colSpan={7} className="p-0">
                        {renderEntriesTable(calendar.id)}
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 text-sm border rounded disabled:opacity-50"
          >
            {tc("previous")}
          </button>
          <span className="px-3 py-1 text-sm text-gray-600">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="px-3 py-1 text-sm border rounded disabled:opacity-50"
          >
            {tc("next")}
          </button>
        </div>
      )}
    </div>
  );
}
