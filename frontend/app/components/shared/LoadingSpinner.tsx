/**
 * LoadingSpinner Component
 *
 * A reusable loading indicator for async operations.
 * Supports different sizes and optional label.
 */

interface LoadingSpinnerProps {
  /** Size of the spinner: sm (16px), md (24px), lg (32px), xl (48px) */
  size?: "sm" | "md" | "lg" | "xl";
  /** Optional label displayed below spinner */
  label?: string;
  /** Additional CSS classes */
  className?: string;
}

const SIZE_CLASSES = {
  sm: "h-4 w-4",
  md: "h-6 w-6",
  lg: "h-8 w-8",
  xl: "h-12 w-12",
} as const;

export function LoadingSpinner({ size = "md", label, className = "" }: LoadingSpinnerProps) {
  const sizeClass = SIZE_CLASSES[size];

  return (
    <output className={`flex flex-col items-center justify-center gap-2 ${className}`} aria-live="polite">
      <svg
        className={`animate-spin text-blue-600 ${sizeClass}`}
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path
          className="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        />
      </svg>
      {label && <span className="text-sm text-gray-600">{label}</span>}
      <span className="sr-only">{label || "Loading..."}</span>
    </output>
  );
}

/**
 * Full-page loading overlay
 */
export function LoadingOverlay({ label = "Loading..." }: { label?: string }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-white/80 backdrop-blur-sm">
      <LoadingSpinner size="xl" label={label} />
    </div>
  );
}

/**
 * Inline loading placeholder for content areas
 */
export function LoadingPlaceholder({ height = "h-32", label }: { height?: string; label?: string }) {
  return (
    <div className={`flex items-center justify-center ${height} rounded-lg bg-gray-50`}>
      <LoadingSpinner size="lg" label={label} />
    </div>
  );
}
