# Member CSV Import - Phase 1: Backend Foundation

**Date:** 2026-02-28
**Issue:** #52
**Status:** Approved

## Overview

Implement backend foundation for member CSV batch registration: CSV parser with auto encoding detection and validation service.

## Architecture Decision

- **Batch processing** (not streaming): 1000-row cap allows `readAllBytes` + in-memory processing
- **Collect all errors**: Return all validation errors at once so users can fix everything in one pass

## Data Types

```java
// Parsed CSV row
record MemberCsvRow(int rowNumber, String email, String displayName)

// Validation error
record CsvValidationError(int rowNumber, String field, String message)

// Combined result
record MemberCsvResult(List<MemberCsvRow> validRows, List<CsvValidationError> errors)
```

## Components

### 1. MemberCsvProcessor

**Path:** `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvProcessor.java`

**Responsibility:** Parse CSV bytes into `List<MemberCsvRow>`

**Encoding Detection Algorithm:**
1. Check for UTF-8 BOM (`EF BB BF`) → UTF-8
2. Validate as UTF-8 multibyte sequence → UTF-8
3. Fallback → Windows-31J (Shift_JIS superset, Japanese Excel default)

**Processing Flow:**
1. Receive `byte[]` input
2. Detect encoding and strip BOM if present
3. Parse CSV with Apache Commons CSV (`CSVFormat.DEFAULT` with header/trim/skip settings)
4. CSV headers: `email`, `displayName`
5. Return `List<MemberCsvRow>` with 1-based row numbers

### 2. MemberCsvValidationService

**Path:** `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvValidationService.java`

**Responsibility:** Validate parsed rows, check DB duplicates

**Per-Row Validation:**
- `email`: required, regex format check, max 255 chars
- `displayName`: required, max 100 chars

**Cross-Row Validation:**
- Duplicate email within CSV → error on 2nd+ occurrence

**DB Duplicate Check (batch):**
- `UserRepository.findExistingEmails(emails)` → check users table
- `MemberRepository.findExistingEmailsInTenant(tenantId, emails)` → check members table
- Single SQL with `IN` clause for each check (efficient for ≤1000 emails)

### 3. Repository Extensions

**JdbcUserRepository:**
```java
Set<String> findExistingEmails(Collection<String> emails)
// SQL: SELECT LOWER(email) FROM users WHERE LOWER(email) IN (?, ?, ...)
```

**JdbcMemberRepository:**
```java
Set<String> findExistingEmailsInTenant(TenantId tenantId, Collection<String> emails)
// SQL: SELECT LOWER(email) FROM members WHERE tenant_id = ? AND LOWER(email) IN (?, ?, ...)
```

Both return empty set for empty input (no SQL executed).

## Test Plan

### MemberCsvProcessorTest (Unit)

| Test Case | Description |
|-----------|-------------|
| UTF-8 parse | Standard UTF-8 CSV with Japanese names |
| Shift_JIS parse | Windows-31J encoded CSV |
| BOM handling | UTF-8 BOM stripped before parse |
| Empty file | Returns empty list |
| Header only | Returns empty list (no data rows) |
| Invalid format | Missing columns → error handling |

### MemberCsvValidationServiceTest (Unit)

| Test Case | Description |
|-----------|-------------|
| Valid data | All rows pass validation |
| Email required | Empty email → error |
| Email format | Invalid email format → error |
| Email max length | >255 chars → error |
| DisplayName required | Empty name → error |
| DisplayName max length | >100 chars → error |
| CSV duplicate email | Same email on 2 rows → error on 2nd |
| DB user duplicate | Email exists in users table → error |
| DB member duplicate | Email exists in members table for tenant → error |

### Repository Integration Tests

- `findExistingEmails`: Insert test users, verify matching emails returned
- `findExistingEmailsInTenant`: Insert test members in tenant, verify tenant-scoped matching

## Technical Notes

- Apache Commons CSV 1.11.0 already in build.gradle.kts
- Encoding detection uses `CharsetDecoder` with `CodingErrorAction.REPORT` for UTF-8 validation
- `IN` clause with up to 1000 parameters is well within PostgreSQL limits
- Follows existing patterns: `StreamingCsvProcessor` for CSV format, `JdbcUserRepository` for JDBC queries
