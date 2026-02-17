# Feature Specification: Password Reset Integration & E2E Tests

**Feature Branch**: `007-password-reset-tests`
**Created**: 2026-02-17
**Status**: Draft
**Input**: User description: "issue#5の対応"
**Related**: GitHub Issue #5

## Clarifications

### Session 2026-02-17

- Q: Email tests (User Story 4) and rate limit tests (FR-006) already exist in the codebase. Should scope include reviewing/enhancing existing tests or focus only on missing tests? → A: Focus only on missing tests (repository integration, E2E, controller validation gaps, CSRF)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Repository Integration Tests (Priority: P1)

As a developer, I want integration tests that verify the password reset token repository works correctly with a real database, so that I can be confident token storage, retrieval, invalidation, and cleanup operations behave as expected in production.

**Why this priority**: The repository is the foundational data layer for all password reset operations. Without verified persistence behavior, no other test layer can be trusted. These tests catch schema mismatches, query errors, and constraint violations that unit tests with mocks cannot detect.

**Independent Test**: Can be fully tested by running repository tests against a PostgreSQL container. Delivers confidence that all token CRUD operations and constraint enforcement work correctly with the real database schema.

**Acceptance Scenarios**:

1. **Given** a valid password reset token, **When** it is saved to the database, **Then** it can be retrieved by its token string
2. **Given** an unused, non-expired token exists, **When** querying for valid tokens, **Then** only that token is returned (expired and used tokens are excluded)
3. **Given** a token exists, **When** it is marked as used, **Then** subsequent queries for valid tokens do not return it
4. **Given** a user has multiple unused tokens, **When** their unused tokens are invalidated, **Then** all their unused tokens are marked invalid
5. **Given** expired tokens exist in the database, **When** the cleanup operation runs, **Then** all expired tokens are deleted
6. **Given** the database schema has a unique constraint on token strings, **When** a duplicate token is inserted, **Then** a constraint violation is raised
7. **Given** a user with a token exists, **When** the user is deleted, **Then** their associated tokens are also removed (cascade behavior)

---

### User Story 2 - Controller Validation & CSRF Tests (Priority: P2)

As a developer, I want integration tests for the password reset API endpoints that verify input validation and CSRF protection, so that I can ensure the API correctly rejects malformed or unauthorized requests.

**Why this priority**: The controller layer is the entry point for all external requests. Existing tests cover happy-path and error scenarios with mocks, but validation of malformed input and CSRF protection with real security context are missing.

**Independent Test**: Can be fully tested by sending HTTP requests to the password reset endpoints and asserting on response status codes and error messages. Delivers confidence that the API rejects invalid input and enforces CSRF protection.

**Note**: Rate limiting is already covered by the existing `PasswordResetRateLimitIT`. Controller happy-path and invalid-token error tests are already covered by `AuthControllerTest`. This story focuses only on the missing validation and CSRF gaps.

**Acceptance Scenarios**:

1. **Given** a password reset request with an empty email address, **When** submitted, **Then** the system returns a validation error
2. **Given** a password reset request with an invalid email format, **When** submitted, **Then** the system returns a validation error
3. **Given** a password reset confirmation with an empty token, **When** submitted, **Then** the system returns a validation error
4. **Given** a password reset confirmation with a password shorter than 8 characters, **When** submitted, **Then** the system returns a validation error
5. **Given** the API has CSRF protection enabled, **When** a request is made without a valid CSRF token, **Then** the request is rejected

---

### User Story 3 - End-to-End Password Reset Flow Test (Priority: P2)

As a developer, I want an end-to-end test that exercises the complete password reset flow from request to successful login with the new password, so that I can verify all components work together correctly.

**Why this priority**: The E2E test validates that the repository, service, controller, and email layers integrate correctly as a complete workflow. It catches integration issues that isolated layer tests cannot detect. Equal priority with controller tests as both serve critical but different validation purposes.

**Independent Test**: Can be fully tested by executing the complete password reset flow against a running application with a test database. Delivers confidence that the entire feature works end-to-end.

**Acceptance Scenarios**:

1. **Given** a registered user, **When** they request a password reset, receive a token, and submit a new password with that token, **Then** they can log in with the new password and cannot log in with the old password
2. **Given** a password reset token that has expired (beyond 24-hour validity), **When** a user attempts to reset their password with that token, **Then** the reset is rejected
3. **Given** a password reset token that has already been used, **When** a user attempts to use it again, **Then** the reset is rejected
4. **Given** a user has an active session, **When** they complete a password reset, **Then** their previous session is invalidated

