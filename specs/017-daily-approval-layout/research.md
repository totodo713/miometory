# Research: Daily Approval Layout Improvement

**Date**: 2026-02-21

## Date Formatting in Japanese Locale

**Decision**: Use `Intl.DateTimeFormat` with `ja-JP` locale
**Rationale**: Built-in browser API, no external dependency. Supports year, month, day, and weekday formatting natively. Already available in all target browsers.
**Alternatives considered**:
- `date-fns` format with Japanese locale — adds dependency for a single use case
- Manual string formatting — error-prone, harder to maintain

**Implementation**:
```typescript
function formatDateJapanese(dateStr: string): string {
  const date = new Date(dateStr + "T00:00:00");
  return new Intl.DateTimeFormat("ja-JP", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
}
// "2026-02-21" → "2026年2月21日(土)"
```

## Summary Statistics Computation

**Decision**: Compute client-side from loaded groups data
**Rationale**: The API already returns all entries for the selected date range. Computing counts client-side avoids an additional API call and keeps the feature frontend-only.
**Alternatives considered**:
- Dedicated summary API endpoint — over-engineering for counting already-loaded data

## No Additional Dependencies

**Decision**: No new npm packages needed
**Rationale**: All requirements can be met with existing Tailwind CSS classes, native browser APIs, and React patterns already used in the codebase.
