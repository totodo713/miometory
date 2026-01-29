/**
 * CSV Import/Export Service
 *
 * Provides methods for:
 * - Downloading CSV template
 * - Importing CSV files with progress tracking
 * - Exporting work log data to CSV
 */

// Normalize API base URL to ensure no trailing path segments
// NEXT_PUBLIC_API_URL should be the origin only (e.g., "https://example.com")
const API_BASE_URL = (() => {
  const url = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  // Remove any trailing path segments to prevent double-prefix issues
  try {
    const parsed = new URL(url);
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    // Fallback for relative URLs or invalid URLs
    return url.replace(/\/+$/, "");
  }
})();

/**
 * Get CSRF token from cookie set by Spring Security.
 * Spring Security sets XSRF-TOKEN cookie which we need to read
 * and send back in X-XSRF-TOKEN header for non-GET requests.
 */
function getCsrfToken(): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const cookies = document.cookie.split(";");
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split("=");
    if (name === "XSRF-TOKEN") {
      return decodeURIComponent(value);
    }
  }
  return null;
}

export interface CsvImportProgress {
  totalRows: number;
  validRows: number;
  errorRows: number;
  status: "processing" | "completed" | "failed";
  errors?: Array<{
    row: number;
    errors: string[];
  }>;
}

export interface CsvImportResult {
  importId: string;
}

/**
 * Download the CSV template file.
 */
export async function downloadTemplate(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/worklog/csv/template`, {
    credentials: "include", // Include session cookies for cross-origin requests
  });
  if (!response.ok) {
    throw new Error("Failed to download template");
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "worklog-template.csv";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}

/**
 * Import a CSV file.
 * Returns an import ID that can be used to track progress.
 * Uses credentials: "include" for session cookie authentication (CSRF protection).
 */
export async function importCsv(
  file: File,
  memberId: string,
): Promise<CsvImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("memberId", memberId);

  const headers: Record<string, string> = {};
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/worklog/csv/import`, {
    method: "POST",
    body: formData,
    headers,
    credentials: "include", // Include session cookies for CSRF protection
  });

  if (!response.ok) {
    throw new Error("Failed to start CSV import");
  }

  return response.json();
}

/**
 * Subscribe to import progress updates via Server-Sent Events.
 *
 * @param importId Import ID from importCsv()
 * @param onProgress Callback for progress updates
 * @param onError Callback for errors
 * @param onComplete Callback when import completes
 */
export function subscribeToImportProgress(
  importId: string,
  onProgress: (progress: CsvImportProgress) => void,
  onError: (error: string) => void,
  onComplete: () => void,
): () => void {
  const eventSource = new EventSource(
    `${API_BASE_URL}/api/v1/worklog/csv/import/${importId}/progress`,
    { withCredentials: true }, // Include session cookies for cross-origin SSE
  );

  eventSource.addEventListener("progress", (event) => {
    const progress = JSON.parse(event.data) as CsvImportProgress;
    onProgress(progress);

    if (progress.status === "completed" || progress.status === "failed") {
      eventSource.close();
      onComplete();
    }
  });

  // Handle server-sent "error" events with payload
  // Note: Native connection errors dispatch as Event (not MessageEvent) without data
  eventSource.addEventListener("error", (event) => {
    if (event instanceof MessageEvent && typeof event.data === "string") {
      try {
        const errorData = JSON.parse(event.data);
        onError(errorData.error || "Unknown error");
      } catch {
        onError("Failed to parse error response");
      }
      eventSource.close();
    }
    // Native transport errors are handled by eventSource.onerror below
  });

  eventSource.onerror = () => {
    onError("Connection to server lost");
    eventSource.close();
    onComplete();
  };

  // Return cleanup function
  return () => {
    eventSource.close();
  };
}

/**
 * Export work log entries for a specific month to CSV.
 *
 * @param year Year (e.g., 2026)
 * @param month Month (1-12)
 * @param memberId Member ID to export entries for
 */
export async function exportCsv(
  year: number,
  month: number,
  memberId: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/worklog/csv/export/${year}/${month}?memberId=${memberId}`,
    {
      credentials: "include", // Include session cookies for cross-origin requests
    },
  );

  if (!response.ok) {
    throw new Error("Failed to export CSV");
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `worklog-${year}-${String(month).padStart(2, "0")}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}
