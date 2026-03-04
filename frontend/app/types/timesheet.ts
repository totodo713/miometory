export type PeriodType = "calendar" | "fiscal";

export interface TimesheetRow {
  date: string;
  dayOfWeek: string;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  startTime: string | null; // "HH:mm"
  endTime: string | null; // "HH:mm"
  workingHours: number;
  remarks: string | null;
  defaultStartTime: string | null;
  defaultEndTime: string | null;
  hasAttendanceRecord: boolean;
  attendanceId: string | null;
  attendanceVersion: number;
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
  periodType: PeriodType;
  periodStart: string;
  periodEnd: string;
  canEdit: boolean;
  rows: TimesheetRow[];
  summary: TimesheetSummary;
}

export interface SaveAttendanceRequest {
  memberId?: string;
  date: string;
  startTime: string | null;
  endTime: string | null;
  remarks: string | null;
  version: number;
}
