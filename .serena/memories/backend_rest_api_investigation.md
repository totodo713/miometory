# Backend REST API Layer - Technical Investigation Report

## Date
2026-02-20

## Summary
Completed comprehensive technical investigation of the Miometry backend REST API layer for work log entries and approval workflow. All key files have been identified and documented with line numbers and relevant code snippets.

## Key Files Identified

### Controllers
1. **WorkLogController** - `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/WorkLogController.java`
2. **ApprovalController** - `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/ApprovalController.java`
3. **CalendarController** - `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/CalendarController.java`
4. **AbsenceController** - `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/AbsenceController.java`

### Request/Response DTOs
Location: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/api/dto/`

- CreateWorkLogEntryRequest.java
- PatchWorkLogEntryRequest.java
- WorkLogEntryResponse.java
- WorkLogEntriesResponse.java
- SubmitMonthRequest.java
- ApproveMonthRequest.java
- RejectMonthRequest.java
- ApprovalQueueResponse.java
- MonthlyCalendarResponse.java
- DailyCalendarEntry.java

### Application Commands
Location: `/home/devman/repos/miometory/backend/src/main/java/com/worklog/application/command/`

- CreateWorkLogEntryCommand.java
- UpdateWorkLogEntryCommand.java
- DeleteWorkLogEntryCommand.java
- SubmitMonthForApprovalCommand.java
- ApproveMonthCommand.java
- RejectMonthCommand.java

### Application Services
- WorkLogEntryService.java (Lines 1-254)
- ApprovalService.java (Lines 1-245)

### Error Handling
- ErrorResponse.java
- GlobalExceptionHandler.java (Lines 1-207)
- DomainException.java

### Security Configuration
- SecurityConfig.kt (Lines 1-118)

## Investigation Details

### 1. WorkLogController (/api/v1/worklog/entries)

**Endpoints:**
- POST /api/v1/worklog/entries - Create entry (201 Created)
- GET /api/v1/worklog/entries - Get entries with filtering (200 OK)
- GET /api/v1/worklog/entries/{id} - Get single entry (200 OK or 404)
- PATCH /api/v1/worklog/entries/{id} - Update entry (204 No Content)
- DELETE /api/v1/worklog/entries/{id} - Delete entry (204 No Content)

**Optimistic Locking:**
- ETag header returned in 201 response (line 65)
- ETag header returned in 200 response (line 115)
- If-Match header required for PATCH (line 131, validation at line 132-134)
- ETag header returned in PATCH 204 response (line 151)
- Version passed as Long from If-Match header

**Error Handling:**
- Controller-level @ExceptionHandler for DomainException (lines 180-211)
- Maps error codes to HTTP status codes:
  - 422 Unprocessable Entity for validation/business rule errors (LIMIT, VALIDATION, INVALID, NEGATIVE, EXCEEDS, FUTURE, TOO_LONG, NOT_EDITABLE, NOT_DELETABLE, INCREMENT)
  - 409 Conflict for optimistic lock errors (OPTIMISTIC_LOCK, VERSION_MISMATCH)
  - 404 Not Found for NOT_FOUND errors
  - 400 Bad Request for other domain errors

### 2. ApprovalController (/api/v1/worklog)

**Endpoints:**
- POST /api/v1/worklog/submissions - Submit month for approval (201 Created)
- GET /api/v1/worklog/approvals/queue - Get pending approvals queue (200 OK)
- POST /api/v1/worklog/approvals/{id}/approve - Approve month (204 No Content)
- POST /api/v1/worklog/approvals/{id}/reject - Reject month (204 No Content)

**Submit Month Flow (lines 58-84):**
- Validates memberId, fiscalMonthStart, fiscalMonthEnd required
- Creates or loads MonthlyApproval aggregate
- Finds all WorkLogEntry and Absence entries for fiscal month
- Transitions entries to SUBMITTED status
- Returns 201 Created with approval ID

**Approval Queue (lines 97-122):**
- GET endpoint with optional managerId parameter
- Returns pending approvals with member name, fiscal month, total hours, submission date

**Approve Month (lines 139-152):**
- POST endpoint with id path parameter
- Requires reviewedBy in request body
- Transitions entries to APPROVED status (permanent lock)

**Reject Month (lines 170-186):**
- POST endpoint with id path parameter
- Requires reviewedBy and rejectionReason in request body
- rejectionReason must not exceed 1000 characters
- Transitions entries back to DRAFT status (editable)

### 3. Request/Response DTOs

**CreateWorkLogEntryRequest.java:**
```java
record CreateWorkLogEntryRequest(
    UUID memberId,
    UUID projectId,
    LocalDate date,
    BigDecimal hours,
    String comment,
    UUID enteredBy
)
```

**PatchWorkLogEntryRequest.java:**
```java
record PatchWorkLogEntryRequest(
    BigDecimal hours,
    String comment
)
```

**WorkLogEntryResponse.java:**
```java
record WorkLogEntryResponse(
    UUID id,
    UUID memberId,
    UUID projectId,
    LocalDate date,
    BigDecimal hours,
    String comment,
    String status,
    UUID enteredBy,
    Instant createdAt,
    Instant updatedAt,
    Long version
)
```

**SubmitMonthRequest.java:**
- Includes validation in record constructor
- memberId required
- fiscalMonthStart, fiscalMonthEnd required
- submittedBy optional (defaults to memberId if not provided)

**ApproveMonthRequest.java:**
- reviewedBy required

**RejectMonthRequest.java:**
- reviewedBy required
- rejectionReason required (non-blank, max 1000 characters)

**ApprovalQueueResponse.java:**
```java
record ApprovalQueueResponse(
    List<PendingApproval> pendingApprovals,
    int totalCount
) {
    record PendingApproval(
        String approvalId,
        String memberId,
        String memberName,
        LocalDate fiscalMonthStart,
        LocalDate fiscalMonthEnd,
        BigDecimal totalWorkHours,
        BigDecimal totalAbsenceHours,
        Instant submittedAt,
        String submittedByName
    )
}
```

### 4. Application Commands

**CreateWorkLogEntryCommand.java:**
- memberId: Member who worked
- projectId: Project worked on
- date: Date of work
- hours: Hours worked (0.25h increments, max 24h)
- comment: Optional (max 500 chars)
- enteredBy: Who entered the data (for proxy entries)

**UpdateWorkLogEntryCommand.java:**
- id: Entry to update
- hours: New hours
- comment: New comment
- updatedBy: Who is updating
- version: Current version for optimistic locking

**DeleteWorkLogEntryCommand.java:**
- id: Entry to delete
- deletedBy: Who is deleting

**SubmitMonthForApprovalCommand.java:**
- memberId: Member submitting
- fiscalMonth: FiscalMonthPeriod (start and end dates)
- submittedBy: Who submitted

**ApproveMonthCommand.java:**
- approvalId: MonthlyApprovalId
- reviewedBy: MemberId of manager approving

**RejectMonthCommand.java:**
- approvalId: MonthlyApprovalId
- reviewedBy: MemberId of manager rejecting
- rejectionReason: Required feedback (max 1000 chars)

### 5. WorkLogEntryService (Lines 1-254)

**Key Methods:**

**createEntry(CreateWorkLogEntryCommand):**
- Validates proxy entry permission if enteredBy != memberId (lines 57-59)
- Checks for duplicate entry (lines 62-66)
- Validates hours format (0.25h increments) (line 69)
- Validates 24-hour daily limit (lines 72-74)
- Creates aggregate and persists (lines 77-88)
- Returns entry UUID

**updateEntry(UpdateWorkLogEntryCommand):**
- Finds entry by ID or throws ENTRY_NOT_FOUND (lines 102-104)
- Checks version for optimistic locking (lines 107-111)
- Validates hours format (line 114)
- Validates 24-hour daily limit excluding current entry (line 117)
- Updates and persists (lines 120-123)

**deleteEntry(DeleteWorkLogEntryCommand):**
- Finds entry by ID or throws ENTRY_NOT_FOUND (lines 134-136)
- Calls entry.delete() and persists (lines 139-142)

**validateDailyLimit(UUID memberId, LocalDate date, TimeAmount newHours, UUID excludeEntryId):**
- Gets existing total for date (line 156)
- Adds new hours and compares to 24-hour limit (lines 158-160)
- Throws DAILY_LIMIT_EXCEEDED if exceeded (lines 161-166)

**validateProxyEntryPermission(UUID managerId, UUID memberId):**
- Checks if member is subordinate (direct or indirect) of manager (line 227)
- Throws PROXY_ENTRY_NOT_ALLOWED if not (lines 230-236)

**canEnterTimeFor(UUID managerId, UUID memberId):**
- Returns true if same person or if manager is subordinate (lines 248-251)

### 6. ApprovalService (Lines 1-245)

**submitMonth(SubmitMonthForApprovalCommand):**
- Finds or creates MonthlyApproval aggregate (lines 71-73)
- Finds all WorkLogEntry IDs for fiscal month (lines 76-79)
- Finds all Absence IDs for fiscal month (lines 81-84)
- Calls aggregate.submit() (line 92)
- Updates all WorkLogEntry statuses to SUBMITTED (lines 95-102)
- Updates all Absence statuses to SUBMITTED (lines 105-111)
- Saves approval aggregate (line 114)
- Returns approval ID (line 116)

**approveMonth(ApproveMonthCommand):**
- Loads approval aggregate or throws APPROVAL_NOT_FOUND (lines 135-138)
- TODO: Validate manager permission (line 140)
- Calls aggregate.approve() (line 145)
- Updates all WorkLogEntry statuses to APPROVED (lines 148-154)
- Updates all Absence statuses to APPROVED (lines 158-164)
- Saves approval aggregate (line 167)

**rejectMonth(RejectMonthCommand):**
- Loads approval aggregate or throws APPROVAL_NOT_FOUND (lines 186-189)
- TODO: Validate manager permission (line 191)
- Calls aggregate.reject() with reason (line 196)
- Updates all WorkLogEntry statuses to DRAFT (lines 199-205)
- Updates all Absence statuses to DRAFT (lines 209-214)
- Saves approval aggregate (line 218)

### 7. Error Handling

**GlobalExceptionHandler.java (Lines 1-207):**

**DomainException Handler (lines 31-44):**
- Returns 422 Unprocessable Entity for state violations
- Returns 400 Bad Request for validation errors
- Includes error code and message

**State Violation Detection (lines 49-61):**
- Checks for: ALREADY_SUBMITTED, ALREADY_APPROVED, ALREADY_REJECTED, NOT_SUBMITTED, NOT_PENDING, INVALID_STATUS_TRANSITION, CANNOT_MODIFY, CANNOT_SUBMIT, CANNOT_APPROVE, CANNOT_REJECT

**OptimisticLockException Handler (lines 67-81):**
- Returns 409 Conflict
- Includes aggregateType, aggregateId, expectedVersion, actualVersion

**Validation Exception Handler (lines 87-104):**
- Returns 400 Bad Request
- Includes field-level validation errors

**Type Mismatch Exception Handler (lines 110-130):**
- Returns 400 Bad Request
- For invalid parameter types (e.g., invalid UUID format)

**JSON Parsing Exception Handler (lines 137-167):**
- Returns 400 Bad Request
- Extracts specific error codes: MEMBER_ID_REQUIRED, FISCAL_MONTH_REQUIRED, REVIEWED_BY_REQUIRED, REJECTION_REASON_REQUIRED, REJECTION_REASON_TOO_LONG

**ErrorResponse Structure:**
```java
record ErrorResponse(
    String errorCode,
    String message,
    Instant timestamp,
    Map<String, Object> details
)
```

### 8. Current User Resolution

**Current State (TODOs throughout codebase):**
- WorkLogController line 47-50: Uses request.enteredBy() if present, otherwise defaults to request.memberId()
- WorkLogController line 137: TODO comment to get updatedBy from SecurityContext
- WorkLogController line 165: TODO comment to get deletedBy from SecurityContext
- ApprovalController line 68-71: Uses request.submittedBy() if present, otherwise defaults to request.memberId()
- AbsenceController line 48-50: Uses request.recordedBy() if present, otherwise defaults to request.memberId()

**Security Context:**
- Not yet integrated with actual authentication
- Development profile permits all requests (SecurityConfig.kt lines 42-59)
- Production profile uses cookie-based CSRF token with SPA support (lines 72-117)
- TODO: Enable OAuth2/OIDC and SAML2 for production (lines 22-24)

### 9. Authorization & Member Ownership

**Current Pattern:**
- Controllers require memberId parameter for read operations
- No enforcement of "own entries only" pattern currently implemented
- Proxy entry permission checking in WorkLogEntryService.validateProxyEntryPermission() (lines 222-237)

**Proxy Entry Logic:**
- Manager can enter time for direct/indirect subordinates
- Checked via memberRepository.isSubordinateOf() (line 227)
- Throws PROXY_ENTRY_NOT_ALLOWED if not authorized

### 10. Key Business Rules

**Work Log Entry Constraints:**
- Hours in 0.25-hour increments (15-minute increments)
- Maximum 24 hours per day (across all projects)
- Cannot have duplicate entry for same member/project/date
- Comment maximum 500 characters

**Approval Workflow:**
- Entries transition DRAFT → SUBMITTED → APPROVED or back to DRAFT
- APPROVED status is permanently read-only
- Rejection requires mandatory reason (max 1000 characters)

**Fiscal Month Calculation:**
- Runs from 21st of previous month to 20th of current month
- Used for approval submission boundaries
