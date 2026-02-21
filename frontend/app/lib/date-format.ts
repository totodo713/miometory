/**
 * Formats an ISO date string (e.g., "2026-02-21") into Japanese format
 * with year and day-of-week (e.g., "2026年2月21日(土)").
 */
export function formatDateJapanese(dateStr: string): string {
  const date = new Date(`${dateStr}T00:00:00`);
  return new Intl.DateTimeFormat("ja-JP", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
}
