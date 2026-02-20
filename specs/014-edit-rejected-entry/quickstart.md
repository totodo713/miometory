# Quickstart: Edit Rejected Work Log Entries

**Feature Branch**: `014-edit-rejected-entry`

## Prerequisites

```bash
# Start infrastructure
cd infra/docker && docker-compose -f docker-compose.dev.yml up -d

# Start backend
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

# Start frontend
cd frontend && npm install && npm run dev
```

## Test Users (from data-dev.sql)

| Role | Email | UUID | Purpose |
|------|-------|------|---------|
| Member | member@example.com | See README | Submit & edit entries |
| Manager | manager@example.com | See README | Approve, reject, proxy edit |

## Manual Testing Flow

### 1. Monthly Rejection Edit (P1 — Member)

1. Login as **member**
2. Navigate to `/worklog` → create entries for the current fiscal month
3. Submit month via MonthlySummary "Submit for Approval" button
4. Login as **manager** → navigate to `/worklog/approval`
5. Reject the submission with reason: "Please correct hours on day X"
6. Login as **member** → navigate to `/worklog`
7. **Verify**: Rejection notification visible on calendar, rejection reason in MonthlySummary
8. Click on a day → **verify**: entry form editable, rejection reason shown
9. Edit entries, add new entries, delete entries
10. Click "Resubmit for Approval" → verify status changes to SUBMITTED

### 2. Proxy Edit of Rejected Entries (P2 — Manager)

1. After step 5 above (month rejected)
2. Login as **manager** → navigate to `/worklog/proxy`
3. Select the subordinate member → enter proxy mode
4. **Verify**: Calendar shows rejected month with visual indicators
5. Click on entries → edit, add new, delete
6. **Verify**: Entries saved with manager as editor (proxy tracking)
7. Resubmit month via proxy → verify submission recorded

### 3. Daily Rejection Edit (P2 — Daily Flow)

1. Login as **member** → create entries for a specific day
2. Submit that day via SubmitDailyButton
3. Login as **manager** → reject daily submission (new endpoint)
4. Login as **member** → **verify**: day marked as rejected on calendar
5. Edit entries for that day → resubmit

## Running Tests

```bash
# Backend
cd backend
./gradlew test --tests "com.worklog.api.*"           # All API tests
./gradlew test --tests "com.worklog.application.*"    # All service tests
./gradlew test --tests "com.worklog.domain.*"         # All domain tests

# Frontend
cd frontend
npm test -- --run                    # All unit tests
npm run test:e2e                     # E2E tests
```

## Key Files to Modify

### Backend

| File | Change |
|------|--------|
| `api/WorkLogController.java` | Add reject-daily endpoint |
| `api/ApprovalController.java` | Add member-facing approval GET endpoint |
| `application/approval/ApprovalService.java` | Handle proxy monthly submission |
| `application/service/WorkLogEntryService.java` | Remove SELF_SUBMISSION_ONLY, add proxy permission check, add daily rejection |
| `domain/worklog/events/` | Add DailyEntriesRejected event (if needed) |
| `infrastructure/` | DailyRejectionLog projection, migration |
| `resources/db/migration/` | V15 migration for daily_rejection_log table |

### Frontend

| File | Change |
|------|--------|
| `components/worklog/Calendar.tsx` | Rejection visual indicators |
| `components/worklog/DailyEntryForm.tsx` | Show rejection reason, enable editing |
| `components/worklog/MonthlySummary.tsx` | Enhance rejection reason display |
| `components/worklog/SubmitDailyButton.tsx` | Support proxy submission |
| `services/api.ts` | New API calls (reject-daily, get approval, daily rejections) |
| `services/worklogStore.ts` | Track rejection state if needed |