---

### Edge Cases (Out of Scope)

The following edge cases are acknowledged but not targeted for testing in this feature. They are documented for future consideration:

- What happens when the database connection is lost mid-operation during token save?
- How does the system handle concurrent password reset requests for the same user?
- What happens when a user requests a password reset and then is deleted before using the token?

## Already Implemented (Out of Scope)

The following tests already exist and are excluded from this feature's scope:

- **Email sending tests**: `EmailServiceTest.kt` — GreenMail-based tests covering recipient, subject, body content, and reset link for password reset emails
- **Rate limiting integration test**: `PasswordResetRateLimitIT.kt` — Testcontainers-based test verifying rate limit enforcement on password reset endpoints
- **Service unit tests**: `PasswordResetServiceTest.kt` — Mock-based tests for requestReset and confirmReset business logic
- **Controller happy-path tests**: `AuthControllerTest.kt` — Tests for successful request, anti-enumeration, successful confirm, and invalid token error responses

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tests MUST use a real PostgreSQL database (via test containers) rather than an in-memory database to ensure production-equivalent behavior
- **FR-002**: Repository tests MUST cover all public methods of the password reset token repository: save, find by token, find valid by token, mark as used, invalidate unused tokens for user, and delete expired
- **FR-003**: Repository tests MUST verify database constraint enforcement (unique token constraint, foreign key cascade on user deletion)
- **FR-004**: Controller tests MUST validate all input rejection scenarios: empty email, invalid email format, empty token, and password below minimum length (8 characters)
- **FR-005**: Controller tests MUST verify CSRF protection is enforced on password reset endpoints
- **FR-006**: E2E tests MUST verify the complete password reset lifecycle: request, token generation, password update, and authentication with new credentials
- **FR-007**: E2E tests MUST verify token expiration enforcement (24-hour validity period)
- **FR-008**: E2E tests MUST verify single-use token enforcement (token cannot be reused after successful reset)
- **FR-009**: E2E tests MUST verify session invalidation after password reset
- **FR-010**: All tests MUST be executable in CI environments without manual intervention
- **FR-011**: Tests MUST follow existing test patterns established in the codebase (JdbcUserRepositoryTest for repository tests, IntegrationTestBase for E2E tests)
- **FR-012**: Test coverage for the password reset feature MUST be maintained at 85% or above

### Key Entities

- **PasswordResetToken**: Represents a time-limited, single-use token associated with a user account. Key attributes: token string (unique), user association, creation time, expiration time, used status
- **User**: The account holder requesting the password reset. Relationship: one user can have multiple tokens (only one valid at a time after invalidation of previous tokens)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All repository methods have at least one dedicated integration test exercising real database behavior
- **SC-002**: All input validation rules on password reset endpoints are covered by controller tests (empty email, invalid format, empty token, short password)
- **SC-003**: CSRF protection is verified on password reset endpoints
- **SC-004**: The complete password reset user journey (request → token extraction from DB → reset → login with new credentials) is verified in an E2E test
- **SC-005**: Token expiration, single-use enforcement, and session invalidation are each verified by at least one E2E test
- **SC-006**: All tests pass in CI without manual setup or external service dependencies
- **SC-007**: Password reset feature test coverage is 85% or above

## Assumptions

- Testcontainers is available and configured as a test dependency for PostgreSQL integration testing
- The existing test infrastructure patterns (JdbcUserRepositoryTest for repository, IntegrationTestBase for integration/E2E) provide a reliable template to follow
- The password reset backend implementation (PR #7) is complete and merged
- Token validity period is 24 hours as specified in the backend implementation
- Rate limiting threshold is 3 requests per second on authentication endpoints (already tested)
- Minimum password length is 8 characters as specified in the existing validation rules
- The existing AuthControllerTest excludes SecurityAutoConfiguration; CSRF testing will require a separate test configuration or a new integration test class with security enabled

## Dependencies

- Backend password reset implementation (PR #7) must be merged
- PostgreSQL database schema with password reset token table (Flyway migrations)
- Test dependencies: Testcontainers, Spring Boot Test, Spring Security Test
