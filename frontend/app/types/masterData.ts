/**
 * TypeScript interfaces for Master Data Management.
 * Matches backend DTOs in AdminMasterDataService.
 */

export interface FiscalYearPresetRow {
  id: string;
  name: string;
  description: string | null;
  startMonth: number;
  startDay: number;
  isActive: boolean;
}

export interface MonthlyPeriodPresetRow {
  id: string;
  name: string;
  description: string | null;
  startDay: number;
  isActive: boolean;
}

export interface HolidayCalendarPresetRow {
  id: string;
  name: string;
  description: string | null;
  country: string | null;
  isActive: boolean;
  entryCount: number;
}

export interface HolidayEntryRow {
  id: string;
  name: string;
  nameJa: string | null;
  entryType: "FIXED" | "NTH_WEEKDAY";
  month: number;
  day: number | null;
  nthOccurrence: number | null;
  dayOfWeek: number | null;
  specificYear: number | null;
}

export interface PresetPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
