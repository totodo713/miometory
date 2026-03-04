/**
 * TypeScript type definitions for Monthly Timesheet feature
 *
 * Mirrors the backend DTOs:
 * - TimesheetResponse.java (TimesheetRow, TimesheetSummary)
 * - SaveAttendanceRequest.java
 */

export interface TimesheetRow {
  date: string;
  dayOfWeek: string;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  startTime: string | null;
  endTime: string | null;
  workingHours: number;
  remarks: string | null;
  defaultStartTime: string | null;
  defaultEndTime: string | null;
  hasAttendanceRecord: boolean;
  attendanceId: string | null;
  attendanceVersion: number | null;
}

export interface TimesheetSummary {
  totalWorkingHours: number;
  totalWorkingDays: number;
  totalBusinessDays: number;
}

export interface TimesheetResponse {
  memberId: string;
  memberName: string;
  projectId: string;
  projectName: string;
  periodType: "calendar" | "fiscal";
  periodStart: string;
  periodEnd: string;
  rows: TimesheetRow[];
  summary: TimesheetSummary;
}

export interface SaveAttendanceRequest {
  date: string;
  startTime: string | null;
  endTime: string | null;
  remarks: string | null;
  version: number | null;
}

export type PeriodType = "calendar" | "fiscal";
