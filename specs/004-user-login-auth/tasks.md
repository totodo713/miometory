# Implementation Tasks: ユーザーログイン認証・認可システム

**Feature**: 004-user-login-auth  
**Generated**: 2026-02-03  
**Updated**: 2026-02-03 (Added test tasks per Constitution Principle II)  
**Input**: spec.md, plan.md, data-model.md, research.md, contracts/

---

## Summary

- **Total Tasks**: 159 (110 original implementation + 43 test tasks + 4 test setup + 2 new requirements)
- **Implementation Tasks**: 116 (includes FR-017 restriction implementation and PersistentLoginRepository)
- **Test Tasks**: 43 (Constitution Principle II compliance)
  - Unit Tests: 37 (Backend: 29, Frontend: 8)
  - Integration/E2E Tests: 3
  - Performance Tests: 3 (Gatling load test, metrics, accessibility audit)
- **MVP Scope**: User Story 1 - 85 tasks (Phase 1: 10 + Phase 2: 11 + Phase 2A: 4 + Phase 3: 60)
- **Parallel Opportunities**: 92 tasks marked [P] (60% can run in parallel)
- **User Stories**: 4 (US1-P1, US2-P2, US3-P3, US4-P1)

---

## Phase 1: Setup & Infrastructure (10 tasks)

**Purpose**: Project initialization and database foundation

- [X] T001 Create database migration file `backend/src/main/resources/db/migration/V003__user_auth.sql` with all 8 tables (users, roles, permissions, role_permissions, user_sessions, persistent_logins, password_reset_tokens, audit_logs)
- [X] T002 Add seed data to `backend/src/main/resources/db/migration/data-dev.sql` for roles (ADMIN, USER, MODERATOR) and permissions
- [X] T003 [P] Configure Spring Security dependencies in `backend/build.gradle.kts` (spring-boot-starter-security, spring-boot-starter-mail)
- [X] T004 [P] Configure SMTP settings in `backend/src/main/resources/application.yml` for email service
- [X] T005 [P] Create SecurityConfig in `backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt` with basic CSRF, session management, and remember-me settings
- [X] T006 [P] Create CorsConfig in `backend/src/main/kotlin/com/worklog/infrastructure/config/CorsConfig.kt` with frontend origin whitelist
- [X] T007 [P] Create MethodSecurityConfig in `backend/src/main/kotlin/com/worklog/infrastructure/config/MethodSecurityConfig.kt` enabling @PreAuthorize
- [X] T008 [P] Create SchedulerConfig in `backend/src/main/kotlin/com/worklog/infrastructure/config/SchedulerConfig.kt` for session/token cleanup jobs
- [X] T009 [P] Configure frontend API base URL in `frontend/.env.local` and `frontend/lib/config.ts`
- [X] T010 Run database migration to create tables: `./gradlew bootRun` (backend startup applies Flyway migrations)

**Checkpoint**: Database tables created, Spring Security configured, ready for entity implementation

---

## Phase 2: Foundational - Core Domain Models (11 tasks)

**Purpose**: Blocking prerequisites - domain entities that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T011 [P] Create UserId value object in `backend/src/main/java/com/worklog/domain/user/UserId.java`
- [X] T012 [P] Create User entity in `backend/src/main/java/com/worklog/domain/user/User.java` (id, email, name, hashed_password, role_id, account_status, created_at, updated_at, last_login_at, email_verified_at, failed_login_attempts, locked_until)
- [X] T013 [P] Create Role entity in `backend/src/main/java/com/worklog/domain/role/Role.java` (id, name, description)
- [X] T014 [P] Create Permission entity in `backend/src/main/java/com/worklog/domain/permission/Permission.java` (id, name, description)
- [X] T015 [P] Create UserSession entity in `backend/src/main/java/com/worklog/domain/session/UserSession.java` (id, user_id, session_id, created_at, last_accessed_at, expires_at, ip_address, user_agent)
- [X] T016 [P] Create PasswordResetToken entity in `backend/src/main/java/com/worklog/domain/password/PasswordResetToken.java` (id, user_id, token, created_at, expires_at, used_at)
- [X] T017 [P] Create AuditLog entity in `backend/src/main/java/com/worklog/domain/audit/AuditLog.java` (id, event_type, user_id, details, ip_address, created_at)
- [X] T018 [P] Create UserRepository interface in `backend/src/main/java/com/worklog/infrastructure/persistence/UserRepository.java`
- [X] T019 [P] Create RoleRepository interface in `backend/src/main/java/com/worklog/infrastructure/persistence/RoleRepository.java`
- [X] T020 [P] Create PermissionRepository interface in `backend/src/main/java/com/worklog/infrastructure/persistence/PermissionRepository.java`
- [X] T021 [P] Create AuditLogRepository interface in `backend/src/main/java/com/worklog/infrastructure/persistence/AuditLogRepository.java`

