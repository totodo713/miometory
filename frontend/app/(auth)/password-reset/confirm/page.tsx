"use client";

/**
 * Password Reset Confirmation Page
 *
 * Allows users to set a new password using the token from their password reset email.
 *
 * Features:
 * - Token extraction from URL query parameter
 * - Token stored in sessionStorage as backup (survives page refresh)
 * - URL cleaned with router.replace() to prevent token exposure in browser history
 * - Password validation (8-128 chars, uppercase, lowercase, digit)
 * - Password confirmation matching
 * - Auto-redirect to login page on success (3-second delay)
 * - Error handling (invalid/expired token, validation, network errors)
 * - Accessibility (WCAG 2.1 AA)
 *
 * User Story 2 (P2): Confirm Password Reset
 * @see specs/005-password-reset-frontend/spec.md
 */

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import type React from "react";
import { Suspense, useEffect, useState } from "react";
import type { ErrorState, ValidationError } from "@/lib/types/password-reset";
import { validatePasswordConfirm } from "@/lib/validation/password";
import { api } from "@/services/api";

// sessionStorage key for token backup
const TOKEN_STORAGE_KEY = "password_reset_token";

/**
 * Inner component that uses useSearchParams
 * Wrapped in Suspense boundary by parent component
 */
function PasswordResetConfirmForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  // Token state
  const [token, setToken] = useState<string | null>(null);
  const [isTokenReady, setIsTokenReady] = useState(false);

  // Form state
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [validationErrors, setValidationErrors] = useState<
    Record<string, ValidationError>
  >({});

  // Success state
  const [isSuccess, setIsSuccess] = useState(false);
  const [redirectCountdown, setRedirectCountdown] = useState(3);

  // Error state
  const [error, setError] = useState<ErrorState>({
    type: null,
    message: "",
    isRetryable: false,
  });

  /**
   * Extract token from URL and store in sessionStorage
   * Clean URL to remove token from browser history
   */
  useEffect(() => {
    // Extract token from URL query parameter
    const urlToken = searchParams.get("token");

    if (urlToken) {
      // Store token in sessionStorage as backup
      try {
        sessionStorage.setItem(TOKEN_STORAGE_KEY, urlToken);
      } catch (e) {
        // Ignore sessionStorage errors (private browsing mode, etc.)
        console.warn("Failed to store token in sessionStorage:", e);
      }

      // Clean URL to remove token from browser history
      router.replace("/password-reset/confirm", { scroll: false });

      setToken(urlToken);
      setIsTokenReady(true);
    } else {
      // Try to retrieve token from sessionStorage
      try {
        const storedToken = sessionStorage.getItem(TOKEN_STORAGE_KEY);
        if (storedToken) {
          setToken(storedToken);
          setIsTokenReady(true);
        } else {
          // No token available
          setError({
            type: "expired_token",
            message:
              "無効なリンクです。パスワードリセットを再度リクエストしてください。",
            isRetryable: false,
          });
          setIsTokenReady(true);
        }
      } catch (e) {
        // sessionStorage not available
        console.warn("Failed to retrieve token from sessionStorage:", e);
        setError({
          type: "expired_token",
          message:
            "無効なリンクです。パスワードリセットを再度リクエストしてください。",
          isRetryable: false,
        });
        setIsTokenReady(true);
      }
    }
  }, [searchParams, router]);

  /**
   * Auto-redirect countdown after success
   */
  useEffect(() => {
    if (!isSuccess) return;

    if (redirectCountdown === 0) {
      router.push("/login");
      return;
    }

    const timer = setTimeout(() => {
      setRedirectCountdown((prev) => prev - 1);
    }, 1000);

    return () => clearTimeout(timer);
  }, [isSuccess, redirectCountdown, router]);

  /**
   * Handle form submission
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Reset states
    setValidationErrors({});
    setError({ type: null, message: "", isRetryable: false });
    setIsSuccess(false);

    // Check token availability
    if (!token) {
      setError({
        type: "expired_token",
        message:
          "無効なリンクです。パスワードリセットを再度リクエストしてください。",
        isRetryable: false,
      });
      return;
    }

    // Validate passwords
    const errors = validatePasswordConfirm(newPassword, confirmPassword, token);
    if (Object.keys(errors).length > 0) {
      setValidationErrors(errors);
      return;
    }

    // Submit API request
    setIsLoading(true);

    try {
      await api.auth.confirmPasswordReset({ token, newPassword });

      // Clear token from sessionStorage on success
      try {
        sessionStorage.removeItem(TOKEN_STORAGE_KEY);
      } catch (_e) {
        // Ignore errors
      }

      // Show success message and start countdown
      setIsSuccess(true);
      setRedirectCountdown(3);
    } catch (err: unknown) {
      // Classify error and show appropriate message
      const apiError = err as {
        status?: number;
        message?: string;
        errorCode?: string;
      };

      if (apiError.status === 404) {
        // Invalid or expired token
        setError({
          type: "expired_token",
          message:
            "リンクの有効期限が切れています。パスワードリセットを再度リクエストしてください。",
          isRetryable: false,
          errorCode: apiError.errorCode,
        });

        // Clear invalid token from sessionStorage
        try {
          sessionStorage.removeItem(TOKEN_STORAGE_KEY);
        } catch (_e) {
          // Ignore errors
        }
      } else if (apiError.status === 400) {
        // Validation error from backend
        setError({
          type: "validation",
          message: apiError.message || "パスワードが要件を満たしていません。",
          isRetryable: false,
          errorCode: apiError.errorCode,
        });
      } else if (apiError.status === 0 || apiError.status === undefined) {
        // Network error
        setError({
          type: "network",
          message:
            "ネットワークエラーが発生しました。接続を確認して再試行してください。",
          isRetryable: true,
        });
      } else {
        // Server error (500, etc.)
        setError({
          type: "server",
          message:
            "サーバーエラーが発生しました。しばらくしてから再試行してください。",
          isRetryable: true,
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * Handle manual retry
   */
  const handleRetry = () => {
    setError({ type: null, message: "", isRetryable: false });
    setValidationErrors({});
  };

  /**
   * Handle password field change
   */
  const handleNewPasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setNewPassword(e.target.value);
    // Clear validation error for this field
    if (validationErrors.newPassword) {
      const { newPassword: _, ...rest } = validationErrors;
      setValidationErrors(rest);
    }
  };

  /**
   * Handle confirm password field change
   */
  const handleConfirmPasswordChange = (
    e: React.ChangeEvent<HTMLInputElement>,
  ) => {
    setConfirmPassword(e.target.value);
    // Clear validation error for this field
    if (validationErrors.confirmPassword) {
      const { confirmPassword: _, ...rest } = validationErrors;
      setValidationErrors(rest);
    }
  };

  // Show loading state while extracting token
  if (!isTokenReady) {
    return (
      <div className="container">
        <div className="card">
          <output className="loading" aria-live="polite">
            処理中...
          </output>
        </div>

        <style jsx>{`
          .container {
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 1rem;
            background-color: #f5f5f5;
          }

          .card {
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            padding: 2rem;
            width: 100%;
            max-width: 400px;
          }

          .loading {
            text-align: center;
            color: #666;
          }
        `}</style>
      </div>
    );
  }

  // Show success message
  if (isSuccess) {
    return (
      <div className="container">
        <div className="card">
          <div className="success-icon" aria-hidden="true">
            ✓
          </div>
          <h1 className="title">パスワードを変更しました</h1>
          <p className="message">新しいパスワードでログインできます。</p>
          <output className="redirect-message" aria-live="polite">
            {redirectCountdown}秒後にログインページにリダイレクトします...
          </output>
          <Link href="/login" className="link">
            今すぐログインする
          </Link>
        </div>

        <style jsx>{`
          .container {
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 1rem;
            background-color: #f5f5f5;
          }

          .card {
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            padding: 2rem;
            width: 100%;
            max-width: 400px;
            text-align: center;
          }

          .success-icon {
            font-size: 3rem;
            color: #4caf50;
            margin-bottom: 1rem;
          }

          .title {
            font-size: 1.5rem;
            font-weight: 600;
            color: #333;
            margin: 0 0 1rem;
          }

          .message {
            color: #666;
            margin: 0 0 1rem;
            line-height: 1.5;
          }

          .redirect-message {
            color: #999;
            font-size: 0.875rem;
            margin: 0 0 1.5rem;
          }

          .link {
            display: inline-block;
            color: #1976d2;
            text-decoration: none;
            font-weight: 500;
          }

          .link:hover {
            text-decoration: underline;
          }

          .link:focus {
            outline: 2px solid #1976d2;
            outline-offset: 2px;
          }
        `}</style>
      </div>
    );
  }

  // Show main form
  return (
    <div className="container">
      <div className="card">
        <h1 className="title">新しいパスワードを設定</h1>
        <p className="description">新しいパスワードを入力してください。</p>

        {/* Token error (invalid/expired) */}
        {error.type === "expired_token" && (
          <div className="error-banner" role="alert" aria-live="assertive">
            <p className="error-message">{error.message}</p>
            <Link href="/password-reset/request" className="link">
              パスワードリセットをリクエスト
            </Link>
          </div>
        )}

        {/* Other errors (network/server) */}
        {error.type && error.type !== "expired_token" && (
          <div className="error-banner" role="alert" aria-live="assertive">
            <p className="error-message">{error.message}</p>
            {error.isRetryable && (
              <button
                type="button"
                onClick={handleRetry}
                className="retry-button"
                aria-label="エラーをクリアして再試行"
              >
                再試行
              </button>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          {/* New Password Field */}
          <div className="field">
            <label htmlFor="new-password" className="label">
              新しいパスワード
              <span className="required">*</span>
            </label>
            <input
              type="password"
              id="new-password"
              name="new-password"
              value={newPassword}
              onChange={handleNewPasswordChange}
              placeholder="8文字以上で入力してください"
              disabled={isLoading || error.type === "expired_token"}
              required
              aria-invalid={!!validationErrors.newPassword}
              aria-describedby={
                validationErrors.newPassword ? "new-password-error" : undefined
              }
              className="input"
              autoComplete="new-password"
            />
            {validationErrors.newPassword && (
              <p
                id="new-password-error"
                className="field-error"
                role="alert"
                aria-live="assertive"
              >
                {validationErrors.newPassword.message}
              </p>
            )}
          </div>

          {/* Confirm Password Field */}
          <div className="field">
            <label htmlFor="confirm-password" className="label">
              パスワードの確認
              <span className="required">*</span>
            </label>
            <input
              type="password"
              id="confirm-password"
              name="confirm-password"
              value={confirmPassword}
              onChange={handleConfirmPasswordChange}
              placeholder="もう一度入力してください"
              disabled={isLoading || error.type === "expired_token"}
              required
              aria-invalid={!!validationErrors.confirmPassword}
              aria-describedby={
                validationErrors.confirmPassword
                  ? "confirm-password-error"
                  : undefined
              }
              className="input"
              autoComplete="new-password"
            />
            {validationErrors.confirmPassword && (
              <p
                id="confirm-password-error"
                className="field-error"
                role="alert"
                aria-live="assertive"
              >
                {validationErrors.confirmPassword.message}
              </p>
            )}
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isLoading || error.type === "expired_token"}
            className="submit-button"
            aria-busy={isLoading}
          >
            {isLoading ? "処理中..." : "パスワードを変更"}
          </button>
        </form>

        {/* Back to Login Link */}
        <div className="footer">
          <Link href="/login" className="link">
            ログインに戻る
          </Link>
        </div>
      </div>

      <style jsx>{`
        .container {
          display: flex;
          justify-content: center;
          align-items: center;
          min-height: 100vh;
          padding: 1rem;
          background-color: #f5f5f5;
        }

        .card {
          background: white;
          border-radius: 8px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
          padding: 2rem;
          width: 100%;
          max-width: 400px;
        }

        .title {
          font-size: 1.5rem;
          font-weight: 600;
          color: #333;
          margin: 0 0 0.5rem;
        }

        .description {
          color: #666;
          margin: 0 0 1.5rem;
          line-height: 1.5;
        }

        .error-banner {
          background-color: #ffebee;
          border: 1px solid #ef5350;
          border-radius: 4px;
          padding: 1rem;
          margin-bottom: 1.5rem;
        }

        .error-message {
          color: #c62828;
          margin: 0 0 0.5rem;
          font-size: 0.875rem;
        }

        .retry-button {
          background: none;
          border: none;
          color: #1976d2;
          text-decoration: underline;
          cursor: pointer;
          font-size: 0.875rem;
          padding: 0;
        }

        .retry-button:hover {
          color: #1565c0;
        }

        .retry-button:focus {
          outline: 2px solid #1976d2;
          outline-offset: 2px;
        }

        .field {
          margin-bottom: 1.5rem;
        }

        .label {
          display: block;
          font-weight: 500;
          color: #333;
          margin-bottom: 0.5rem;
        }

        .required {
          color: #d32f2f;
          margin-left: 0.25rem;
        }

        .input {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid #ddd;
          border-radius: 4px;
          font-size: 1rem;
          transition: border-color 0.2s;
        }

        .input:focus {
          outline: none;
          border-color: #1976d2;
          box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.1);
        }

        .input:disabled {
          background-color: #f5f5f5;
          cursor: not-allowed;
          opacity: 0.6;
        }

        .input[aria-invalid='true'] {
          border-color: #d32f2f;
        }

        .field-error {
          color: #d32f2f;
          font-size: 0.875rem;
          margin: 0.5rem 0 0;
        }

        .submit-button {
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

        .submit-button:hover:not(:disabled) {
          background-color: #1565c0;
        }

        .submit-button:focus {
          outline: 2px solid #1976d2;
          outline-offset: 2px;
        }

        .submit-button:disabled {
          background-color: #ccc;
          cursor: not-allowed;
        }

        .footer {
          margin-top: 1.5rem;
          text-align: center;
        }

        .link {
          color: #1976d2;
          text-decoration: none;
          font-size: 0.875rem;
        }

        .link:hover {
          text-decoration: underline;
        }

        .link:focus {
          outline: 2px solid #1976d2;
          outline-offset: 2px;
        }

        @media (max-width: 480px) {
          .card {
            padding: 1.5rem;
          }

          .title {
            font-size: 1.25rem;
          }
        }
      `}</style>
    </div>
  );
}

/**
 * Page component with Suspense boundary
 * Required for useSearchParams() in Next.js 16+
 */
export default function PasswordResetConfirmPage() {
  return (
    <Suspense
      fallback={
        <div className="container">
          <div className="card">
            <output className="loading" aria-live="polite">
              処理中...
            </output>
          </div>

          <style jsx>{`
            .container {
              display: flex;
              justify-content: center;
              align-items: center;
              min-height: 100vh;
              padding: 1rem;
              background-color: #f5f5f5;
            }

            .card {
              background: white;
              border-radius: 8px;
              box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
              padding: 2rem;
              width: 100%;
              max-width: 400px;
            }

            .loading {
              text-align: center;
              color: #666;
            }
          `}</style>
        </div>
      }
    >
      <PasswordResetConfirmForm />
    </Suspense>
  );
}
