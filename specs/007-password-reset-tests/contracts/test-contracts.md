# Test Contracts: Password Reset Integration & E2E Tests

**Branch**: `007-password-reset-tests` | **Date**: 2026-02-17

> This feature adds tests only — no new API contracts. This document defines the **test contracts**: the expected behaviors each test class must verify.

## TC-1: PasswordResetTokenRepositoryTest

**Test class**: `backend/src/test/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepositoryTest.kt`
**Pattern**: `@SpringBootTest` + `@Testcontainers` + `@Transactional` (follows JdbcUserRepositoryTest)

| Test ID  | Method Under Test                   | Scenario                                | Expected Result                     |
|----------|-------------------------------------|-----------------------------------------|-------------------------------------|
| TC-1.1   | save + findByToken                  | Save token, retrieve by token string    | Token found with matching fields    |
| TC-1.2   | findValidByToken                    | Unused, non-expired token exists        | Token returned                      |
| TC-1.3   | findValidByToken                    | Token is used (used_at set)             | Empty result                        |
| TC-1.4   | findValidByToken                    | Token is expired (expires_at in past)   | Empty result                        |
| TC-1.5   | markAsUsed                          | Mark token as used                      | used_at is set, findValid returns empty |
| TC-1.6   | invalidateUnusedTokensForUser       | User has 2 unused tokens                | Both tokens have used_at set        |
| TC-1.7   | invalidateUnusedTokensForUser       | User has 1 used + 1 unused token        | Only unused token gets used_at set  |
| TC-1.8   | deleteExpired                       | Mix of expired and valid tokens         | Only expired tokens deleted         |
| TC-1.9   | save (constraint)                   | Insert duplicate token string           | Constraint violation exception      |
| TC-1.10  | CASCADE                             | Delete user with tokens                 | User's tokens also deleted          |

## TC-2: AuthControllerTest (Validation Extensions)

**Test class**: `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt` (existing, add methods)
**Pattern**: `@WebMvcTest` with MockK (existing setup)

| Test ID  | Endpoint               | Scenario                     | Expected Status | Expected Error     |
|----------|------------------------|------------------------------|-----------------|--------------------|
| TC-2.1   | POST .../request       | Empty email ("")             | 400             | Validation error   |
| TC-2.2   | POST .../request       | Invalid email ("not-email")  | 400             | Validation error   |
| TC-2.3   | POST .../confirm       | Empty token ("")             | 400             | Validation error   |
| TC-2.4   | POST .../confirm       | Password < 8 chars ("short") | 400             | Validation error   |

## TC-3: PasswordResetCsrfTest

**Test class**: `backend/src/test/kotlin/com/worklog/api/PasswordResetCsrfTest.kt` (new)
**Pattern**: `@WebMvcTest` with custom SecurityFilterChain enabling CSRF

| Test ID  | Endpoint               | Scenario                          | Expected Status |
|----------|------------------------|-----------------------------------|-----------------|
| TC-3.1   | POST .../request       | Request without CSRF token        | 403             |
| TC-3.2   | POST .../request       | Request with valid CSRF token     | 200             |
| TC-3.3   | POST .../confirm       | Request without CSRF token        | 403             |
| TC-3.4   | POST .../confirm       | Request with valid CSRF token     | 200             |

## TC-4: PasswordResetE2ETest

**Test class**: `backend/src/test/kotlin/com/worklog/e2e/PasswordResetE2ETest.kt` (new)
**Pattern**: extends `IntegrationTestBase`, uses WebTestClient + JdbcTemplate

| Test ID  | Scenario                                    | Steps                                                           | Expected Result                      |
|----------|---------------------------------------------|-----------------------------------------------------------------|--------------------------------------|
| TC-4.1   | Complete password reset flow                | Create user → request reset → extract token from DB → confirm → login with new password | Login succeeds with new, fails with old |
| TC-4.2   | Expired token rejection                     | Create user → request reset → set expires_at to past → confirm  | 404 (invalid/expired token)          |
| TC-4.3   | Used token rejection                        | Create user → request reset → confirm once → confirm again      | 404 on second attempt                |
| TC-4.4   | Session invalidation after reset            | Create user → login → request reset → confirm → verify old session invalid | Old session rejected         |
