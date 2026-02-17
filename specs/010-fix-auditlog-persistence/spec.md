# Feature Specification: AuditLog Persistence Bug Fix

**Feature Branch**: `010-fix-auditlog-persistence`
**Created**: 2026-02-17
**Status**: Draft
**Input**: User description: "GitHub Issue #15 — AuditLog エンティティの Spring Data JDBC 永続化バグ修正"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Audit Events Are Reliably Recorded on Login (Priority: P1)

When a user logs into the system, the audit log entry for that login event must be saved successfully. Currently, the audit log entry fails to persist due to data type conversion issues, which means security-relevant events are silently lost.

**Why this priority**: Audit logging is a fundamental security requirement. Without reliable persistence, the system has no record of who accessed it, when, or from where. This is the core bug that blocks all audit functionality.

**Independent Test**: Can be fully tested by performing a login and verifying the audit log entry exists in the data store with correct structured details and IP address information.

**Acceptance Scenarios**:

1. **Given** a user submits valid login credentials, **When** the login succeeds, **Then** an audit log entry is created containing the event type, timestamp, user identifier, IP address, and structured event details.
2. **Given** a user submits invalid login credentials, **When** the login fails, **Then** an audit log entry is created recording the failed attempt with the same metadata (event type, timestamp, IP address, structured details).
3. **Given** an audit event contains structured detail data (key-value pairs), **When** the event is saved, **Then** the structured data is stored in a queryable format and can be retrieved intact.
4. **Given** an audit event contains an IP address, **When** the event is saved, **Then** the IP address is stored in a network-address-compatible format.

---

### User Story 2 - Audit Logging Does Not Disrupt Primary Operations (Priority: P1)

Audit logging must be a non-blocking, fault-tolerant subsystem. If an error occurs during audit log persistence, the primary operation (e.g., user login) must still complete successfully. Currently, a failure in audit log saving causes the entire login transaction to roll back, preventing users from logging in.

**Why this priority**: This is equally critical as Story 1 — users being unable to log in due to an audit subsystem failure is a production-blocking defect. Audit logging is supplementary and must never interfere with core business operations.

**Independent Test**: Can be tested by simulating an audit log persistence failure and verifying that the primary login operation still completes successfully.

**Acceptance Scenarios**:

1. **Given** a user submits valid login credentials, **When** the audit log persistence encounters an error, **Then** the user login still completes successfully and the user receives a valid session.
2. **Given** an audit event is being saved, **When** an error occurs, **Then** the error is logged internally but does not propagate to the calling operation.
3. **Given** multiple audit events are generated during a single request lifecycle, **When** one event fails to persist, **Then** other events and the primary operation are unaffected.

---

### User Story 3 - New Audit Log Entries Are Always Created (Not Updated) (Priority: P2)

Each audit event must result in a new record. The system must never attempt to update an existing audit log entry when creating a new one. Currently, the system incorrectly treats new audit entries as existing records and attempts an update instead of an insert, which fails silently or causes errors.

**Why this priority**: This is a data integrity issue. Audit logs are append-only by nature — they must never be overwritten. Incorrect insert/update detection breaks the fundamental contract of the audit trail.

**Independent Test**: Can be tested by creating multiple audit events and verifying each one results in a distinct, new record with its own unique identifier.

**Acceptance Scenarios**:

1. **Given** a new audit event occurs, **When** the system saves it, **Then** a new record is always created (never updating an existing one).
2. **Given** two audit events occur for the same user in quick succession, **When** both are saved, **Then** two distinct records exist, each with a unique identifier and separate timestamps.
3. **Given** an audit log entry already exists, **When** a new event of the same type occurs, **Then** the existing entry is not modified and a new entry is appended.

---

### Edge Cases

- What happens when the audit log details contain extremely large structured data (e.g., thousands of key-value pairs)?
- How does the system behave when the IP address is in IPv6 format vs. IPv4?
- What happens when the audit log table is temporarily unavailable (e.g., connection pool exhausted)?
- How does the system handle concurrent audit events from the same user (e.g., rapid repeated login attempts)?
- What happens when the audit event details field is null or empty?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist audit log entries with structured event details in a queryable data format that preserves key-value structure.
- **FR-002**: System MUST persist audit log IP addresses in a network-address-compatible storage format that supports both IPv4 and IPv6.
- **FR-003**: System MUST always create new audit log records (insert), never update existing records, regardless of how the record's identifier is generated.
- **FR-004**: System MUST isolate audit log persistence failures from primary business operations — a failure to save an audit event must not cause the calling operation to fail or roll back.
- **FR-005**: System MUST log audit events for login-related authentication actions (successful login, failed login, account locked). Audit logging for other authentication actions (logout, password reset) is out of scope for this bug fix and will be addressed in a separate issue.
- **FR-006**: Each audit log entry MUST contain at minimum: unique identifier, event type, timestamp, user identifier (if known), IP address, and structured event details.

### Key Entities

- **AuditLog**: An immutable, append-only record of a security-relevant event. Key attributes: unique identifier, event type, associated user, timestamp, originating IP address, and structured detail data. Audit logs are write-once and must never be modified after creation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of login attempts (successful and failed) result in a corresponding audit log entry being persisted.
- **SC-002**: Login operations complete successfully even when audit log persistence encounters errors — zero login failures caused by audit subsystem issues.
- **SC-003**: All audit log entries contain correctly formatted structured details and IP address data that can be queried and retrieved without data loss.
- **SC-004**: End-to-end authentication tests (including audit log verification) pass without requiring workaround approaches for database validation.

## Assumptions

- Audit log entries are immutable and append-only — no update or delete operations are required.
- The audit subsystem is secondary to core business operations and must be fault-tolerant.
- IPv4 and IPv6 addresses are both valid inputs for the IP address field.
- Structured event details follow a key-value format with string keys and string values.
- The existing audit log storage schema (structured data and network address column types) is correct and should not be changed.
