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

import type {
  FiscalYearPresetRow,
  HolidayCalendarPresetRow,
  HolidayEntryRow,
  MonthlyPeriodPresetRow,
  PresetPage,
} from "@/types/masterData";
import type { UserStatusResponse } from "@/types/tenant";
import type { MonthlyCalendarResponse } from "@/types/worklog";
import { getCsrfToken } from "./csrf";

/**
 * Organization types for admin API
 */
interface OrganizationRow {
  id: string;
  tenantId: string;
  parentId: string | null;
  parentName: string | null;
  code: string;
  name: string;
  level: number;
  status: "ACTIVE" | "INACTIVE";
  memberCount: number;
  fiscalYearPatternId: string | null;
  monthlyPeriodPatternId: string | null;
  createdAt: string;
  updatedAt: string;
}

interface OrganizationPage {
  content: OrganizationRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/**
 * Member types for organization member management
 */
interface OrganizationMemberRow {
  id: string;
  email: string;
  displayName: string;
  managerId: string | null;
  managerName: string | null;
  managerIsActive: boolean | null;
  isActive: boolean;
}

interface MemberPage {
  content: OrganizationMemberRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/**
 * Pattern option types for fiscal year and monthly period patterns
 */
interface FiscalYearPatternOption {
  id: string;
  tenantId: string;
  name: string;
  startMonth: number;
  startDay: number;
}

interface MonthlyPeriodPatternOption {
  id: string;
  tenantId: string;
  name: string;
  startDay: number;
}

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

export class ForbiddenError extends ApiError {
  constructor(message = "Access denied", code = "FORBIDDEN") {
    super(message, 403, code);
    this.name = "ForbiddenError";
  }
}

export class ValidationError extends ApiError {
  constructor(message: string, details?: unknown) {
    super(message, 400, "VALIDATION_ERROR", details);
    this.name = "ValidationError";
  }
}

/** Custom event name fired when a non-auth API call receives 401 */
export const AUTH_UNAUTHORIZED_EVENT = "miometry:auth:unauthorized";

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
    const { version, skipAuth, ...fetchOptions } = options;

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
        if (!skipAuth && typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT));
        }
        throw new UnauthorizedError();
      }

      if (response.status === 403) {
        const errorData = await response.json().catch(() => ({}) as Record<string, unknown>);
        throw new ForbiddenError(
          typeof errorData.message === "string" ? errorData.message : undefined,
          typeof errorData.code === "string" ? errorData.code : undefined,
        );
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

    /**
     * Submit all DRAFT entries for a member on a specific date
     */
    submitDailyEntries: (data: { memberId: string; date: string; submittedBy: string }) =>
      apiClient.post<{
        submittedCount: number;
        date: string;
        entries: Array<{
          id: string;
          projectId: string;
          hours: number;
          status: string;
          version: number;
        }>;
      }>("/api/v1/worklog/entries/submit-daily", data),

    /**
     * Recall all SUBMITTED entries for a member on a specific date back to DRAFT
     */
    recallDailyEntries: (data: { memberId: string; date: string; recalledBy: string }) =>
      apiClient.post<{
        recalledCount: number;
        date: string;
        entries: Array<{
          id: string;
          projectId: string;
          hours: number;
          status: string;
          version: number;
        }>;
      }>("/api/v1/worklog/entries/recall-daily", data),

    /**
     * Reject all SUBMITTED entries for a member on a specific date
     */
    rejectDailyEntries: (data: { memberId: string; date: string; rejectedBy: string; rejectionReason: string }) =>
      apiClient.post<{
        rejectedCount: number;
        date: string;
        rejectionReason: string;
        entries: Array<{
          id: string;
          projectId: string;
          hours: number;
          status: string;
          version: number;
        }>;
      }>("/api/v1/worklog/entries/reject-daily", data),

    /**
     * Get daily rejection log entries for a member within a date range
     */
    getDailyRejections: (params: { memberId: string; startDate: string; endDate: string }) => {
      const query = new URLSearchParams({
        memberId: params.memberId,
        startDate: params.startDate,
        endDate: params.endDate,
      });
      return apiClient.get<{
        rejections: Array<{
          date: string;
          rejectionReason: string;
          rejectedBy: string;
          rejectedByName: string | null;
          rejectedAt: string;
        }>;
      }>(`/api/v1/worklog/rejections/daily?${query}`);
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

    /**
     * Get member's approval status for a fiscal month
     */
    getMemberApproval: (params: { memberId: string; fiscalMonthStart: string; fiscalMonthEnd: string }) => {
      const query = new URLSearchParams({
        fiscalMonthStart: params.fiscalMonthStart,
        fiscalMonthEnd: params.fiscalMonthEnd,
      });
      return apiClient.get<{
        approvalId: string;
        memberId: string;
        fiscalMonthStart: string;
        fiscalMonthEnd: string;
        status: string;
        submittedAt: string | null;
        reviewedAt: string | null;
        reviewedBy: string | null;
        reviewerName: string | null;
        rejectionReason: string | null;
      }>(`/api/v1/worklog/approvals/member/${params.memberId}?${query}`);
    },

    getDetail: (approvalId: string) =>
      apiClient.get<{
        approvalId: string;
        status: string;
        memberName: string;
        fiscalMonthStart: string;
        fiscalMonthEnd: string;
        totalWorkHours: number;
        totalAbsenceHours: number;
        projectBreakdown: Array<{
          projectCode: string;
          projectName: string;
          hours: number;
        }>;
        dailyApprovalSummary: {
          approvedCount: number;
          rejectedCount: number;
          unapprovedCount: number;
        };
        unresolvedEntries: Array<{
          entryId: string;
          date: string;
          projectCode: string;
          rejectionComment: string;
        }>;
      }>(`/api/v1/worklog/approvals/${approvalId}/detail`),
  },

  /**
   * Authentication endpoints
   */
  auth: {
    /**
     * Login with email and password
     */
    login: (data: { email: string; password: string; rememberMe: boolean }) =>
      apiClient.post<{
        user: {
          id: string;
          email: string;
          name: string;
          accountStatus: string;
          preferredLocale: string;
          memberId: string | null;
        };
        sessionExpiresAt: string;
        rememberMeToken: string | null;
        warning: string | null;
        tenantAffiliationState: string;
        memberships: Array<{
          memberId: string;
          tenantId: string;
          tenantName: string;
          organizationId: string | null;
          organizationName: string | null;
        }>;
      }>("/api/v1/auth/login", data, { skipAuth: true }),

    /**
     * Logout current session
     */
    logout: () => apiClient.post<void>("/api/v1/auth/logout"),

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

    signup: (email: string, password: string) =>
      apiClient.post<void>("/api/v1/auth/signup", { email, password }, { skipAuth: true }),

    verifyEmail: (token: string) => apiClient.post<void>("/api/v1/auth/verify-email", { token }, { skipAuth: true }),
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

  /**
   * Admin endpoints
   */
  admin: {
    getContext: () =>
      apiClient.get<{
        role: string;
        permissions: string[];
        tenantId: string | null;
        tenantName: string | null;
        memberId: string | null;
      }>("/api/v1/admin/context"),

    members: {
      list: (params?: {
        page?: number;
        size?: number;
        search?: string;
        organizationId?: string;
        isActive?: boolean;
      }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", params.page.toString());
        if (params?.size !== undefined) query.set("size", params.size.toString());
        if (params?.search) query.set("search", params.search);
        if (params?.organizationId) query.set("organizationId", params.organizationId);
        if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
        return apiClient.get<{
          content: Array<{
            id: string;
            email: string;
            displayName: string;
            organizationId: string | null;
            managerId: string | null;
            managerName: string | null;
            isActive: boolean;
          }>;
          totalElements: number;
          totalPages: number;
          number: number;
        }>(`/api/v1/admin/members?${query}`);
      },
      create: (data: { email: string; displayName: string; organizationId?: string; managerId?: string }) =>
        apiClient.post<{ id: string }>("/api/v1/admin/members", data),
      update: (id: string, data: { email: string; displayName: string; organizationId?: string; managerId?: string }) =>
        apiClient.put<void>(`/api/v1/admin/members/${id}`, data),
      deactivate: (id: string) => apiClient.patch<void>(`/api/v1/admin/members/${id}/deactivate`, {}),
      activate: (id: string) => apiClient.patch<void>(`/api/v1/admin/members/${id}/activate`, {}),
      assignTenantAdmin: (id: string) => apiClient.post<void>(`/api/v1/admin/members/${id}/assign-tenant-admin`, {}),
      assignManager: (memberId: string, managerId: string) =>
        apiClient.put<void>(`/api/v1/admin/members/${memberId}/manager`, { managerId }),
      removeManager: (memberId: string) => apiClient.delete<void>(`/api/v1/admin/members/${memberId}/manager`),
      transferMember: (memberId: string, organizationId: string) =>
        apiClient.put<void>(`/api/v1/admin/members/${memberId}/organization`, { organizationId }),
      assignTenant: (userId: string, displayName: string) =>
        apiClient.post<void>("/api/v1/admin/members/assign-tenant", { userId, displayName }),
    },

    projects: {
      list: (params?: { page?: number; size?: number; search?: string; isActive?: boolean }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", params.page.toString());
        if (params?.size !== undefined) query.set("size", params.size.toString());
        if (params?.search) query.set("search", params.search);
        if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
        return apiClient.get<{
          content: Array<{
            id: string;
            code: string;
            name: string;
            isActive: boolean;
            validFrom: string | null;
            validUntil: string | null;
            assignedMemberCount: number;
          }>;
          totalElements: number;
          totalPages: number;
          number: number;
        }>(`/api/v1/admin/projects?${query}`);
      },
      create: (data: { code: string; name: string; validFrom?: string; validUntil?: string }) =>
        apiClient.post<{ id: string }>("/api/v1/admin/projects", data),
      update: (id: string, data: { name: string; validFrom?: string; validUntil?: string }) =>
        apiClient.put<void>(`/api/v1/admin/projects/${id}`, data),
      deactivate: (id: string) => apiClient.patch<void>(`/api/v1/admin/projects/${id}/deactivate`, {}),
      activate: (id: string) => apiClient.patch<void>(`/api/v1/admin/projects/${id}/activate`, {}),
    },

    assignments: {
      listByMember: (memberId: string) =>
        apiClient.get<
          Array<{
            id: string;
            memberId: string;
            memberName: string;
            memberEmail: string;
            projectId: string;
            projectCode: string;
            projectName: string;
            isActive: boolean;
            assignedAt: string;
          }>
        >(`/api/v1/admin/assignments/by-member/${memberId}`),
      listByProject: (projectId: string) =>
        apiClient.get<
          Array<{
            id: string;
            memberId: string;
            memberName: string;
            memberEmail: string;
            projectId: string;
            projectCode: string;
            projectName: string;
            isActive: boolean;
            assignedAt: string;
          }>
        >(`/api/v1/admin/assignments/by-project/${projectId}`),
      create: (data: { memberId: string; projectId: string }) =>
        apiClient.post<{ id: string }>("/api/v1/admin/assignments", data),
      deactivate: (id: string) => apiClient.patch<void>(`/api/v1/admin/assignments/${id}/deactivate`, {}),
      activate: (id: string) => apiClient.patch<void>(`/api/v1/admin/assignments/${id}/activate`, {}),
    },

    tenants: {
      list: (params?: { page?: number; size?: number; status?: string }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", params.page.toString());
        if (params?.size !== undefined) query.set("size", params.size.toString());
        if (params?.status) query.set("status", params.status);
        return apiClient.get<{
          content: Array<{
            id: string;
            code: string;
            name: string;
            status: string;
            createdAt: string;
          }>;
          totalElements: number;
          totalPages: number;
          number: number;
        }>(`/api/v1/admin/tenants?${query}`);
      },
      create: (data: { code: string; name: string }) => apiClient.post<{ id: string }>("/api/v1/admin/tenants", data),
      update: (id: string, data: { name: string }) => apiClient.put<void>(`/api/v1/admin/tenants/${id}`, data),
      deactivate: (id: string) => apiClient.patch<void>(`/api/v1/admin/tenants/${id}/deactivate`, {}),
      activate: (id: string) => apiClient.patch<void>(`/api/v1/admin/tenants/${id}/activate`, {}),
      getDefaultPatterns: (id: string) =>
        apiClient.get<{
          defaultFiscalYearPatternId: string | null;
          defaultMonthlyPeriodPatternId: string | null;
        }>(`/api/v1/admin/tenants/${id}/default-patterns`),
      updateDefaultPatterns: (
        id: string,
        data: { defaultFiscalYearPatternId: string | null; defaultMonthlyPeriodPatternId: string | null },
      ) => apiClient.put<void>(`/api/v1/admin/tenants/${id}/default-patterns`, data),
    },

    users: {
      list: (params?: {
        page?: number;
        size?: number;
        search?: string;
        tenantId?: string;
        roleId?: string;
        accountStatus?: string;
      }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", params.page.toString());
        if (params?.size !== undefined) query.set("size", params.size.toString());
        if (params?.search) query.set("search", params.search);
        if (params?.tenantId) query.set("tenantId", params.tenantId);
        if (params?.roleId) query.set("roleId", params.roleId);
        if (params?.accountStatus) query.set("accountStatus", params.accountStatus);
        return apiClient.get<{
          content: Array<{
            id: string;
            email: string;
            name: string;
            roleName: string;
            tenantName: string | null;
            accountStatus: string;
            lastLoginAt: string | null;
          }>;
          totalElements: number;
          totalPages: number;
          number: number;
        }>(`/api/v1/admin/users?${query}`);
      },
      changeRole: (id: string, data: { roleId: string }) => apiClient.put<void>(`/api/v1/admin/users/${id}/role`, data),
      lock: (id: string, data: { durationMinutes: number }) =>
        apiClient.patch<void>(`/api/v1/admin/users/${id}/lock`, data),
      unlock: (id: string) => apiClient.patch<void>(`/api/v1/admin/users/${id}/unlock`, {}),
      resetPassword: (id: string) => apiClient.post<void>(`/api/v1/admin/users/${id}/password-reset`, {}),
      searchForAssignment: (email: string) =>
        apiClient.get<{
          users: Array<{ userId: string; email: string; name: string; isAlreadyInTenant: boolean }>;
        }>(`/api/v1/admin/users/search-for-assignment?email=${encodeURIComponent(email)}`),
    },

    organizations: {
      list: (params?: { page?: number; size?: number; search?: string; isActive?: boolean; parentId?: string }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", String(params.page));
        if (params?.size !== undefined) query.set("size", String(params.size));
        if (params?.search) query.set("search", params.search);
        if (params?.isActive !== undefined) query.set("isActive", String(params.isActive));
        if (params?.parentId) query.set("parentId", params.parentId);
        const qs = query.toString();
        return apiClient.get<OrganizationPage>(`/api/v1/admin/organizations${qs ? `?${qs}` : ""}`);
      },
      create: (data: { code: string; name: string; parentId?: string }) =>
        apiClient.post<{ id: string }>("/api/v1/admin/organizations", data),
      update: (id: string, data: { name: string }) => apiClient.put<void>(`/api/v1/admin/organizations/${id}`, data),
      deactivate: (id: string) =>
        apiClient.patch<{ warnings: string[] }>(`/api/v1/admin/organizations/${id}/deactivate`, {}),
      activate: (id: string) => apiClient.patch<void>(`/api/v1/admin/organizations/${id}/activate`, {}),
      listMembers: (orgId: string, params?: { page?: number; size?: number; isActive?: boolean }) => {
        const query = new URLSearchParams();
        if (params?.page !== undefined) query.set("page", String(params.page));
        if (params?.size !== undefined) query.set("size", String(params.size));
        if (params?.isActive !== undefined) query.set("isActive", String(params.isActive));
        const qs = query.toString();
        return apiClient.get<MemberPage>(`/api/v1/admin/organizations/${orgId}/members${qs ? `?${qs}` : ""}`);
      },
      getOrganizationTree: (includeInactive?: boolean) => {
        const query = new URLSearchParams();
        if (includeInactive !== undefined) query.set("includeInactive", String(includeInactive));
        const qs = query.toString();
        return apiClient.get<OrganizationTreeNode[]>(`/api/v1/admin/organizations/tree${qs ? `?${qs}` : ""}`);
      },
      assignPatterns: (orgId: string, fiscalYearPatternId: string | null, monthlyPeriodPatternId: string | null) =>
        apiClient.put<void>(`/api/v1/admin/organizations/${orgId}/patterns`, {
          fiscalYearPatternId,
          monthlyPeriodPatternId,
        }),
      getDateInfo: (tenantId: string, orgId: string, date: string) =>
        apiClient.post<{
          fiscalYear: string;
          fiscalPeriod: string;
          monthlyPeriodStart: string;
          monthlyPeriodEnd: string;
        }>(`/api/v1/tenants/${tenantId}/organizations/${orgId}/date-info`, { date }),
      getEffectivePatterns: (orgId: string) =>
        apiClient.get<EffectivePatterns>(`/api/v1/admin/organizations/${orgId}/effective-patterns`),
    },

    system: {
      getPatterns: () => apiClient.get<SystemDefaultPatterns>("/api/v1/admin/system/settings/patterns"),
      updatePatterns: (data: SystemDefaultPatterns) =>
        apiClient.put<void>("/api/v1/admin/system/settings/patterns", data),
    },

    /**
     * Pattern list endpoints (tenant-scoped)
     */
    patterns: {
      listFiscalYearPatterns: (tenantId: string) =>
        apiClient.get<FiscalYearPatternOption[]>(`/api/v1/tenants/${tenantId}/fiscal-year-patterns`),
      listMonthlyPeriodPatterns: (tenantId: string) =>
        apiClient.get<MonthlyPeriodPatternOption[]>(`/api/v1/tenants/${tenantId}/monthly-period-patterns`),
      createFiscalYearPattern: (tenantId: string, data: { name: string; startMonth: number; startDay: number }) =>
        apiClient.post<FiscalYearPatternOption>(`/api/v1/tenants/${tenantId}/fiscal-year-patterns`, data),
      createMonthlyPeriodPattern: (tenantId: string, data: { name: string; startDay: number }) =>
        apiClient.post<MonthlyPeriodPatternOption>(`/api/v1/tenants/${tenantId}/monthly-period-patterns`, data),
    },

    /**
     * Master data preset management endpoints (SYSTEM_ADMIN only)
     */
    masterData: {
      fiscalYearPresets: {
        list: (params?: { search?: string; isActive?: boolean; page?: number; size?: number }) => {
          const query = new URLSearchParams();
          if (params?.search) query.set("search", params.search);
          if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
          if (params?.page !== undefined) query.set("page", params.page.toString());
          if (params?.size !== undefined) query.set("size", params.size.toString());
          const qs = query.toString();
          return apiClient.get<PresetPage<FiscalYearPresetRow>>(
            `/api/v1/admin/master-data/fiscal-year-patterns${qs ? `?${qs}` : ""}`,
          );
        },
        create: (data: { name: string; description?: string; startMonth: number; startDay: number }) =>
          apiClient.post<{ id: string }>("/api/v1/admin/master-data/fiscal-year-patterns", data),
        update: (id: string, data: { name: string; description?: string; startMonth: number; startDay: number }) =>
          apiClient.put<void>(`/api/v1/admin/master-data/fiscal-year-patterns/${id}`, data),
        deactivate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/fiscal-year-patterns/${id}/deactivate`, {}),
        activate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/fiscal-year-patterns/${id}/activate`, {}),
      },

      monthlyPeriodPresets: {
        list: (params?: { search?: string; isActive?: boolean; page?: number; size?: number }) => {
          const query = new URLSearchParams();
          if (params?.search) query.set("search", params.search);
          if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
          if (params?.page !== undefined) query.set("page", params.page.toString());
          if (params?.size !== undefined) query.set("size", params.size.toString());
          const qs = query.toString();
          return apiClient.get<PresetPage<MonthlyPeriodPresetRow>>(
            `/api/v1/admin/master-data/monthly-period-patterns${qs ? `?${qs}` : ""}`,
          );
        },
        create: (data: { name: string; description?: string; startDay: number }) =>
          apiClient.post<{ id: string }>("/api/v1/admin/master-data/monthly-period-patterns", data),
        update: (id: string, data: { name: string; description?: string; startDay: number }) =>
          apiClient.put<void>(`/api/v1/admin/master-data/monthly-period-patterns/${id}`, data),
        deactivate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/monthly-period-patterns/${id}/deactivate`, {}),
        activate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/monthly-period-patterns/${id}/activate`, {}),
      },

      holidayCalendars: {
        list: (params?: { search?: string; isActive?: boolean; page?: number; size?: number }) => {
          const query = new URLSearchParams();
          if (params?.search) query.set("search", params.search);
          if (params?.isActive !== undefined) query.set("isActive", params.isActive.toString());
          if (params?.page !== undefined) query.set("page", params.page.toString());
          if (params?.size !== undefined) query.set("size", params.size.toString());
          const qs = query.toString();
          return apiClient.get<PresetPage<HolidayCalendarPresetRow>>(
            `/api/v1/admin/master-data/holiday-calendars${qs ? `?${qs}` : ""}`,
          );
        },
        create: (data: { name: string; description?: string; country?: string }) =>
          apiClient.post<{ id: string }>("/api/v1/admin/master-data/holiday-calendars", data),
        update: (id: string, data: { name: string; description?: string; country?: string }) =>
          apiClient.put<void>(`/api/v1/admin/master-data/holiday-calendars/${id}`, data),
        deactivate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/holiday-calendars/${id}/deactivate`, {}),
        activate: (id: string) =>
          apiClient.patch<void>(`/api/v1/admin/master-data/holiday-calendars/${id}/activate`, {}),
        listEntries: (calendarId: string) =>
          apiClient.get<HolidayEntryRow[]>(`/api/v1/admin/master-data/holiday-calendars/${calendarId}/entries`),
        addEntry: (
          calendarId: string,
          data: {
            name: string;
            entryType: string;
            month: number;
            day?: number;
            nthOccurrence?: number;
            dayOfWeek?: number;
            specificYear?: number;
          },
        ) => apiClient.post<{ id: string }>(`/api/v1/admin/master-data/holiday-calendars/${calendarId}/entries`, data),
        updateEntry: (
          calendarId: string,
          entryId: string,
          data: {
            name: string;
            entryType: string;
            month: number;
            day?: number;
            nthOccurrence?: number;
            dayOfWeek?: number;
            specificYear?: number;
          },
        ) => apiClient.put<void>(`/api/v1/admin/master-data/holiday-calendars/${calendarId}/entries/${entryId}`, data),
        deleteEntry: (calendarId: string, entryId: string) =>
          apiClient.delete<void>(`/api/v1/admin/master-data/holiday-calendars/${calendarId}/entries/${entryId}`),
      },
    },
  },

  /**
   * Daily approval endpoints (supervisor)
   */
  dailyApproval: {
    getEntries: (params?: { dateFrom?: string; dateTo?: string; memberId?: string }) => {
      const query = new URLSearchParams();
      if (params?.dateFrom) query.set("dateFrom", params.dateFrom);
      if (params?.dateTo) query.set("dateTo", params.dateTo);
      if (params?.memberId) query.set("memberId", params.memberId);
      return apiClient.get<
        Array<{
          date: string;
          members: Array<{
            memberId: string;
            memberName: string;
            entries: Array<{
              entryId: string;
              projectCode: string;
              projectName: string;
              hours: number;
              comment: string | null;
              approvalId: string | null;
              approvalStatus: string | null;
              approvalComment: string | null;
            }>;
          }>;
        }>
      >(`/api/v1/worklog/daily-approvals?${query}`);
    },
    approve: (data: { entryIds: string[]; comment?: string }) =>
      apiClient.post<void>("/api/v1/worklog/daily-approvals/approve", data),
    reject: (data: { entryId: string; comment: string }) =>
      apiClient.post<void>("/api/v1/worklog/daily-approvals/reject", data),
    recall: (approvalId: string) => apiClient.post<void>(`/api/v1/worklog/daily-approvals/${approvalId}/recall`, {}),
  },

  /**
   * Notification endpoints
   */
  notification: {
    list: (params?: { page?: number; size?: number; isRead?: boolean }) => {
      const query = new URLSearchParams();
      if (params?.page !== undefined) query.set("page", params.page.toString());
      if (params?.size !== undefined) query.set("size", params.size.toString());
      if (params?.isRead !== undefined) query.set("isRead", params.isRead.toString());
      return apiClient.get<{
        content: Array<{
          id: string;
          type: string;
          referenceId: string;
          title: string;
          message: string;
          isRead: boolean;
          createdAt: string;
        }>;
        unreadCount: number;
        totalElements: number;
        totalPages: number;
        number: number;
      }>(`/api/v1/notifications?${query}`);
    },
    markRead: (id: string) => apiClient.patch<void>(`/api/v1/notifications/${id}/read`, {}),
    markAllRead: () => apiClient.patch<void>("/api/v1/notifications/read-all", {}),
  },

  userStatus: {
    async getStatus(): Promise<UserStatusResponse> {
      return apiClient.get("/api/v1/user/status");
    },
    async selectTenant(tenantId: string): Promise<void> {
      return apiClient.post("/api/v1/user/select-tenant", { tenantId });
    },
  },

  user: {
    updateLocale: (locale: string) => apiClient.patch<void>("/api/v1/user/locale", { locale }),
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
    // biome-ignore lint/suspicious/noConsole: intentional warning for auth diagnostics
    console.warn("checkAuth: non-auth error occurred, assuming authenticated:", error);
    return true;
  }
}

/**
 * System default pattern settings
 */
interface SystemDefaultPatterns {
  fiscalYearStartMonth: number;
  fiscalYearStartDay: number;
  monthlyPeriodStartDay: number;
}

/**
 * Effective patterns for an organization (resolved from inheritance chain)
 */
interface EffectivePatterns {
  fiscalYearPatternId: string | null;
  fiscalYearSource: string;
  fiscalYearSourceName: string | null;
  monthlyPeriodPatternId: string | null;
  monthlyPeriodSource: string;
  monthlyPeriodSourceName: string | null;
}

/**
 * Organization tree node type for tree view
 */
interface OrganizationTreeNode {
  id: string;
  code: string;
  name: string;
  level: number;
  status: "ACTIVE" | "INACTIVE";
  memberCount: number;
  children: OrganizationTreeNode[];
}

/**
 * Export organization types for use in components
 */
export type {
  OrganizationRow,
  OrganizationPage,
  OrganizationMemberRow,
  MemberPage,
  OrganizationTreeNode,
  FiscalYearPatternOption,
  MonthlyPeriodPatternOption,
  SystemDefaultPatterns,
  EffectivePatterns,
};
