# API Contracts: Submit Work Log Entries

**Feature Branch**: `013-submit-worklog-entry`
**Created**: 2026-02-20

## New Endpoints

### POST /api/v1/worklog/entries/submit-daily

Submit all DRAFT entries for a member on a specific date, transitioning them to SUBMITTED status.

**Request Body**:
```json
{
  "memberId": "uuid",
  "date": "2026-02-20",
  "submittedBy": "uuid"
}
```

**Validation**:
- `memberId`: Required (UUID)
- `date`: Required (ISO date, YYYY-MM-DD)
- `submittedBy`: Required (UUID), must equal `memberId` (no proxy submission)

**Success Response** `200 OK`:
```json
{
  "submittedCount": 3,
  "date": "2026-02-20",
  "entries": [
    {
      "id": "uuid",
      "projectId": "uuid",
      "hours": 8.0,
      "status": "SUBMITTED",
      "version": 2
    }
  ]
}
```

**Error Responses**:

| Status | Error Code | Condition |
|--------|-----------|-----------|
| 400 | MEMBER_ID_REQUIRED | memberId missing |
| 400 | DATE_REQUIRED | date missing |
| 400 | SUBMITTED_BY_REQUIRED | submittedBy missing |
| 404 | NO_DRAFT_ENTRIES | No DRAFT entries found for member/date |
| 403 | SELF_SUBMISSION_ONLY | submittedBy does not match memberId |
| 409 | OPTIMISTIC_LOCK_FAILURE | Version conflict on one or more entries |

---

### POST /api/v1/worklog/entries/recall-daily

Recall all SUBMITTED entries for a member on a specific date back to DRAFT status.

**Request Body**:
```json
{
  "memberId": "uuid",
  "date": "2026-02-20",
  "recalledBy": "uuid"
}
```

**Validation**:
- `memberId`: Required (UUID)
- `date`: Required (ISO date, YYYY-MM-DD)
- `recalledBy`: Required (UUID), must equal `memberId`

**Success Response** `200 OK`:
```json
{
  "recalledCount": 3,
  "date": "2026-02-20",
  "entries": [
    {
      "id": "uuid",
      "projectId": "uuid",
      "hours": 8.0,
      "status": "DRAFT",
      "version": 3
    }
  ]
}
```

**Error Responses**:

| Status | Error Code | Condition |
|--------|-----------|-----------|
| 400 | MEMBER_ID_REQUIRED | memberId missing |
| 400 | DATE_REQUIRED | date missing |
| 400 | RECALLED_BY_REQUIRED | recalledBy missing |
| 404 | NO_SUBMITTED_ENTRIES | No SUBMITTED entries found for member/date |
| 403 | SELF_RECALL_ONLY | recalledBy does not match memberId |
| 422 | RECALL_BLOCKED_BY_APPROVAL | Entries are part of a MonthlyApproval that has been approved/rejected or is in SUBMITTED status |
| 409 | OPTIMISTIC_LOCK_FAILURE | Version conflict on one or more entries |

---

## Security Notes

> **TODO (pre-existing pattern):** Both `submittedBy` and `recalledBy` are currently client-supplied fields and are not validated against the authenticated user's identity from `SecurityContext`. This matches the existing pattern used throughout the codebase (e.g., `enteredBy` in entry creation). When Spring Security integration is completed (tracked separately), these fields should be derived from the authenticated session rather than accepted from the request body.

---

## Existing Endpoints (No Changes)

The following endpoints remain unchanged but are relevant to this feature:

### GET /api/v1/worklog/entries

Already supports `status` query parameter for filtering — can be used to check for DRAFT/SUBMITTED entries.

### GET /api/v1/worklog/calendar/{year}/{month}

Already returns per-day `DailyCalendarEntry` with aggregate status field. After submit/recall, re-fetching this endpoint reflects the updated status. No changes needed.

### PATCH /api/v1/worklog/entries/{id}

Continues to work for DRAFT entries only. SUBMITTED entries will return 422 (NOT_EDITABLE) as before.

---

## Frontend API Client Additions

```typescript
// api.ts additions
worklog: {
  // ... existing methods ...

  submitDailyEntries(request: {
    memberId: string;
    date: string;       // ISO YYYY-MM-DD
    submittedBy: string;
  }): Promise<{
    submittedCount: number;
    date: string;
    entries: Array<{
      id: string;
      projectId: string;
      hours: number;
      status: "SUBMITTED";
      version: number;
    }>;
  }>;

  recallDailyEntries(request: {
    memberId: string;
    date: string;       // ISO YYYY-MM-DD
    recalledBy: string;
  }): Promise<{
    recalledCount: number;
    date: string;
    entries: Array<{
      id: string;
      projectId: string;
      hours: number;
      status: "DRAFT";
      version: number;
    }>;
  }>;
}
```
