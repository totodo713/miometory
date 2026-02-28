/**
 * Member CSV Bulk Import Service
 *
 * Provides methods for:
 * - Downloading member CSV template
 * - Running dry-run validation
 * - Executing member import
 */

import { getCsrfToken } from "./csrf";

const API_BASE_URL = (() => {
  const url = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  try {
    const parsed = new URL(url);
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    return url.replace(/\/+$/, "");
  }
})();

export interface MemberCsvDryRunRow {
  rowNumber: number;
  email: string;
  displayName: string;
  status: "VALID" | "ERROR";
  errors: string[];
}

export interface MemberCsvDryRunResult {
  sessionId: string;
  totalRows: number;
  validRows: number;
  errorRows: number;
  rows: MemberCsvDryRunRow[];
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}

export async function downloadMemberCsvTemplate(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/members/csv/template`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error("Failed to download template");
  }

  const blob = await response.blob();
  downloadBlob(blob, "member-import-template.csv");
}

export async function dryRunMemberCsv(file: File, organizationId: string): Promise<MemberCsvDryRunResult> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("organizationId", organizationId);

  const headers: Record<string, string> = {};
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/admin/members/csv/dry-run`, {
    method: "POST",
    body: formData,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => null);
    throw new Error(errorData?.message || "Dry run failed");
  }

  return response.json();
}

export async function executeMemberCsvImport(sessionId: string, signal?: AbortSignal): Promise<void> {
  const encodedSessionId = encodeURIComponent(sessionId);

  const headers: Record<string, string> = {};
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/admin/members/csv/import/${encodedSessionId}`, {
    method: "POST",
    headers,
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => null);
    throw new Error(errorData?.message || "Import failed");
  }

  const blob = await response.blob();
  downloadBlob(blob, "member-import-result.csv");
}
