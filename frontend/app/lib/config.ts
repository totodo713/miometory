/**
 * Application configuration constants.
 *
 * Loads environment variables and provides type-safe access to configuration values.
 */

/**
 * API configuration
 */
export const API_CONFIG = {
  /**
   * Base URL for the backend API.
   * Loaded from NEXT_PUBLIC_API_BASE_URL environment variable.
   * Defaults to http://localhost:8080/api/v1 for development.
   */
  baseURL:
    process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080/api/v1",

  /**
   * Request timeout in milliseconds
   */
  timeout: 30000, // 30 seconds

  /**
   * Whether to include credentials (cookies) in cross-origin requests
   */
  withCredentials: true,
} as const;

/**
 * Authentication configuration
 */
export const AUTH_CONFIG = {
  /**
   * Session timeout warning threshold in seconds (25 minutes)
   * Shows warning modal 5 minutes before session expires (30 min timeout)
   */
  sessionWarningThreshold: 25 * 60,

  /**
   * Password validation rules
   */
  password: {
    minLength: 8,
    requireUppercase: true,
    requireLowercase: true,
    requireDigit: true,
    requireSpecialChar: false,
  },

  /**
   * Email validation regex (RFC 5322 simplified)
   */
  emailRegex: /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/,
} as const;

/**
 * Feature flags
 */
export const FEATURES = {
  /**
   * Enable remember-me functionality
   */
  rememberMe: true,

  /**
   * Enable email verification requirement
   */
  emailVerification: true,

  /**
   * Enable password reset functionality
   */
  passwordReset: true,
} as const;
