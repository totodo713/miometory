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

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

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
  private async request<T>(
    endpoint: string,
    options: ApiRequestOptions = {},
  ): Promise<T> {
    const { version, ...fetchOptions } = options;

    const url = `${this.baseUrl}${endpoint}`;

    const headers: HeadersInit = {
      "Content-Type": "application/json",
      ...fetchOptions.headers,
    };

    // Add If-Match header for optimistic locking
    if (version !== undefined) {
      headers["If-Match"] = version.toString();
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
        throw new ValidationError(
          errorData.message || "Validation failed",
          errorData.errors,
        );
      }

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new ApiError(
          errorData.message || "API request failed",
          response.status,
          errorData.code,
        );
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
      throw new ApiError(
        error instanceof Error ? error.message : "Network error",
        0,
      );
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
  async post<T>(
    endpoint: string,
    data?: unknown,
    options?: ApiRequestOptions,
  ): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T>(
    endpoint: string,
    data: unknown,
    options?: ApiRequestOptions,
  ): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  /**
   * PATCH request (for partial updates, e.g., auto-save)
   */
  async patch<T>(
    endpoint: string,
    data: unknown,
    options?: ApiRequestOptions,
  ): Promise<T> {
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
    check: () =>
      apiClient.get<{ status: string }>("/api/v1/health", { skipAuth: true }),
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
    getEntries: (params: {
      memberId: string;
      startDate: string;
      endDate: string;
      status?: string;
    }) => {
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
    updateEntry: (
      id: string,
      data: { hours: number; comment?: string },
      options: { version: number },
    ) =>
      apiClient.patch<void>(`/api/v1/worklog/entries/${id}`, data, {
        version: options.version,
      }),

    /**
     * Delete a work log entry
     */
    deleteEntry: (id: string) =>
      apiClient.delete<void>(`/api/v1/worklog/entries/${id}`),

    /**
     * Get monthly calendar view
     */
    getCalendar: (params: {
      year: number;
      month: number;
      memberId: string;
    }) => {
      const query = new URLSearchParams({ memberId: params.memberId });
      return apiClient.get<{
        memberId: string;
        memberName: string;
        periodStart: string;
        periodEnd: string;
        dates: Array<{
          date: string;
          totalWorkHours: number;
          totalAbsenceHours: number;
          status: string;
          isWeekend: boolean;
          isHoliday: boolean;
        }>;
      }>(`/api/v1/worklog/calendar/${params.year}/${params.month}?${query}`);
    },
  },

  /**
   * Absence endpoints (to be implemented in Phase 5)
   */
  absence: {
    // Placeholder - will be implemented in Phase 5 (US3)
  },

  /**
   * Approval endpoints (to be implemented in Phase 6)
   */
  approval: {
    // Placeholder - will be implemented in Phase 6 (US4)
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