**Checkpoint**: Foundation ready - all domain entities and repositories created

---

## Phase 2A: Test Infrastructure Setup (4 tasks)

**Purpose**: Configure testing frameworks per Constitution Principle II

- [X] T022 [P] Configure JUnit 5 and MockK test infrastructure in `backend/build.gradle.kts` with testImplementation dependencies
- [X] T023 [P] Configure Vitest for frontend in `frontend/vitest.config.ts` with React Testing Library
- [X] T024 [P] Create test database configuration in `backend/src/test/resources/application-test.yml` with H2 in-memory database
- [X] T025 [P] Create test data fixtures in `backend/src/test/kotlin/com/worklog/fixtures/UserFixtures.kt` and `RoleFixtures.kt`

**Checkpoint**: Test infrastructure ready - JUnit 5, Vitest, test database configured

---

## Phase 3: User Story 1 - Account Creation & Login (P1) 🎯 MVP (60 tasks)

**Goal**: Users can sign up, verify email, log in, and log out with session management

**Independent Test**: Create account → Receive verification email → Verify email → Login → Access dashboard → Logout

### Unit Tests (US1) - Write tests FIRST per Constitution Principle II

**Backend Tests (17 tasks)**

- [X] T026 [P] [US1] Write User entity unit tests in `backend/src/test/kotlin/com/worklog/domain/user/UserTest.kt` - verify business logic (37 tests covering creation, validation, login tracking, locking, email verification, password management, soft delete, eligibility checks)
- [ ] T027 [P] [US1] Write AuthService.signup() unit test in `backend/src/test/kotlin/com/worklog/application/auth/AuthServiceTest.kt` - verify user creation and email sent
- [ ] T028 [P] [US1] Write AuthService.login() success case test - verify session created and last_login_at updated
- [ ] T029 [P] [US1] Write AuthService.login() failed attempts test - verify account locks after 5 failures for 15 minutes
- [ ] T030 [P] [US1] Write AuthService.verifyEmail() test - verify email_verified_at set and account_status updated
- [ ] T031 [P] [US1] Write AuthController.signup endpoint test in `backend/src/test/kotlin/com/worklog/api/AuthControllerTest.kt` - verify 201 response
- [ ] T032 [P] [US1] Write AuthController.login endpoint test - verify session cookie and CSRF token returned
- [ ] T033 [P] [US1] Write AuthController.logout endpoint test - verify session invalidated
- [ ] T034 [P] [US1] Write AuthController.verifyEmail endpoint test - verify token validation
- [ ] T035 [P] [US1] Write EmailService test in `backend/src/test/kotlin/com/worklog/infrastructure/email/EmailServiceTest.kt` using GreenMail mock SMTP server
- [ ] T036 [P] [US1] Write UserRepository integration test in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/UserRepositoryTest.kt` - verify CRUD operations
- [ ] T037 [P] [US1] Write password validation test - verify BCrypt hashing and strength requirements (8+ chars, mixed case, digit)
- [ ] T038 [P] [US1] Write email uniqueness validation test - verify duplicate email rejected with 409 conflict

**Frontend Tests (5 tasks)**

- [ ] T039 [P] [US1] Write UnverifiedBanner component test in `frontend/app/components/auth/__tests__/UnverifiedBanner.test.tsx` - verify visibility logic
- [ ] T040 [P] [US1] Write useAuth hook test in `frontend/app/hooks/__tests__/useAuth.test.ts` - verify login/logout state changes
- [ ] T041 [P] [US1] Write signup page test in `frontend/app/(auth)/signup/__tests__/page.test.tsx` - verify form validation and submission
- [ ] T042 [P] [US1] Write login page test in `frontend/app/(auth)/login/__tests__/page.test.tsx` - verify remember-me checkbox and error handling
- [ ] T043 [P] [US1] Write email verification page test in `frontend/app/(auth)/verify-email/__tests__/page.test.tsx` - verify token handling

**Test Execution**: All tests MUST FAIL initially, then PASS after implementation

### Backend API Layer (US1)

- [ ] T044 [P] [US1] Create SignupCommand in `backend/src/main/kotlin/com/worklog/application/auth/SignupCommand.kt` (email, password, name)
- [ ] T045 [P] [US1] Create LoginCommand in `backend/src/main/kotlin/com/worklog/application/auth/LoginCommand.kt` (email, password, rememberMe)
- [ ] T046 [P] [US1] Create VerifyEmailCommand in `backend/src/main/kotlin/com/worklog/application/auth/VerifyEmailCommand.kt` (token)
- [ ] T047 [US1] Create AuthService in `backend/src/main/kotlin/com/worklog/application/auth/AuthService.kt` implementing signup, login, logout, verifyEmail logic
- [ ] T048 [US1] Implement password hashing using BCryptPasswordEncoder (strength: 10) in AuthService
- [ ] T049 [US1] Implement email uniqueness validation in AuthService.signup
- [ ] T050 [US1] Implement failed login tracking (max 5 attempts, 15-minute lock) in AuthService.login
- [ ] T051 [US1] Implement session creation and tracking in UserSessionRepository in `backend/src/main/kotlin/com/worklog/infrastructure/persistence/UserSessionRepository.kt`
- [ ] T052 [US1] Create EmailService in `backend/src/main/kotlin/com/worklog/infrastructure/email/EmailService.kt` with sendVerificationEmail method
- [ ] T053 [US1] Add @Async and @Retryable to EmailService for async email sending with 3 retry attempts
- [ ] T054 [US1] Create AuthController in `backend/src/main/kotlin/com/worklog/api/AuthController.kt` with POST /auth/signup endpoint
- [ ] T055 [US1] Add POST /auth/login endpoint to AuthController with remember-me cookie handling
- [ ] T056 [US1] Add POST /auth/logout endpoint to AuthController with session invalidation
- [ ] T057 [US1] Add POST /auth/verify-email endpoint to AuthController with token validation
- [ ] T058 [US1] Add GET /auth/csrf endpoint to AuthController returning CSRF token
- [ ] T059 [US1] Implement audit logging for SIGNUP, LOGIN, LOGOUT, EMAIL_VERIFICATION events in AuthService
- [ ] T060 [US1] Implement unverified user restrictions in CustomPermissionEvaluator: block work_log.create, work_log.approve, admin.access for account_status='unverified' (FR-017)

### Frontend Components (US1)

- [ ] T061 [P] [US1] Create AuthContext in `frontend/app/lib/context/AuthContext.tsx` with user state, login, logout, signup methods
- [ ] T062 [P] [US1] Create useAuth hook in `frontend/app/hooks/useAuth.ts` wrapping AuthContext
- [ ] T063 [P] [US1] Create API client functions in `frontend/app/lib/api/auth.ts` (signup, login, logout, verifyEmail)
- [ ] T064 [US1] Create signup page in `frontend/app/(auth)/signup/page.tsx` with form (email, name, password)
- [ ] T065 [US1] Create login page in `frontend/app/(auth)/login/page.tsx` with form (email, password, rememberMe checkbox)
- [ ] T066 [US1] Create email verification page in `frontend/app/(auth)/verify-email/page.tsx` accepting token query param
- [ ] T067 [US1] Create UnverifiedBanner component in `frontend/app/components/auth/UnverifiedBanner.tsx` showing warning for unverified users (FR-017)
- [ ] T068 [US1] Add UnverifiedBanner to main layout in `frontend/app/layout.tsx` conditionally rendered for unverified users
- [ ] T069 [US1] Implement client-side password strength validation (8+ chars, uppercase, lowercase, digit) in signup form
- [ ] T070 [US1] Add session timeout warning modal (25-minute warning for 30-minute timeout) in `frontend/app/components/auth/SessionTimeoutModal.tsx`

**Checkpoint**: User Story 1 complete - users can sign up, verify email, login, logout

---

## Phase 4: User Story 4 - Session Management (P1) (15 tasks)

**Goal**: Sessions timeout after 30 minutes, remember-me for 30 days, multi-device support, session cleanup

**Independent Test**: Login → Wait 30 minutes → Session expires → Login with remember-me → Still logged in after 30 minutes

### Unit Tests (US4) - 5 tasks

- [ ] T070 [P] [US4] Write SessionCleanupScheduler test in `backend/src/test/kotlin/com/worklog/infrastructure/scheduler/SessionCleanupSchedulerTest.kt` - verify deleteExpiredSessions called daily
- [ ] T071 [P] [US4] Write UserSessionRepository.deleteExpiredSessions test - verify sessions older than 30 minutes deleted
- [ ] T072 [P] [US4] Write PersistentLoginRepository.deleteStalePersistentLogins test - verify tokens older than 30 days deleted
- [ ] T073 [P] [US4] Write SessionListener test in `backend/src/test/kotlin/com/worklog/infrastructure/session/SessionListenerTest.kt` - verify last_accessed_at updated
- [ ] T074 [P] [US4] Write multi-device session test - verify multiple concurrent sessions allowed per user

### Backend Implementation (US4)

- [ ] T075 [P] [US4] Configure session timeout in `backend/src/main/resources/application.yml` (server.servlet.session.timeout=30m)
- [ ] T076 [P] [US4] Configure remember-me in SecurityConfig with 30-day token validity (tokenValiditySeconds=2592000)
- [ ] T077 [US4] Create SessionCleanupScheduler in `backend/src/main/kotlin/com/worklog/infrastructure/scheduler/SessionCleanupScheduler.kt` with @Scheduled cron job (daily at 3 AM)
- [ ] T078 [US4] Implement deleteExpiredSessions method in UserSessionRepository
- [ ] T079 [US4] Create PersistentLoginRepository in `backend/src/main/kotlin/com/worklog/infrastructure/persistence/PersistentLoginRepository.kt` extending JdbcTokenRepositoryImpl
- [ ] T080 [US4] Implement deleteStalePersistentLogins method in PersistentLoginRepository (tokens older than 30 days)
- [ ] T081 [US4] Add session update logic in AuthService to refresh last_accessed_at on each request via HttpSessionListener
- [ ] T082 [US4] Create HttpSessionListener in `backend/src/main/kotlin/com/worklog/infrastructure/session/SessionListener.kt` tracking session creation/destruction
- [ ] T083 [US4] Implement multi-device session support allowing unlimited concurrent sessions per user

### Frontend Implementation (US4)

- [ ] T084 [US4] Add session expiration check in AuthContext polling sessionExpiresAt from API
- [ ] T085 [US4] Show SessionTimeoutModal 5 minutes before expiration with "Extend Session" button

**Checkpoint**: User Story 4 complete - session timeout, remember-me, multi-device support working

---

## Phase 5: User Story 2 - Password Reset (P2) (18 tasks)

**Goal**: Users can reset forgotten passwords via email with 24-hour token expiration

**Independent Test**: Request password reset → Receive email → Click reset link → Set new password → Login with new password

### Unit Tests (US2) - 6 tasks

- [X] T086 [P] [US2] Write PasswordResetService.requestReset test in `backend/src/test/kotlin/com/worklog/application/password/PasswordResetServiceTest.kt` - verify token generated and email sent
- [X] T087 [P] [US2] Write PasswordResetService.confirmReset test - verify password updated and old sessions invalidated
- [X] T088 [P] [US2] Write token expiration test - verify 24-hour tokens rejected after expiry
- [X] T089 [P] [US2] Write token invalidation test - verify old tokens marked as used when new reset requested
- [X] T090 [P] [US2] Write password reset request page test in `frontend/app/(auth)/password-reset/request/__tests__/page.test.tsx` - verify anti-enumeration (always 200 response)
- [X] T091 [P] [US2] Write password reset confirm page test in `frontend/app/(auth)/password-reset/confirm/__tests__/page.test.tsx` - verify token validation errors

### Backend Implementation (US2)

- [X] T092 [P] [US2] Create PasswordResetRequestCommand in `backend/src/main/kotlin/com/worklog/application/password/PasswordResetRequestCommand.kt` (email)
- [X] T093 [P] [US2] Create PasswordResetConfirmCommand in `backend/src/main/kotlin/com/worklog/application/password/PasswordResetConfirmCommand.kt` (token, newPassword)
- [X] T094 [US2] Create PasswordResetService in `backend/src/main/kotlin/com/worklog/application/password/PasswordResetService.kt` with requestReset and confirmReset methods
- [X] T095 [US2] Implement token generation (secure random 32-byte token) in PasswordResetService.requestReset
- [X] T096 [US2] Implement token expiration (24 hours) in PasswordResetService.requestReset
- [X] T097 [US2] Implement invalidation of previous tokens (set used_at) in PasswordResetService.requestReset
- [X] T098 [US2] Add sendPasswordResetEmail method to EmailService with reset link containing token
- [X] T099 [US2] Implement token validation (unused, not expired) in PasswordResetService.confirmReset
- [X] T100 [US2] Implement password update and session invalidation in PasswordResetService.confirmReset (delete all user sessions and persistent logins)
- [X] T101 [US2] Create PasswordResetTokenRepository in `backend/src/main/kotlin/com/worklog/infrastructure/persistence/PasswordResetTokenRepository.kt`
- [X] T102 [US2] Add POST /auth/password-reset/request endpoint to AuthController (always returns 200 to prevent email enumeration)
- [X] T103 [US2] Add POST /auth/password-reset/confirm endpoint to AuthController with token validation

### Frontend Implementation (US2)

- [X] T104 [P] [US2] Create password reset request page in `frontend/app/(auth)/password-reset/request/page.tsx` with email form
- [X] T105 [P] [US2] Create password reset confirm page in `frontend/app/(auth)/password-reset/confirm/page.tsx` accepting token query param and new password form
- [X] T106 [US2] Add API client functions in `frontend/app/lib/api/auth.ts` (requestPasswordReset, confirmPasswordReset)

**Checkpoint**: User Story 2 complete - password reset flow functional

---

## Phase 6: User Story 3 - Role-Based Access Control (P3) (28 tasks)

**Goal**: Admin users can manage other users and assign roles; regular users have restricted access

**Independent Test**: Login as admin → View user list → Edit user role → Verify permissions updated → Login as regular user → Cannot access admin endpoints

### Unit Tests (US3) - 8 tasks

- [ ] T107 [P] [US3] Write CustomPermissionEvaluator test in `backend/src/test/kotlin/com/worklog/infrastructure/security/CustomPermissionEvaluatorTest.kt` - verify permission checks
- [ ] T108 [P] [US3] Write UserDetailsServiceImpl test in `backend/src/test/kotlin/com/worklog/infrastructure/security/UserDetailsServiceImplTest.kt` - verify permissions loaded correctly
- [ ] T109 [P] [US3] Write UserService.updateUser test in `backend/src/test/kotlin/com/worklog/application/user/UserServiceTest.kt` - verify role assignment
- [ ] T110 [P] [US3] Write UserController authorization test in `backend/src/test/kotlin/com/worklog/api/UserControllerTest.kt` - verify @PreAuthorize annotations work
- [ ] T111 [P] [US3] Write permission caching test - verify performance improvement from cached permissions
- [ ] T112 [P] [US3] Write usePermission hook test in `frontend/app/hooks/__tests__/usePermission.test.ts` - verify permission checks in UI
- [ ] T113 [P] [US3] Write ProtectedRoute component test in `frontend/app/components/auth/__tests__/ProtectedRoute.test.tsx` - verify unauthorized redirect
- [ ] T114 [P] [US3] Write user list page test in `frontend/app/(admin)/users/__tests__/page.test.tsx` - verify admin-only access

### Backend Permission System (US3)

- [ ] T115 [P] [US3] Create CustomPermissionEvaluator in `backend/src/main/kotlin/com/worklog/infrastructure/security/CustomPermissionEvaluator.kt` implementing PermissionEvaluator interface
- [ ] T116 [P] [US3] Implement hasPermission method in CustomPermissionEvaluator checking user's role permissions from database
- [ ] T117 [US3] Create UserDetailsService implementation in `backend/src/main/kotlin/com/worklog/infrastructure/security/UserDetailsServiceImpl.kt` loading user with permissions
- [ ] T118 [US3] Add permissions to UserDetails via GrantedAuthority using format "PERMISSION_<name>" (e.g., PERMISSION_user.create)
- [ ] T119 [US3] Create RolePermissionRepository in `backend/src/main/kotlin/com/worklog/infrastructure/persistence/RolePermissionRepository.kt` with findPermissionsByRoleId query
- [ ] T120 [US3] Implement permission caching in UserDetailsServiceImpl to avoid DB lookup per request

### Backend User Management API (US3)

- [ ] T121 [P] [US3] Create GetUserQuery in `backend/src/main/kotlin/com/worklog/application/user/GetUserQuery.kt` (userId)
- [ ] T122 [P] [US3] Create ListUsersQuery in `backend/src/main/kotlin/com/worklog/application/user/ListUsersQuery.kt` (page, size, sort, accountStatus, roleId)
- [ ] T123 [P] [US3] Create UpdateUserCommand in `backend/src/main/kotlin/com/worklog/application/user/UpdateUserCommand.kt` (userId, name, email, roleId, accountStatus)
- [ ] T124 [P] [US3] Create DeleteUserCommand in `backend/src/main/kotlin/com/worklog/application/user/DeleteUserCommand.kt` (userId)
- [ ] T125 [US3] Create UserService in `backend/src/main/kotlin/com/worklog/application/user/UserService.kt` implementing getUser, listUsers, updateUser, deleteUser methods
- [ ] T126 [US3] Create UserController in `backend/src/main/kotlin/com/worklog/api/UserController.kt` with GET /users endpoint (@PreAuthorize("hasPermission(null, 'user.view')"))
- [ ] T127 [US3] Add GET /users/{id} endpoint to UserController with user.view permission check
- [ ] T128 [US3] Add PUT /users/{id} endpoint to UserController with user.edit permission check
- [ ] T129 [US3] Add DELETE /users/{id} endpoint to UserController with user.delete permission check (soft delete)
- [ ] T130 [US3] Add PUT /users/{id}/role endpoint to UserController with user.assign_role permission check
- [ ] T131 [US3] Add GET /roles endpoint to UserController returning all roles (user.view permission)
- [ ] T132 [US3] Add GET /permissions endpoint to UserController returning all permissions (user.view permission)

### Frontend User Management (US3)

- [ ] T133 [P] [US3] Create usePermission hook in `frontend/app/hooks/usePermission.ts` checking user permissions from AuthContext
- [ ] T134 [P] [US3] Create API client functions in `frontend/app/lib/api/users.ts` (listUsers, getUser, updateUser, deleteUser, assignRole)
- [ ] T135 [US3] Create admin user list page in `frontend/app/(admin)/users/page.tsx` with table, filtering, and pagination
- [ ] T136 [US3] Create user edit page in `frontend/app/(admin)/users/[id]/edit/page.tsx` with form for name, email, role, account status
- [ ] T137 [US3] Add permission checks in components using usePermission hook (hide/disable admin features for non-admin users)
- [ ] T138 [US3] Create ProtectedRoute component in `frontend/app/components/auth/ProtectedRoute.tsx` wrapping admin pages requiring specific permissions

**Checkpoint**: User Story 3 complete - role-based access control fully implemented

---

## Phase 7: Profile Management (Cross-Story) (6 tasks)

**Purpose**: User profile CRUD operations available to all authenticated users

- [ ] T139 [P] Create GetCurrentUserQuery in `backend/src/main/kotlin/com/worklog/application/user/GetCurrentUserQuery.kt`
- [ ] T140 [P] Create UpdateProfileCommand in `backend/src/main/kotlin/com/worklog/application/user/UpdateProfileCommand.kt` (name, email)
- [ ] T141 [P] Create ChangePasswordCommand in `backend/src/main/kotlin/com/worklog/application/user/ChangePasswordCommand.kt` (currentPassword, newPassword)
- [ ] T142 Add GET /users/me endpoint to UserController returning current user profile
- [ ] T143 Add PUT /users/me endpoint to UserController updating profile (re-verify email if changed)
- [ ] T144 Add PUT /users/me/password endpoint to UserController with password change logic (invalidate all other sessions)

---

## Phase 8: Polish & Cross-Cutting Concerns (15 tasks)

**Purpose**: Production readiness and non-functional requirements

### Integration & E2E Tests (3 tasks)

- [ ] T145 [P] Create E2E authentication flow test in `frontend/e2e/auth.spec.ts` using Playwright - signup → verify → login → logout
- [ ] T146 [P] Create E2E admin flow test in `frontend/e2e/admin.spec.ts` - login as admin → manage users → assign roles
- [ ] T147 [P] Create integration test in `backend/src/test/kotlin/com/worklog/integration/AuthIntegrationTest.kt` testing full API flow with real database

### Performance & Monitoring (4 tasks)

- [ ] T148 [P] Create Gatling performance test suite in `backend/src/test/gatling/simulations/AuthLoadTest.scala` - simulate 100 concurrent users
- [ ] T149 [P] Configure Prometheus metrics in `backend/src/main/resources/application.yml` with actuator endpoints
- [ ] T150 [P] Create Grafana dashboard config in `infra/monitoring/grafana/dashboards/auth-metrics.json` for auth metrics
- [ ] T151 Run WCAG 2.1 AA accessibility audit using axe-core on all frontend auth pages

### Production Readiness (8 tasks)

- [ ] T152 [P] Add comprehensive error handling to all controllers with consistent ErrorResponse format
- [ ] T153 [P] Add request/response logging interceptor in `backend/src/main/kotlin/com/worklog/infrastructure/logging/RequestLoggingInterceptor.kt`
- [ ] T154 [P] Configure HTTPS/TLS for production in `infra/docker/docker-compose.prod.yml`
- [ ] T155 [P] Add audit log cleanup scheduler (delete logs older than 90 days per FR-020) in `backend/src/main/kotlin/com/worklog/infrastructure/scheduler/AuditLogCleanupScheduler.kt`
- [ ] T156 Add input validation annotations (@Valid, @Email, @Size) to all command objects
- [ ] T157 Add rate limiting to authentication endpoints (max 5 login attempts per IP per minute) using bucket4j in `backend/src/main/kotlin/com/worklog/infrastructure/ratelimit/RateLimitConfig.kt`
- [ ] T158 Update quickstart.md with initial admin user creation script and testing instructions
- [ ] T159 Run full smoke test following quickstart.md validation steps

---

## Dependencies & Execution Order

### Phase Dependencies

1. **Phase 1 (Setup)** → **Phase 2 (Foundational)** → **Phase 2A (Test Infrastructure)** → **User Stories (Phase 3-6)** → **Phase 7-8 (Polish)**
2. **Phase 2 BLOCKS all user stories** - must complete before any US work begins
3. **Phase 2A REQUIRED** - test infrastructure must be ready before writing tests (Constitution Principle II)
4. Once Phase 2 + 2A complete, user stories can proceed:
   - **Sequential (recommended by priority)**: US1 (P1) → US4 (P1) → US2 (P2) → US3 (P3)
   - **Parallel (if multiple developers)**: US1 must complete first; then US2+US4 can run parallel; US3 depends only on Phase 2 entities

### User Story Dependencies

- **US1 (Account/Login)**: No dependencies on other stories - can start after Phase 2+2A
- **US4 (Session Management)**: Builds on US1 session infrastructure - start after US1 complete OR parallel with careful coordination
- **US2 (Password Reset)**: Independent of other stories - can start after Phase 2+2A, references User entity only
- **US3 (RBAC)**: Depends on User and Role entities from Phase 2 only - can start after Phase 2+2A

### Within Each User Story

- **Test-First Approach**: Write unit tests FIRST → Verify tests FAIL → Implement feature → Verify tests PASS
- Backend: entities → repositories → services → controllers
- Frontend: API clients → hooks → components → pages
- Tests validate behavior at each layer

### Parallel Opportunities

**Phase 1**: T003-T009 (7 parallel tasks)  
**Phase 2**: T011-T021 (11 parallel tasks)  
**Phase 2A**: T022-T025 (4 parallel tasks)  
**Phase 3 (US1) Tests**: T026-T042 (17 parallel test tasks)  
**Phase 3 (US1) Implementation**: T043-T045, T060-T062 (6 parallel tasks)  
**Phase 4 (US4) Tests**: T070-T074 (5 parallel test tasks)  
**Phase 4 (US4) Implementation**: T075-T076 (2 parallel tasks)  
**Phase 5 (US2) Tests**: T086-T091 (6 parallel test tasks)  
**Phase 5 (US2) Implementation**: T092-T093, T104-T105 (4 parallel tasks)  
**Phase 6 (US3) Tests**: T107-T114 (8 parallel test tasks)  
**Phase 6 (US3) Implementation**: T115-T116, T121-T124, T133-T134 (8 parallel tasks)  
**Phase 7**: T139-T141 (3 parallel tasks)  
**Phase 8 Tests**: T145-T151 (7 parallel tasks)  
**Phase 8 Implementation**: T152-T155 (4 parallel tasks)

**Total Parallel Tasks**: 92 (60% of all tasks can run in parallel)

---

## Parallel Example: User Story 1

```bash
# Launch all command objects for US1 together:
opencode "Create SignupCommand, LoginCommand, VerifyEmailCommand"

