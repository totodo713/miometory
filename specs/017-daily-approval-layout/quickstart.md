# Quickstart: Daily Approval Layout Improvement

## Prerequisites

- Frontend dev server running (`cd frontend && npm run dev`)
- Backend running with dev profile (`cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`)
- Dev database with seed data (test users with daily entries)

## Test Scenarios

### Scenario 1: Visual Hierarchy (US1)

1. Login as supervisor (e.g., tanaka@example.com / Password1)
2. Navigate to `/worklog/daily-approval`
3. Verify:
   - Date headers show Japanese format with year and weekday (e.g., "2026年2月21日(金)")
   - Member sections are visually distinct within each date group
   - Table columns are properly aligned
   - Long comments are truncated with tooltip on hover
   - Each member section shows a subtotal row (e.g., "合計: 8.0h")

### Scenario 2: Status Badges and Actions (US2)

1. On the same page, verify:
   - Pending entries show yellow/amber "未承認" badge with checkbox and reject button
   - Approved entries show green "承認済" badge with recall button
   - Rejected entries show red "差戻" badge
   - Action buttons have consistent styling (padding, font size, hover states)

### Scenario 3: Summary Statistics (US3)

1. On the same page, verify:
   - Summary cards appear above the entry list showing counts for 未承認, 承認済, 差戻
   - Counts update when date filter is changed
   - Bulk approve button shows selected count and is visually prominent

### Scenario 4: Edge Cases

1. Set date range to a period with no entries → verify empty state message with descriptive text
2. Resize browser to 1024px width → verify table remains readable
3. Resize below 1024px → verify horizontal scroll appears without layout breaking

## Running Tests

```bash
cd frontend
npm test -- --run tests/unit/components/admin/DailyApprovalDashboard.test.tsx
npm test -- --run tests/unit/lib/date-format.test.ts
```
