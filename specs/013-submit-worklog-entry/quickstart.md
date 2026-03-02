**Note**: Development now uses devcontainer. See [QUICKSTART.md](/QUICKSTART.md) for current setup instructions.

# Quickstart: Submit Work Log Entries

**Feature Branch**: `013-submit-worklog-entry`
**Created**: 2026-02-20

## Prerequisites

1. Docker running (PostgreSQL dev instance)
2. Backend and frontend dev servers running (see main README)

```bash
cd infra/docker && docker-compose -f docker-compose.dev.yml up -d
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
cd frontend && npm install && npm run dev
```

## What This Feature Adds

- **Submit button** on DailyEntryForm: Transitions all DRAFT entries for a day to SUBMITTED (read-only)
- **Recall button** on DailyEntryForm: Transitions all SUBMITTED entries for a day back to DRAFT (editable), if not yet acted on by a manager
- **Calendar status indicators**: Already exist — no new changes needed

## Backend Changes

### New Files
- `SubmitDailyEntriesRequest.java` — Request DTO
- `RecallDailyEntriesRequest.java` — Request DTO
- `SubmitDailyEntriesCommand.java` — Application command
- `RecallDailyEntriesCommand.java` — Application command

### Modified Files
- `WorkLogController.java` — Two new POST endpoints
- `WorkLogEntryService.java` — Two new service methods (`submitDailyEntries`, `recallDailyEntries`)
- `GlobalExceptionHandler.java` — New error codes if needed

### Key Design Points
- No new domain entities, events, or database migrations
- Uses existing `WorkLogEntry.changeStatus()` and `WorkLogEntryStatusChanged` event
- Atomicity via Spring `@Transactional` on the service method
- Recall checks for MonthlyApproval association before allowing

## Frontend Changes

### New Files
- `SubmitDailyButton.tsx` — Submit/Recall button component with confirmation dialog

### Modified Files
- `DailyEntryForm.tsx` — Integrates SubmitDailyButton
- `api.ts` — Two new API methods

## Testing Approach

### Backend
- **Unit tests**: WorkLogEntry.changeStatus() transition validation (existing coverage)
- **Integration tests**: New endpoints — submit, recall, error cases, optimistic locking
- **Service tests**: submitDailyEntries atomicity, recall blocked by MonthlyApproval

### Frontend
- **Component tests**: SubmitDailyButton rendering, button states, confirmation dialog
- **Integration tests**: DailyEntryForm with submit/recall flow

## Manual Testing Flow

1. Open http://localhost:3000, log in as test user
2. Navigate to calendar, click on a day with DRAFT entries
3. Verify "Submit" button appears at the bottom of DailyEntryForm
4. Click "Submit" → confirmation dialog appears
5. Confirm → entries transition to SUBMITTED, badges update, form becomes read-only
6. Verify "Recall" button now appears instead of "Submit"
7. Click "Recall" → entries transition back to DRAFT, form becomes editable
8. Navigate back to calendar → verify day status updated