# Launch all frontend API clients for US1 together:
opencode "Create API client functions for signup, login, logout, verifyEmail"

# Launch all frontend pages for US1 together:
opencode "Create signup page, login page, email verification page"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete **Phase 1: Setup** (10 tasks)
2. Complete **Phase 2: Foundational** (11 tasks) - CRITICAL BLOCKER
3. Complete **Phase 2A: Test Infrastructure** (4 tasks) - REQUIRED per Constitution Principle II
4. Complete **Phase 3: User Story 1** (60 tasks: 22 tests + 38 implementation)
5. **STOP and VALIDATE**: 
   - Run all 22 unit tests → Verify 100% pass rate
   - Test E2E flow: signup → verify email → login → logout
   - Verify FR-017: unverified users see warning banner and restricted from work_log.* permissions
6. Deploy/demo MVP if ready

**Total MVP Tasks**: 85 tasks (10+11+4+60)

### Incremental Delivery

1. **Foundation** (Phase 1+2+2A): 25 tasks → Database, security, and test infrastructure ready
2. **MVP** (+ Phase 3): +60 tasks → Users can create accounts and log in (with tests)
3. **Session Management** (+ Phase 4): +15 tasks → Sessions timeout and remember-me works (with tests)
4. **Password Reset** (+ Phase 5): +18 tasks → Users can reset forgotten passwords (with tests)
5. **Full RBAC** (+ Phase 6): +28 tasks → Admins can manage users (with tests)
6. **Profile + Polish** (+ Phase 7+8): +20 tasks → Production ready (with E2E/performance tests)

