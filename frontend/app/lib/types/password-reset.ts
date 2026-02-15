/**
 * TypeScript type definitions for password reset feature
 * 
 * This file contains all entity types, validation error types, and API response types
 * used throughout the password reset flow.
 * 
 * @see specs/005-password-reset-frontend/data-model.md
 */

/**
 * Form state for password reset request page
 * User enters email address to receive password reset link
 */
export interface PasswordResetRequestForm {
  /** User's email address */
  email: string;
  /** Loading state during API request */
  isLoading: boolean;
  /** Validation errors keyed by field name */
  errors: Record<string, ValidationError>;
}

/**
 * Form state for password reset confirmation page
 * User enters new password with token from email link
 */
export interface PasswordResetConfirmForm {
  /** Password reset token from URL query parameter */
  token: string;
  /** New password to set */
  newPassword: string;
  /** Confirmation password (must match newPassword) */
  confirmPassword: string;
  /** Password strength analysis result */
  passwordStrength: PasswordStrengthResult | null;
  /** Loading state during API request */
  isLoading: boolean;
  /** Validation errors keyed by field name */
  errors: Record<string, ValidationError>;
}

/**
 * Validation error for a single form field
 */
export interface ValidationError {
  /** Form field name (e.g., "email", "newPassword") */
  field: string;
  /** User-friendly error message (localized) */
  message: string;
  /** Error type for programmatic handling */
  type: 'required' | 'format' | 'mismatch' | 'length' | 'strength';
}

/**
 * Client-side rate limiting state
 * Tracks password reset request attempts to prevent abuse
 */
export interface RateLimitState {
  /** Array of attempt timestamps (milliseconds since epoch) */
  attempts: number[];
  /** Whether user can make another request */
  isAllowed: boolean;
  /** Number of remaining attempts before rate limit */
  remainingAttempts: number;
  /** Timestamp when rate limit will reset (null if not rate limited) */
  resetTime: number | null;
}

/**
 * Password strength analysis result from zxcvbn
 */
export interface PasswordStrengthResult {
  /** Strength category for UI display */
  strength: 'weak' | 'medium' | 'strong';
  /** Raw zxcvbn score (0-4) */
  score: number;
  /** User-friendly feedback messages (localized) */
  feedback: string[];
  /** Time to crack estimate (for logging/analytics only) */
  crackTimeDisplay: string;
}

/**
 * API response wrapper for password reset endpoints
 */
export interface ApiResponse<T = unknown> {
  /** Whether request was successful */
  success: boolean;
  /** Response data (if successful) */
  data?: T;
  /** Error message (if failed, localized by backend) */
  message?: string;
  /** Error code for programmatic handling */
  errorCode?: string;
  /** HTTP status code */
  status: number;
}

/**
 * Error state for API request failures
 */
export interface ErrorState {
  /** Error type for UI rendering */
  type: 'network' | 'validation' | 'expired_token' | 'rate_limit' | 'server' | null;
  /** User-friendly error message (localized) */
  message: string;
  /** Whether error is retryable (show retry button) */
  isRetryable: boolean;
  /** Original error code from backend (optional) */
  errorCode?: string;
}

/**
 * Password reset request payload (request page)
 */
export interface PasswordResetRequestPayload {
  email: string;
}

/**
 * Password reset confirmation payload (confirm page)
 */
export interface PasswordResetConfirmPayload {
  token: string;
  newPassword: string;
}

/**
 * Password reset request response (always success for anti-enumeration)
 */
export interface PasswordResetRequestResponse {
  message: string;
}

/**
 * Password reset confirmation response
 */
export interface PasswordResetConfirmResponse {
  message: string;
}
