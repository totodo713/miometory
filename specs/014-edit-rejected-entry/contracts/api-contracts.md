# API Contracts: Edit Rejected Work Log Entries

**Feature Branch**: `014-edit-rejected-entry`
**Date**: 2026-02-20

## New Endpoints

### 1. GET /api/v1/worklog/approvals/member/{memberId}

Retrieve approval status and rejection reason for a member's fiscal month. Used by the member-facing UI (not the manager approval queue).

**Path Parameters**:
- `memberId` (UUID, required): Member whose approval to query

**Query Parameters**:
- `fiscalMonthStart` (LocalDate, required): Start of fiscal month (e.g., 2026-01-21)
- `fiscalMonthEnd` (LocalDate, required): End of fiscal month (e.g., 2026-02-20)

**Response** (200 OK):
```json
{
  "approvalId": "uuid",
  "memberId": "uuid",
  "fiscalMonthStart": "2026-01-21",
  "fiscalMonthEnd": "2026-02-20",
  "status": "REJECTED",
  "submittedAt": "2026-02-15T10:00:00Z",
  "reviewedAt": "2026-02-16T14:30:00Z",
  "reviewedBy": "uuid",
  "reviewerName": "Manager Name",
  "rejectionReason": "Please correct hours on 2/5 and add missing entry for 2/10."
}
```

**Response** (404 Not Found): No approval record exists for this member/period.

---

### 2. POST /api/v1/worklog/entries/reject-daily

Manager rejects daily-submitted entries for a specific day. All SUBMITTED entries for the member on that date are transitioned to DRAFT.

**Request Body**:
```json
{
  "memberId": "uuid",
  "date": "2026-02-05",
  "rejectedBy": "uuid",
  "rejectionReason": "Hours for this day exceed the expected working time."
}
```

**Validation**:
- `memberId`: Required, must exist
- `date`: Required, valid date
- `rejectedBy`: Required, must be a manager with proxy permission for the member
- `rejectionReason`: Required, non-blank, max 1000 characters

**Response** (200 OK):
```json
{
  "rejectedCount": 3,
  "date": "2026-02-05",
  "rejectionReason": "Hours for this day exceed the expected working time.",
  "entries": [
    {
      "id": "uuid",
      "projectId": "uuid",
      "hours": 8.0,
      "status": "DRAFT",
      "version": 4
    }
  ]
}
```

**Error Responses**:
- 403 Forbidden: `PROXY_ENTRY_NOT_ALLOWED` — rejectedBy is not a manager for this member
- 404 Not Found: `NO_SUBMITTED_ENTRIES_FOR_DATE` — no SUBMITTED entries exist for this member/date
- 422 Unprocessable Entity: `REJECT_BLOCKED_BY_APPROVAL` — entries are part of a non-PENDING monthly approval

---

### 3. GET /api/v1/worklog/rejections/daily

Retrieve daily rejection log entries for calendar display.

**Query Parameters**:
- `memberId` (UUID, required): Member whose rejections to query
- `startDate` (LocalDate, required): Range start
- `endDate` (LocalDate, required): Range end

**Response** (200 OK):
```json
{
  "rejections": [
    {
      "date": "2026-02-05",
      "rejectionReason": "Hours for this day exceed the expected working time.",
      "rejectedBy": "uuid",
      "rejectedByName": "Manager Name",
      "rejectedAt": "2026-02-06T09:15:00Z"
    }
  ]
}
```

---

## Modified Endpoints

### 4. GET /api/v1/worklog/calendar (Modified)

Add rejection metadata to calendar response for visual indicators.

**Existing Query Parameters** (unchanged):
- `year`, `month`, `memberId`

