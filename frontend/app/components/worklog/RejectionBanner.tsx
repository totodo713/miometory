export interface RejectionBannerProps {
  rejectionReason: string;
  rejectionSource: "monthly" | "daily";
  rejectedByName?: string;
  rejectedAt?: string;
}

/**
 * Reusable banner component for displaying rejection feedback.
 * Used in MonthlySummary, DailyEntryForm, and Calendar views.
 */
export function RejectionBanner({
  rejectionReason,
  rejectionSource,
  rejectedByName,
  rejectedAt,
}: RejectionBannerProps) {
  const sourceLabel = rejectionSource === "monthly" ? "Monthly Rejection" : "Daily Rejection";

  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4" role="alert">
      <div className="flex items-start gap-3">
        <div className="shrink-0 mt-0.5">
          <svg className="h-5 w-5 text-red-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
              clipRule="evenodd"
            />
          </svg>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="text-sm font-semibold text-red-800">{sourceLabel}</h3>
            {rejectedByName && <span className="text-xs text-red-600">by {rejectedByName}</span>}
            {rejectedAt && (
              <span className="text-xs text-red-500">
                {new Date(rejectedAt).toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </span>
            )}
          </div>
          <p className="text-sm text-red-700">{rejectionReason}</p>
        </div>
      </div>
    </div>
  );
}
