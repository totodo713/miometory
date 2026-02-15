/**
 * API Client for Work-Log Application
 *
 * Provides type-safe API calls with:
 * - Automatic authentication (session cookies)
 * - CSRF token handling
 * - Optimistic locking support (If-Match/ETag headers)
 * - Error handling with typed errors
 * - Request/response interceptors
 *
 * Works with Spring Security session-based authentication.
 * Session timeout (30 minutes) is handled client-side via session timeout warnings.
 */

import type { MonthlyCalendarResponse } from "@/types/worklog";
import { getCsrfToken } from "./csrf";

// Normalize API base URL to ensure consistent URL construction
// This prevents issues when NEXT_PUBLIC_API_URL contains path prefixes
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
 * API Error types
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export class ConflictError extends ApiError {
  constructor(message = "Resource was modified by another user") {
    super(message, 412, "CONFLICT");
    this.name = "ConflictError";
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = "Session expired or unauthorized") {
    super(message, 401, "UNAUTHORIZED");
    this.name = "UnauthorizedError";
  }
}

export class ValidationError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 400, "VALIDATION_ERROR", details);
    this.name = "ValidationError";
  }
}

/**
 * API request options
 */
interface ApiRequestOptions extends RequestInit {
  /** Entity version for optimistic locking (sends If-Match header) */
  version?: number;
  /** Skip authentication check (for public endpoints) */
  skipAuth?: boolean;
}

/**
 * API Client class
 */
class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  /**
   * Makes an authenticated API request
   */
  private async request<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
    const { version, ...fetchOptions } = options;

    const url = `${this.baseUrl}${endpoint}`;

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      ...((fetchOptions.headers as Record<string, string>) || {}),
    };

    // Add If-Match header for optimistic locking
    if (version !== undefined) {
      headers["If-Match"] = version.toString();
    }

    // Add CSRF token for non-safe methods (POST, PUT, PATCH, DELETE)
    // Safe methods (GET, HEAD, OPTIONS, TRACE) don't require CSRF protection
    const method = fetchOptions.method?.toUpperCase() || "GET";
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      const csrfToken = getCsrfToken();
      if (csrfToken) {
        headers["X-XSRF-TOKEN"] = csrfToken;
      }
    }

    try {
      const response = await fetch(url, {
        ...fetchOptions,
        headers,
        credentials: "include", // Include session cookies
      });

      // Handle specific HTTP status codes
      if (response.status === 401) {
        throw new UnauthorizedError();
      }

      if (response.status === 412) {
        throw new ConflictError();
      }

      if (response.status === 400) {
        const errorData = await response.json().catch(() => ({}));
        throw new ValidationError(errorData.message || "Validation failed", errorData.errors);
      }

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new ApiError(errorData.message || "API request failed", response.status, errorData.code);
      }

      // Handle 204 No Content
      if (response.status === 204) {
        return undefined as T;
      }

      // Extract version from ETag header if present
      const etag = response.headers.get("ETag");
      const data = await response.json();

      // Attach version to response if ETag is present
      if (etag && typeof data === "object" && data !== null) {
        data._version = Number.parseInt(etag, 10);
      }

      return data;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }

      // Network or other errors
      throw new ApiError(error instanceof Error ? error.message : "Network error", 0);
    }
  }

  /**
   * GET request
   */
  async get<T>(endpoint: string, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "GET" });
  }

  /**
   * POST request
   */
  async post<T>(endpoint: string, data?: unknown, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T>(endpoint: string, data: unknown, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  /**
   * PATCH request (for partial updates, e.g., auto-save)
   */
  async patch<T>(endpoint: string, data: unknown, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "PATCH",
      body: JSON.stringify(data),
    });
  }

  /**
   * DELETE request
   */
  async delete<T>(endpoint: string, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "DELETE" });
  }
}

/**
 * Singleton API client instance
 */
export const apiClient = new ApiClient(API_BASE_URL);

/**
 * Type-safe API endpoints
 *
 * These will be populated as features are implemented.
 * Example usage:
 *
 * ```typescript
 * const entry = await api.worklog.getEntry('entry-id')
 * await api.worklog.updateEntry('entry-id', data, { version: 5 })
 * ```
 */
