"use client";

/**
 * ErrorBoundary Component
 *
 * Catches JavaScript errors in child component tree and displays
 * a fallback UI instead of crashing the whole app.
 */

import { Component, type ReactNode } from "react";
import { useTranslations } from "next-intl";

interface ErrorBoundaryProps {
  /** Child components to wrap */
  children: ReactNode;
  /** Custom fallback UI */
  fallback?: ReactNode;
  /** Callback when error is caught */
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
  /** Whether to show retry button */
  showRetry?: boolean;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    // Log error to console in development
    // biome-ignore lint/suspicious/noConsole: error boundary must log caught errors
    console.error("ErrorBoundary caught an error:", error, errorInfo);

    // Call optional error callback
    this.props.onError?.(error, errorInfo);
  }

  handleRetry = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      // Custom fallback provided
      if (this.props.fallback) {
        return this.props.fallback;
      }

      // Default error UI
      return (
        <ErrorFallback
          error={this.state.error}
          onRetry={this.props.showRetry !== false ? this.handleRetry : undefined}
        />
      );
    }

    return this.props.children;
  }
}

/**
 * Default error fallback UI
 */
interface ErrorFallbackProps {
  error: Error | null;
  onRetry?: () => void;
}

export function ErrorFallback({ error, onRetry }: ErrorFallbackProps) {
  const t = useTranslations("errorBoundary");
  return (
    <div
      className="flex min-h-64 flex-col items-center justify-center rounded-lg border border-red-200 bg-red-50 p-6"
      role="alert"
      aria-live="assertive"
    >
      <div className="mb-4 text-4xl" aria-hidden="true">
        :(
      </div>
      <h2 className="mb-2 text-lg font-semibold text-red-800">{t("title")}</h2>
      <p className="mb-4 text-center text-sm text-red-600">{error?.message || t("message")}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
          aria-label={t("retryAriaLabel")}
        >
          {t("retry")}
        </button>
      )}
    </div>
  );
}

/**
 * Error message for inline display
 */
interface ErrorMessageProps {
  message: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorMessage({ message, onRetry, className = "" }: ErrorMessageProps) {
  const t = useTranslations("errorBoundary");
  return (
    <div className={`flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-4 ${className}`} role="alert">
      <span className="text-red-500" aria-hidden="true">
        !
      </span>
      <p className="flex-1 text-sm text-red-700">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="text-sm font-medium text-red-600 hover:text-red-800 focus:outline-none focus:underline"
          aria-label={t("retry")}
        >
          {t("retry")}
        </button>
      )}
    </div>
  );
}

/**
 * Hook-friendly error display for async operations
 */
interface AsyncErrorProps {
  error: Error | null;
  isLoading: boolean;
  onRetry?: () => void;
  children: ReactNode;
}

export function AsyncErrorHandler({ error, isLoading, onRetry, children }: AsyncErrorProps) {
  if (isLoading) {
    return null; // Parent should handle loading state
  }

  if (error) {
    return <ErrorMessage message={error.message} onRetry={onRetry} />;
  }

  return <>{children}</>;
}