Each increment adds value without breaking previous functionality.

**Total Implementation Tasks**: 166 (110 original + 43 test tasks + 4 test setup + 9 new requirements)

---

## Notes

- **[P] markers**: Tasks operating on different files with no dependencies - can run in parallel
- **[Story] labels**: Map tasks to user stories for traceability (US1, US2, US3, US4)
- **Tests**: INCLUDED per Constitution Principle II - 43 test tasks across all phases ensure quality and regression prevention
- **Test-First**: Write tests BEFORE implementation → Verify FAIL → Implement → Verify PASS (TDD cycle)
- **File paths**: All tasks include exact file paths for implementation
- **Checkpoints**: Each phase ends with validation criteria for independent testing
- **Anti-enumeration**: Password reset always returns 200 (FR-011 security requirement)
- **Email verification**: Required before full access (FR-017 - task T059 implements restrictions)
- **FR-017 Clarification**: Unverified users blocked from: `work_log.create`, `work_log.approve`, `admin.access` (implemented in CustomPermissionEvaluator)
- **Session cleanup**: Automated daily job for expired sessions and stale tokens
- **Permission format**: `<resource>.<action>` (e.g., `user.create`, `report.view`) per FR-019
- **PersistentLoginRepository**: Created in Phase 4, task T079 - extends Spring Security's JdbcTokenRepositoryImpl
- **Performance**: Phase 8 includes Gatling load tests (T148) and Prometheus metrics (T149)
- **Accessibility**: Phase 8 includes WCAG 2.1 AA audit (T151)

---

**Generated by**: /speckit.tasks command  
**Last updated**: 2026-02-03 (Added 43 test tasks, FR-017 implementation, performance/monitoring tasks per analysis findings)
