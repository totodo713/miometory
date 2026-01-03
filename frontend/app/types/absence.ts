// Absence types for recording time away from work

export enum AbsenceType {
  PAID_LEAVE = "PAID_LEAVE",
  SICK_LEAVE = "SICK_LEAVE",
  SPECIAL_LEAVE = "SPECIAL_LEAVE",
  OTHER = "OTHER",
}

export enum AbsenceStatus {
  DRAFT = "DRAFT",
  SUBMITTED = "SUBMITTED",
  APPROVED = "APPROVED",
  REJECTED = "REJECTED",
}

export interface Absence {
  id: string;
  memberId: string;
  date: string; // ISO date format (YYYY-MM-DD)
  hours: number;
  absenceType: AbsenceType;
  reason?: string;
  status: AbsenceStatus;
  recordedBy: string;
  createdAt: string; // ISO timestamp
  updatedAt: string; // ISO timestamp
  version: number;
}

export interface CreateAbsenceRequest {
  memberId: string;
  date: string;
  hours: number;
  absenceType: AbsenceType;
  reason?: string;
  recordedBy?: string;
}

export interface UpdateAbsenceRequest {
  hours: number;
  absenceType: AbsenceType;
  reason?: string;
}

// Display labels for absence types
export const AbsenceTypeLabels: Record<AbsenceType, string> = {
  [AbsenceType.PAID_LEAVE]: "Paid Leave",
  [AbsenceType.SICK_LEAVE]: "Sick Leave",
  [AbsenceType.SPECIAL_LEAVE]: "Special Leave",
  [AbsenceType.OTHER]: "Other",
};

// Color coding for absence types (for UI)
export const AbsenceTypeColors: Record<AbsenceType, string> = {
  [AbsenceType.PAID_LEAVE]: "blue",
  [AbsenceType.SICK_LEAVE]: "orange",
  [AbsenceType.SPECIAL_LEAVE]: "purple",
  [AbsenceType.OTHER]: "gray",
};