export const api = {
  /**
   * Health check endpoint (public, no auth required)
   */
  health: {
    check: () => apiClient.get<{ status: string }>("/api/v1/health", { skipAuth: true }),
  },

  /**
   * Work log entry endpoints
   */
  worklog: {
    /**
     * Create a new work log entry
     */
    createEntry: (data: {
      memberId: string;
      projectId: string;
      date: string;
      hours: number;
      comment?: string;
      enteredBy?: string;
    }) =>
      apiClient.post<{
        id: string;
        memberId: string;
        projectId: string;
        date: string;
        hours: number;
        comment: string | null;
        status: string;
        enteredBy: string;
        createdAt: string;
        updatedAt: string;
        version: number;
      }>("/api/v1/worklog/entries", data),

    /**
     * Get work log entries by date range
     */
    getEntries: (params: { memberId: string; startDate: string; endDate: string; status?: string }) => {
      const query = new URLSearchParams({
        memberId: params.memberId,
        startDate: params.startDate,
        endDate: params.endDate,
        ...(params.status && { status: params.status }),
      });
      return apiClient.get<{
        entries: Array<{
          id: string;
          memberId: string;
          projectId: string;
          date: string;
          hours: number;
          comment: string | null;
          status: string;
          enteredBy: string;
          createdAt: string;
          updatedAt: string;
          version: number;
        }>;
        total: number;
      }>(`/api/v1/worklog/entries?${query}`);
    },

    /**
     * Get a single work log entry by ID
     */
    getEntry: (id: string) =>
      apiClient.get<{
        id: string;
        memberId: string;
        projectId: string;
        date: string;
        hours: number;
        comment: string | null;
        status: string;
        enteredBy: string;
        createdAt: string;
        updatedAt: string;
        version: number;
      }>(`/api/v1/worklog/entries/${id}`),

    /**
     * Update a work log entry (PATCH)
     */
    updateEntry: (id: string, data: { hours: number; comment?: string }, options: { version: number }) =>
      apiClient.patch<void>(`/api/v1/worklog/entries/${id}`, data, {
        version: options.version,
      }),

    /**
     * Delete a work log entry
     */
    deleteEntry: (id: string) => apiClient.delete<void>(`/api/v1/worklog/entries/${id}`),

    /**
     * Get monthly calendar view
     */
    getCalendar: (params: { year: number; month: number; memberId: string }) => {
      const query = new URLSearchParams({ memberId: params.memberId });
      return apiClient.get<MonthlyCalendarResponse>(`/api/v1/worklog/calendar/${params.year}/${params.month}?${query}`);
    },

    /**
     * Get monthly summary with project breakdown
     */
    getMonthlySummary: (params: { year: number; month: number; memberId: string }) => {
      const query = new URLSearchParams({ memberId: params.memberId });
      return apiClient.get<{
        year: number;
        month: number;
        totalWorkHours: number;
        totalAbsenceHours: number;
        totalBusinessDays: number;
        projects: Array<{
          projectId: string;
          projectName: string;
          totalHours: number;
          percentage: number;
        }>;
        approvalStatus: "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED" | null;
        rejectionReason: string | null;
      }>(`/api/v1/worklog/calendar/${params.year}/${params.month}/summary?${query}`);
    },

    /**
     * Get projects from previous fiscal month (for copy feature)
     */
    getPreviousMonthProjects: (params: { year: number; month: number; memberId: string }) => {
      const query = new URLSearchParams({
        year: params.year.toString(),
        month: params.month.toString(),
        memberId: params.memberId,
      });
      return apiClient.get<{
        projectIds: string[];
        previousMonthStart: string;
        previousMonthEnd: string;
        count: number;
      }>(`/api/v1/worklog/projects/previous-month?${query}`);
    },
  },

  /**
   * Absence endpoints
   */
  absence: {
    /**
     * Create a new absence record
     */
    createAbsence: (data: {
      memberId: string;
      date: string;
      hours: number;
      absenceType: string;
      reason?: string;
      recordedBy?: string;
    }) =>
      apiClient.post<{
        id: string;
        memberId: string;
        date: string;
        hours: number;
        absenceType: string;
        reason: string | null;
        status: string;
        recordedBy: string;
        createdAt: string;
        updatedAt: string;
        version: number;
      }>("/api/v1/absences", data),

    /**
     * Get absences by date range
     */
    getAbsences: (params: { memberId: string; startDate: string; endDate: string; status?: string }) => {
      const query = new URLSearchParams({
        memberId: params.memberId,
        startDate: params.startDate,
        endDate: params.endDate,
        ...(params.status && { status: params.status }),
      });
      return apiClient.get<{
        absences: Array<{
          id: string;
          memberId: string;
          date: string;
          hours: number;
          absenceType: string;
          reason: string | null;
          status: string;
          recordedBy: string;
          createdAt: string;
          updatedAt: string;
          version: number;
        }>;
        total: number;
      }>(`/api/v1/absences?${query}`);
    },

    /**
     * Get a single absence by ID
     */
    getAbsence: (id: string) =>
      apiClient.get<{
        id: string;
        memberId: string;
        date: string;
        hours: number;
        absenceType: string;
        reason: string | null;
        status: string;
        recordedBy: string;
        createdAt: string;
        updatedAt: string;
        version: number;
      }>(`/api/v1/absences/${id}`),

    /**
     * Update an absence (PATCH)
     */
    updateAbsence: (
      id: string,
      data: { hours: number; absenceType: string; reason?: string },
      options: { version: number },
    ) =>
      apiClient.patch<void>(`/api/v1/absences/${id}`, data, {
        version: options.version,
      }),

    /**
     * Delete an absence
     */
    deleteAbsence: (id: string) => apiClient.delete<void>(`/api/v1/absences/${id}`),
  },

  /**
   * Approval endpoints
   */
  approval: {
    /**
     * Submit a fiscal month for approval
     */
    submitMonth: (data: { memberId: string; fiscalMonthStart: string; fiscalMonthEnd: string; submittedBy?: string }) =>
      apiClient.post<{ approvalId: string }>("/api/v1/worklog/submissions", data),

    /**
     * Get manager's approval queue
     */
    getApprovalQueue: (managerId: string) => {
      const query = new URLSearchParams({ managerId });
      return apiClient.get<{
        pendingApprovals: Array<{
          approvalId: string;
          memberId: string;
          memberName: string;
          fiscalMonthStart: string;
          fiscalMonthEnd: string;
          totalWorkHours: number;
          totalAbsenceHours: number;
          submittedAt: string;
          submittedByName: string;
        }>;
        totalCount: number;
      }>(`/api/v1/worklog/approvals/queue?${query}`);
    },

    /**
     * Approve a submitted month
     */
    approveMonth: (approvalId: string, reviewedBy: string) =>
      apiClient.post<void>(`/api/v1/worklog/approvals/${approvalId}/approve`, {
        reviewedBy,
      }),

    /**
     * Reject a submitted month with reason
     */
    rejectMonth: (approvalId: string, data: { reviewedBy: string; rejectionReason: string }) =>
      apiClient.post<void>(`/api/v1/worklog/approvals/${approvalId}/reject`, data),
  },

  /**
   * Authentication endpoints
   */
  auth: {
    /**
     * Request password reset email
     * Always returns 200 OK to prevent email enumeration
     */
    requestPasswordReset: (data: { email: string }) =>
      apiClient.post<{ message: string }>("/api/v1/auth/password-reset/request", data, { skipAuth: true }),

    /**
     * Confirm password reset with token
     */
    confirmPasswordReset: (data: { token: string; newPassword: string }) =>
      apiClient.post<{ message: string }>("/api/v1/auth/password-reset/confirm", data, { skipAuth: true }),
  },

  /**
   * Member endpoints (for proxy entry feature)
   */
  members: {
    /**
     * Get a member by ID
     */
    getMember: (id: string) =>
      apiClient.get<{
        id: string;
        email: string;
        displayName: string;
        managerId: string | null;
        isActive: boolean;
      }>(`/api/v1/members/${id}`),

    /**
     * Get subordinates of a manager
     */
    getSubordinates: (managerId: string, recursive = false) => {
      const query = new URLSearchParams({ recursive: recursive.toString() });
      return apiClient.get<{
        subordinates: Array<{
          id: string;
          email: string;
          displayName: string;
          managerId: string | null;
          isActive: boolean;
        }>;
        count: number;
        includesIndirect: boolean;
      }>(`/api/v1/members/${managerId}/subordinates?${query}`);
    },

    /**
     * Check if manager can enter time on behalf of a member
     */
    canProxy: (managerId: string, memberId: string) =>
      apiClient.get<{
        canProxy: boolean;
        reason: string;
      }>(`/api/v1/members/${managerId}/can-proxy/${memberId}`),

    /**
     * Get assigned projects for a member
     * Returns projects that the member is assigned to and can log time against
     */
    getAssignedProjects: (memberId: string) =>
      apiClient.get<{
        projects: Array<{
          id: string;
          code: string;
          name: string;
        }>;
        count: number;
      }>(`/api/v1/members/${memberId}/projects`),
  },
};

/**
 * Check if user is authenticated (has valid session)
 * Returns true if session is valid, false otherwise
 */
export async function checkAuth(): Promise<boolean> {
  try {
    await api.health.check();
    return true;
  } catch (error) {
    if (error instanceof UnauthorizedError) {
      return false;
    }
    // Other errors (network, etc.) - assume authenticated
    return true;
  }
}
