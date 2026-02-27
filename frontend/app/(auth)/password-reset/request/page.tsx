"use client";

/**
 * Password Reset Request Page
 *
 * Allows users to request a password reset email by entering their email address.
 *
 * Features:
 * - Email validation (format check)
 * - Client-side rate limiting (3 requests per 5 minutes)
 * - Success message (always shown for anti-enumeration)
 * - Error handling with retry button
 * - Accessibility (WCAG 2.1 AA)
 *
 * User Story 1 (P1): Request Password Reset
 * @see specs/005-password-reset-frontend/spec.md
 */

import Link from "next/link";
import { useTranslations } from "next-intl";
import type React from "react";
import { useEffect, useState } from "react";
import { useToast } from "@/hooks/useToast";
import type { ErrorState, RateLimitState, ValidationError } from "@/lib/types/password-reset";
import { checkRateLimit, getMinutesUntilReset, recordAttempt, setupStorageListener } from "@/lib/utils/rate-limit";
import { validateEmail } from "@/lib/validation/password";
import { api } from "@/services/api";

export default function PasswordResetRequestPage() {
  const toast = useToast();
  const t = useTranslations("passwordReset.request");
  const tc = useTranslations("passwordReset.common");

  // Form state
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [validationError, setValidationError] = useState<ValidationError | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Rate limiting state
  const [rateLimitState, setRateLimitState] = useState<RateLimitState>(() => checkRateLimit());

  // Success state
  const [isSuccess, setIsSuccess] = useState(false);

  // Error state
  const [error, setError] = useState<ErrorState>({
    type: null,
    message: "",
    isRetryable: false,
  });

  // Set up cross-tab rate limit synchronization
  useEffect(() => {
    const cleanup = setupStorageListener(() => {
      setRateLimitState(checkRateLimit());
    });
    return cleanup;
  }, []);

  const validateField = (name: string, value: string) => {
    if (name === "email" && !value.trim()) return t("errors.emailRequired");
    if (name === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return t("errors.emailInvalid");
    return "";
  };

  const handleBlur = (name: string, value: string) => {
    const fieldError = validateField(name, value);
    setFieldErrors((prev) => ({ ...prev, [name]: fieldError }));
  };

  const hasFieldErrors = Object.values(fieldErrors).some((e) => e !== "");

  /**
   * Handle form submission
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Reset states
    setValidationError(null);
    setError({ type: null, message: "", isRetryable: false });
    setIsSuccess(false);

    // Validate email
    const emailError = validateEmail(email, {
      emailRequired: t("errors.emailRequired"),
      emailInvalid: t("errors.emailInvalid"),
    });
    if (emailError) {
      setValidationError(emailError);
      return;
    }

    // Check rate limit
    const rateLimitCheck = checkRateLimit();
    setRateLimitState(rateLimitCheck);

    if (!rateLimitCheck.isAllowed) {
      const minutes = rateLimitCheck.resetTime ? getMinutesUntilReset(rateLimitCheck.resetTime) : 5;
      setError({
        type: "rate_limit",
        message: t("errors.rateLimitExceeded", { minutes }),
        isRetryable: false,
      });
      return;
    }

    // Submit API request
    setIsLoading(true);

    try {
      await api.auth.requestPasswordReset({ email: email.toLowerCase() });

      // Record attempt after successful request
      recordAttempt();
      setRateLimitState(checkRateLimit());

      // Always show success message (anti-enumeration)
      setIsSuccess(true);
      setEmail(""); // Clear email for security
      toast.success(t("toastSuccess"));
    } catch (err) {
      // Handle API errors
      if (err instanceof Error) {
        const isNetworkError = err.message.includes("network") || err.message.includes("Network");

        setError({
          type: isNetworkError ? "network" : "server",
          message: isNetworkError ? t("errors.networkError") : t("errors.serverError"),
          isRetryable: true,
        });
      } else {
        setError({
          type: "server",
          message: t("errors.unknownError"),
          isRetryable: true,
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * Handle retry button click
   */
  const handleRetry = () => {
    setError({ type: null, message: "", isRetryable: false });
  };

  /**
   * Handle email input change
   */
  const handleEmailChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setEmail(e.target.value);
    // Clear validation error on input
    if (validationError) {
      setValidationError(null);
    }
    if (fieldErrors.email) {
      setFieldErrors((prev) => ({ ...prev, email: "" }));
    }
  };

  return (
    <div className="password-reset-request-page">
      <div className="container">
        <h1>{t("title")}</h1>

        {/* Success message */}
        {isSuccess && (
          <div className="success-message" role="alert" aria-live="assertive">
            <h2>{t("successTitle")}</h2>
            <p>{t("successMessage")}</p>
            <Link href="/login" className="back-to-login-link">
              {t("backToLogin")}
            </Link>
          </div>
        )}

        {/* Error message */}
        {error.type && (
          <div className="error-message" role="alert" aria-live="assertive">
            <p>{error.message}</p>
            {error.isRetryable && (
              <button type="button" onClick={handleRetry} className="retry-button">
                {tc("retry")}
              </button>
            )}
          </div>
        )}

        {/* Rate limit warning */}
        {!rateLimitState.isAllowed && rateLimitState.resetTime && (
          <div className="rate-limit-warning" role="alert" aria-live="polite">
            <p>{t("rateLimitReached", { minutes: getMinutesUntilReset(rateLimitState.resetTime) })}</p>
          </div>
        )}

        {/* Request form */}
        {!isSuccess && (
          <form onSubmit={handleSubmit} noValidate>
            <p className="form-description">{t("description")}</p>

            <div className="form-field">
              <label htmlFor="email">
                {t("emailLabel")}
                <span className="required-indicator">*</span>
              </label>
              <input
                id="email"
                name="email"
                type="email"
                value={email}
                onChange={handleEmailChange}
                onBlur={() => handleBlur("email", email)}
                placeholder={t("emailPlaceholder")}
                required
                aria-required="true"
                aria-invalid={!!(validationError || fieldErrors.email)}
                aria-describedby={validationError || fieldErrors.email ? "email-error" : undefined}
                disabled={isLoading || !rateLimitState.isAllowed}
                style={fieldErrors.email ? { borderColor: "#d32f2f" } : undefined}
              />
              {(validationError || fieldErrors.email) && (
                <span id="email-error" className="field-error" role="alert">
                  {validationError ? validationError.message : fieldErrors.email}
                </span>
              )}
            </div>

            <div className="form-actions">
              <button
                type="submit"
                disabled={isLoading || !rateLimitState.isAllowed || hasFieldErrors}
                aria-busy={isLoading}
              >
                {isLoading ? t("submitting") : t("submitButton")}
              </button>
            </div>

            <div className="form-footer">
              <Link href="/login" className="back-to-login-link">
                {t("backToLogin")}
              </Link>
              {rateLimitState.isAllowed && rateLimitState.remainingAttempts < 3 && (
                <p className="rate-limit-info" aria-live="polite">
                  {t("remainingAttempts", { count: rateLimitState.remainingAttempts })}
                </p>
              )}
            </div>
          </form>
        )}
      </div>

      <style jsx>{`
        .password-reset-request-page {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 1rem;
          background-color: #f5f5f5;
        }

        .container {
          max-width: 400px;
          width: 100%;
          background: white;
          padding: 2rem;
          border-radius: 8px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }

        h1 {
          margin: 0 0 1.5rem;
          font-size: 1.5rem;
          font-weight: 600;
          color: #333;
        }

        .form-description {
          margin: 0 0 1.5rem;
          font-size: 0.875rem;
          color: #666;
          line-height: 1.5;
        }

        .form-field {
          margin-bottom: 1.5rem;
        }

        label {
          display: block;
          margin-bottom: 0.5rem;
          font-size: 0.875rem;
          font-weight: 500;
          color: #333;
        }

        .required-indicator {
          color: #d32f2f;
          margin-left: 0.25rem;
        }

        input[type="email"] {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid #ddd;
          border-radius: 4px;
          font-size: 1rem;
          transition: border-color 0.2s;
        }

        input[type="email"]:focus {
          outline: none;
          border-color: #1976d2;
          box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.1);
        }

        input[type="email"]:disabled {
          background-color: #f5f5f5;
          cursor: not-allowed;
        }

        input[type="email"][aria-invalid="true"] {
          border-color: #d32f2f;
        }

        .field-error {
          display: block;
          margin-top: 0.5rem;
          font-size: 0.875rem;
          color: #d32f2f;
        }

        .form-actions {
          margin-bottom: 1rem;
        }

        button[type="submit"] {
          width: 100%;
          padding: 0.75rem;
          background-color: #1976d2;
          color: white;
          border: none;
          border-radius: 4px;
          font-size: 1rem;
          font-weight: 500;
          cursor: pointer;
          transition: background-color 0.2s;
        }

        button[type="submit"]:hover:not(:disabled) {
          background-color: #1565c0;
        }

        button[type="submit"]:disabled {
          background-color: #ccc;
          cursor: not-allowed;
        }

        .form-footer {
          text-align: center;
        }

        .back-to-login-link {
          display: inline-block;
          font-size: 0.875rem;
          color: #1976d2;
          text-decoration: none;
        }

        .back-to-login-link:hover {
          text-decoration: underline;
        }

        .rate-limit-info {
          margin-top: 0.5rem;
          font-size: 0.75rem;
          color: #666;
        }

        .success-message {
          padding: 1.5rem;
          background-color: #e8f5e9;
          border: 1px solid #4caf50;
          border-radius: 4px;
          margin-bottom: 1.5rem;
        }

        .success-message h2 {
          margin: 0 0 0.5rem;
          font-size: 1.125rem;
          font-weight: 600;
          color: #2e7d32;
        }

        .success-message p {
          margin: 0 0 1rem;
          font-size: 0.875rem;
          color: #1b5e20;
          line-height: 1.5;
        }

        .error-message {
          padding: 1rem;
          background-color: #ffebee;
          border: 1px solid #f44336;
          border-radius: 4px;
          margin-bottom: 1.5rem;
        }

        .error-message p {
          margin: 0 0 0.5rem;
          font-size: 0.875rem;
          color: #c62828;
        }

        .retry-button {
          padding: 0.5rem 1rem;
          background-color: #f44336;
          color: white;
          border: none;
          border-radius: 4px;
          font-size: 0.875rem;
          cursor: pointer;
        }

        .retry-button:hover {
          background-color: #d32f2f;
        }

        .rate-limit-warning {
          padding: 1rem;
          background-color: #fff3e0;
          border: 1px solid #ff9800;
          border-radius: 4px;
          margin-bottom: 1.5rem;
        }

        .rate-limit-warning p {
          margin: 0;
          font-size: 0.875rem;
          color: #e65100;
        }

        @media (max-width: 480px) {
          .container {
            padding: 1.5rem;
          }

          h1 {
            font-size: 1.25rem;
          }
        }
      `}</style>
    </div>
  );
}