**Modified Response** — add fields to each `DailyCalendarEntry`:
```json
{
  "dates": [
    {
      "date": "2026-02-05",
      "totalWorkHours": 8.0,
      "totalAbsenceHours": 0,
      "status": "DRAFT",
      "isWeekend": false,
      "isHoliday": false,
      "hasProxyEntries": false,
      "rejectionSource": "daily",
      "rejectionReason": "Hours for this day exceed the expected working time."
    }
  ],
  "monthlyApproval": {
    "status": "REJECTED",
    "rejectionReason": "Please correct hours on 2/5 and add missing entry for 2/10.",
    "reviewedBy": "uuid",
    "reviewerName": "Manager Name",
    "reviewedAt": "2026-02-16T14:30:00Z"
  }
}
```

**New Fields on `DailyCalendarEntry`**:
- `rejectionSource` (String, nullable): `"daily"` | `"monthly"` | null — indicates why this day shows rejection
- `rejectionReason` (String, nullable): The rejection reason applicable to this day

**New Top-Level Field**:
- `monthlyApproval` (object, nullable): Monthly approval status with rejection reason if applicable

---

### 5. POST /api/v1/worklog/entries/submit-daily (Modified)

Remove `SELF_SUBMISSION_ONLY` constraint. Allow proxy submission with permission check.

**Modified Request Body**:
```json
{
  "memberId": "uuid",
  "date": "2026-02-05",
  "submittedBy": "uuid"
}
```

**New Behavior**:
- If `submittedBy == memberId`: Self-submission (unchanged)
- If `submittedBy != memberId`: Proxy submission
  - Validate proxy permission via `memberRepository.isSubordinateOf(submittedBy, memberId)`
  - Throw `PROXY_ENTRY_NOT_ALLOWED` (403) if not authorized

**Response** (unchanged).

---

### 6. POST /api/v1/worklog/entries/recall-daily (Modified)

Remove `SELF_RECALL_ONLY` constraint. Allow proxy recall with permission check.

**Modified Request Body**:
```json
{
  "memberId": "uuid",
  "date": "2026-02-05",
  "recalledBy": "uuid"
}
```

**New Behavior**:
- If `recalledBy == memberId`: Self-recall (unchanged)
- If `recalledBy != memberId`: Proxy recall
  - Validate proxy permission
  - Throw `PROXY_ENTRY_NOT_ALLOWED` (403) if not authorized

---

### 7. POST /api/v1/worklog/submissions (Modified)

Allow proxy submission (manager submitting month on behalf of member).

**Modified Behavior**:
- If `submittedBy != memberId`: Validate proxy permission
- Currently defaults `submittedBy` to `memberId` if null; keep this default
- Track proxy submission via `submittedBy` field in `MonthSubmittedForApproval` event

---

## Error Codes (New)

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `REJECT_BLOCKED_BY_APPROVAL` | 422 | Cannot reject daily entries that are part of a non-PENDING monthly approval |
| `DAILY_REJECTION_REASON_REQUIRED` | 400 | rejectionReason is required for daily rejection |
| `DAILY_REJECTION_REASON_TOO_LONG` | 400 | rejectionReason exceeds 1000 characters |
| `NO_SUBMITTED_ENTRIES_FOR_DATE` | 404 | No SUBMITTED entries found for the specified member/date |

## Unchanged Endpoints (Confirmed Compatible)

The following existing endpoints require no changes and already support the rejection editing workflow:

- `PATCH /api/v1/worklog/entries/{id}` — Edit entry (works for DRAFT entries after rejection)
- `DELETE /api/v1/worklog/entries/{id}` — Delete entry (works for DRAFT entries after rejection)
- `POST /api/v1/worklog/entries` — Create new entry (works when month is REJECTED, entries created as DRAFT)
- `PATCH /api/v1/absences/{id}` — Edit absence (same as entry)
- `DELETE /api/v1/absences/{id}` — Delete absence (same as entry)
- `POST /api/v1/absences` — Create new absence (same as entry)
- `POST /api/v1/worklog/approvals/{id}/approve` — Approve month (unchanged)
- `POST /api/v1/worklog/approvals/{id}/reject` — Reject month (unchanged)
