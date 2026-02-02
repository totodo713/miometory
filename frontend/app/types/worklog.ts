/**
 * TypeScript type definitions for Work-Log Entry feature
 *
 * Mirrors the backend DTOs and domain model.
 */

export type WorkLogStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";

export type DailyStatus = WorkLogStatus | "MIXED";

/**
 * Work log entry response from backend
 */
export interface WorkLogEntry {
  id: string;
  memberId: string;
  projectId: string;
  date: string; // ISO date format (YYYY-MM-DD)
  hours: number;
  comment: string | null;
  status: WorkLogStatus;
  enteredBy: string;
  createdAt: string; // ISO timestamp
  updatedAt: string; // ISO timestamp
  version: number;
}

/**
 * Request to create a new work log entry
 */
export interface CreateWorkLogEntryRequest {
  memberId: string;
  projectId: string;
  date: string; // ISO date format (YYYY-MM-DD)
  hours: number;
  comment?: string;
  enteredBy?: string;
}

/**
 * Request to update a work log entry (partial)
 */
export interface PatchWorkLogEntryRequest {
  hours: number;
  comment?: string;
}

/**
 * List of work log entries with pagination
 */
export interface WorkLogEntriesResponse {
  entries: WorkLogEntry[];
  total: number;
}

/**
 * Single day in the calendar view
 */
export interface DailyCalendarEntry {
  date: string; // ISO date format (YYYY-MM-DD)
  totalWorkHours: number;
  totalAbsenceHours: number;
  status: DailyStatus;
  isWeekend: boolean;
  isHoliday: boolean;
  hasProxyEntries: boolean;
}

/**
 * Monthly calendar response
 */
export interface MonthlyCalendarResponse {
  memberId: string;
  memberName: string;
  periodStart: string; // ISO date format
  periodEnd: string; // ISO date format
  dates: DailyCalendarEntry[];
}

/**
 * Project reference (minimal info for dropdowns)
 */
export interface Project {
  id: string;
  code: string;
  name: string;
  isActive: boolean;
}

/**
 * Assigned project (from member's project assignments)
 */
export interface AssignedProject {
  id: string;
  code: string;
  name: string;
}

/**
 * Response for assigned projects endpoint
 */
export interface AssignedProjectsResponse {
  projects: AssignedProject[];
  count: number;
}

/**
 * Member reference (minimal info for dropdowns)
 */
export interface Member {
  id: string;
  employeeId: string;
  name: string;
}

/**
 * Local form state for daily entry form
 */
export interface DailyEntryFormData {
  date: string;
  entries: Array<{
    id?: string; // undefined for new entries
    projectId: string;
    hours: number;
    comment: string;
    version?: number;
  }>;
}

/**
 * Auto-save status
 */
export type AutoSaveStatus = "idle" | "saving" | "saved" | "error";

/**
 * Auto-save state
 */
export interface AutoSaveState {
  status: AutoSaveStatus;
  lastSavedAt: string | null; // ISO timestamp
  error: string | null;
}
