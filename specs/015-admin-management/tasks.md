# Tasks: Admin Management

**Input**: Design documents from `/specs/015-admin-management/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/api-contracts.md, quickstart.md

**Tests**: Integration tests (backend) and component tests (frontend) are included per constitution requirements. Test tasks are placed at the end of each user story phase.

**Organization**: Tasks are grouped by user story (P1–P7) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/com/worklog/` (domain, api, application), `backend/src/main/kotlin/com/worklog/infrastructure/` (config, repository)
- **Frontend**: `frontend/app/` (pages, components, services, hooks)
- **Migrations**: `backend/src/main/resources/db/migration/`

---

## Phase 1: Setup (Database & Seed Data)

**Purpose**: Create database schema for new entities, seed admin roles/permissions, and update dev test data.

- [x] T001 [P] Create daily entry approval table migration in backend/src/main/resources/db/migration/V16__daily_entry_approval.sql — define `daily_entry_approvals` table with columns (id UUID PK, work_log_entry_id UUID FK, member_id UUID FK, supervisor_id UUID FK, status VARCHAR(20) CHECK, comment VARCHAR(500), created_at, updated_at), partial unique index on (work_log_entry_id) WHERE status != 'RECALLED', and indexes per data-model.md
- [x] T002 [P] Create in-app notifications table migration in backend/src/main/resources/db/migration/V17__in_app_notifications.sql — define `in_app_notifications` table with columns (id UUID PK, recipient_member_id UUID FK, type VARCHAR(30), reference_id UUID, title VARCHAR(200), message VARCHAR(500), is_read BOOLEAN DEFAULT FALSE, created_at), composite index on (recipient_member_id, is_read, created_at DESC)
- [x] T003 [P] Create admin permissions seed migration in backend/src/main/resources/db/migration/V18__admin_permissions_seed.sql — INSERT roles (SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR), INSERT 27 permissions (tenant.view/create/update/deactivate, user.view/update_role/lock/reset_password, member.view/create/update/deactivate, project.view/create/update/deactivate, assignment.view/create/deactivate, daily_approval.view/approve/reject/recall, monthly_approval.view/approve/reject, tenant_admin.assign), INSERT role_permissions mappings per data-model.md. Use ON CONFLICT DO NOTHING for idempotency
- [x] T004 Update dev seed data in backend/src/main/resources/data-dev.sql — assign SYSTEM_ADMIN role to existing admin test user, assign TENANT_ADMIN role to one test user per tenant, assign SUPERVISOR role to test users with manager_id relationships, create sample daily_entry_approvals and in_app_notifications for testing

---

## Phase 2: Foundational (Permission Framework & Admin Shell)

**Purpose**: Core permission enforcement infrastructure and admin UI shell that MUST be complete before ANY user story can be implemented.

**CRITICAL**: No user story work can begin until this phase is complete.

- [x] T005 Create AdminRole constants in backend/src/main/java/com/worklog/shared/AdminRole.java — define static final Strings for SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR role names matching V16 seed data
- [x] T006 Implement CustomPermissionEvaluator in backend/src/main/kotlin/com/worklog/infrastructure/config/CustomPermissionEvaluator.kt — implement Spring Security PermissionEvaluator interface, resolve authenticated user's role from SecurityContext, query role_permissions table to check if user has the requested `resource.action` permission, register as @Component
- [x] T007 Update SecurityConfig.kt in backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt — add URL-pattern rules for `/api/v1/admin/**` requiring authentication, keep `/api/v1/worklog/**` and `/api/v1/notifications/**` as authenticated endpoints, register TenantStatusFilter in the filter chain after authentication, preserve existing dev profile permitAll behavior with a TODO flag for production role enforcement
- [x] T007a [P] Implement TenantStatusFilter in backend/src/main/kotlin/com/worklog/infrastructure/config/TenantStatusFilter.kt — Spring OncePerRequestFilter that resolves authenticated user's tenant from SecurityContext, checks tenant status via TenantRepository/projection query. If tenant is INACTIVE, return 403 with JSON error body {"error": "TENANT_DEACTIVATED", "message": "Your tenant has been deactivated"}. Skip filter for unauthenticated requests and SYSTEM_ADMIN role users (who operate cross-tenant)
- [x] T008a [P] Integrate admin actions with audit logging — extend existing audit log infrastructure (AuditLogInterceptor or create AdminAuditAspect in backend/src/main/kotlin/com/worklog/infrastructure/config/AdminAuditAspect.kt) to capture all admin controller actions (create, update, deactivate, activate, role change, approve, reject, recall) with actor ID, action type, target entity type/ID, and request details. Persist to existing audit_logs table with JSONB payload
- [x] T008 Create AdminContextController in backend/src/main/java/com/worklog/api/AdminContextController.java — implement GET /api/v1/admin/context endpoint returning authenticated user's role name, permissions list, tenantId, tenantName, and memberId as JSON response record. Resolve user from SecurityContext, look up role and permissions from database
- [x] T009 Extend frontend API client in frontend/app/services/api.ts — add `admin` namespace with methods: getContext(), and placeholder method groups for members, projects, assignments, tenants, users. Add `dailyApproval` namespace with methods: getEntries(), approve(), reject(), recall(). Add `notification` namespace with methods: list(), markRead(), markAllRead(). Follow existing api.* pattern using apiClient.get/post/put/patch
- [x] T010 Create admin layout with role-based auth guard in frontend/app/admin/layout.tsx — wrap children with AuthGuard, call api.admin.getContext() on mount to verify admin role, redirect non-admin users to /worklog, store admin context (role, permissions, tenantName) in React state and pass via React Context, render AdminNav sidebar + main content area
- [x] T011 [P] Create AdminNav sidebar component in frontend/app/components/admin/AdminNav.tsx — render navigation links conditionally based on user permissions from admin context: show Tenants/Users for SYSTEM_ADMIN, show Members/Projects/Assignments for TENANT_ADMIN, show Daily Approval for SUPERVISOR. Use Tailwind CSS for styling consistent with existing app design
- [x] T012 [P] Create admin dashboard page in frontend/app/admin/page.tsx — display role-specific summary cards (e.g., member count for Tenant Admin, pending approvals for Supervisor), use admin context from layout to determine which cards to show

**Checkpoint**: Permission framework active, admin shell renders with role-based navigation. User story implementation can begin.

---

## Phase 3: User Story 1 — Tenant Admin Manages Users (Priority: P1) MVP

**Goal**: Tenant Admin can view, invite, edit, deactivate, and reactivate members within their tenant.

**Independent Test**: Log in as tenant-admin@acme.dev, navigate to /admin/members, invite a new member, edit their details, deactivate them, then reactivate. Verify all actions succeed and member list updates.

### Implementation for User Story 1

- [x] T013 [P] [US1] Create InviteMemberCommand record in backend/src/main/java/com/worklog/application/command/InviteMemberCommand.java — fields: email, displayName, organizationId, managerId (nullable), invitedBy (UUID of admin performing action)
- [x] T014 [P] [US1] Create UpdateMemberCommand record in backend/src/main/java/com/worklog/application/command/UpdateMemberCommand.java — fields: memberId, email, displayName, organizationId, managerId (nullable), updatedBy
- [x] T015 [US1] Create AdminMemberService in backend/src/main/java/com/worklog/application/service/AdminMemberService.java — @Service @Transactional, inject MemberRepository + UserRepository + RoleRepository. Implement: listMembers(tenantId, filters, pagination) returning Page, inviteMember(command) creating both User (UNVERIFIED) and Member records, updateMember(command), deactivateMember(memberId) with supervisor reassignment logic (see T015a), activateMember(memberId), assignTenantAdmin(memberId, assignedBy) validating target is in same tenant and updating user role to TENANT_ADMIN. Enforce tenant isolation by comparing command's tenantId with repository lookups
- [x] T015a [US1] Handle supervisor deactivation in AdminMemberService — when deactivating a member who has the SUPERVISOR role or has direct reports (manager_id references), check for direct reports. If found, cascade manager_id to the deactivated member's own manager_id. If no parent manager exists, set manager_id to NULL and create an InAppNotification for the Tenant Admin alerting that manual reassignment is needed. Log a warning via SLF4J
- [x] T016 [US1] Create AdminMemberController in backend/src/main/java/com/worklog/api/AdminMemberController.java — @RestController @RequestMapping("/api/v1/admin/members"). Define request/response DTOs as inner records. Endpoints: GET / (list with pagination, search, organizationId, isActive filters), POST / (invite), PUT /{id} (update), PATCH /{id}/deactivate, PATCH /{id}/activate, POST /{id}/assign-tenant-admin (assign Tenant Admin role). Add @PreAuthorize("hasPermission(null, 'member.view')") etc. per endpoint, POST /{id}/assign-tenant-admin uses @PreAuthorize("hasPermission(null, 'tenant_admin.assign')")
- [x] T017 [P] [US1] Create MemberList component in frontend/app/components/admin/MemberList.tsx — table displaying members with columns: name, email, organization, manager, status (active/inactive badge), actions (edit/deactivate/activate). Include search input, organization filter dropdown, active status toggle filter. Support pagination with page size selector
- [x] T018 [P] [US1] Create MemberForm component in frontend/app/components/admin/MemberForm.tsx — form for inviting new member and editing existing member. Fields: email (input), displayName (input), organization (select dropdown loaded from API), manager (select dropdown of active members). Submit calls api.admin.members.create() or api.admin.members.update(). Show validation errors inline
- [x] T019 [US1] Create admin members page in frontend/app/admin/members/page.tsx — compose MemberList and MemberForm (modal/drawer). Load member list via api.admin.members.list() using useCallback + useEffect pattern. Handle create/edit/deactivate/activate actions with optimistic UI updates and error handling. Show loading spinner and empty state
- [ ] T019a [US1] Write integration tests for AdminMemberController in backend/src/test/kotlin/com/worklog/api/AdminMemberControllerTest.kt — test list with pagination/search/filters, invite member, update member, deactivate/activate, tenant admin role assignment endpoint, verify @PreAuthorize enforcement (403 for non-admin), verify tenant isolation (cannot access other tenant's members)
- [ ] T019b [P] [US1] Write frontend tests for MemberList and MemberForm in frontend/app/components/admin/__tests__/MemberList.test.tsx and MemberForm.test.tsx — test rendering, search/filter interactions, form validation (required fields, email format), API call mocking, optimistic UI updates with Vitest + React Testing Library

**Checkpoint**: Tenant Admin can fully manage members. MVP is functional and independently testable.

---

## Phase 4: User Story 2 — Tenant Admin Manages Projects (Priority: P2)

**Goal**: Tenant Admin can view, create, edit, deactivate, and activate projects with codes and validity periods.

**Independent Test**: Log in as tenant-admin@acme.dev, navigate to /admin/projects, create a project with code "TEST-001" and validity dates, edit the name, deactivate it, verify it shows as inactive.

### Implementation for User Story 2

- [x] T020 [P] [US2] Create CreateProjectCommand and UpdateProjectCommand records in backend/src/main/java/com/worklog/application/command/ — CreateProjectCommand: tenantId, code, name, validFrom (nullable), validUntil (nullable). UpdateProjectCommand: projectId, name, validFrom, validUntil
- [x] T021 [US2] Create AdminProjectService in backend/src/main/java/com/worklog/application/service/AdminProjectService.java — @Service @Transactional, inject ProjectRepository. Implement: listProjects(tenantId, filters, pagination), createProject(command) with duplicate code validation, updateProject(command), deactivateProject(projectId), activateProject(projectId). Return assignedMemberCount in list responses via join query
- [x] T022 [US2] Create AdminProjectController in backend/src/main/java/com/worklog/api/AdminProjectController.java — @RestController @RequestMapping("/api/v1/admin/projects"). DTOs: CreateProjectRequest, UpdateProjectRequest, ProjectResponse (with assignedMemberCount). Endpoints: GET /, POST /, PUT /{id}, PATCH /{id}/deactivate, PATCH /{id}/activate. @PreAuthorize with project.* permissions
- [x] T023 [P] [US2] Create ProjectList component in frontend/app/components/admin/ProjectList.tsx — table with columns: code, name, status, validFrom, validUntil, assignedMemberCount, actions. Include search input and active status filter. Pagination support
- [x] T024 [P] [US2] Create ProjectForm component in frontend/app/components/admin/ProjectForm.tsx — form with fields: code (input, disabled on edit), name (input), validFrom (date picker), validUntil (date picker). Date validation: validUntil must be after validFrom. Duplicate code error handling
- [x] T025 [US2] Create admin projects page in frontend/app/admin/projects/page.tsx — compose ProjectList and ProjectForm. Load via api.admin.projects.list(). Handle CRUD actions with feedback
- [ ] T025a [US2] Write integration tests for AdminProjectController in backend/src/test/kotlin/com/worklog/api/AdminProjectControllerTest.kt — test CRUD endpoints, duplicate code validation (409), validity date validation, deactivate/activate, verify @PreAuthorize with project.* permissions
- [ ] T025b [P] [US2] Write frontend tests for ProjectList and ProjectForm in frontend/app/components/admin/__tests__/ProjectList.test.tsx and ProjectForm.test.tsx — test rendering, search/filter, form validation, date picker interactions, duplicate code error display with Vitest + React Testing Library

**Checkpoint**: Tenant Admin can manage projects. US1 + US2 both work independently.

---

## Phase 5: User Story 3 — User-Project Assignment Management (Priority: P3)

**Goal**: Tenant Admin can assign any member to any project. Supervisor can assign direct reports to projects. Assignments can be activated/deactivated.

**Independent Test**: Log in as tenant-admin, assign a member to a project, verify assignment appears. Log in as supervisor, verify only direct reports are visible, assign a direct report to a project, attempt to assign a non-direct-report (should fail).

### Implementation for User Story 3

- [x] T026 [P] [US3] Create CreateAssignmentCommand record in backend/src/main/java/com/worklog/application/command/CreateAssignmentCommand.java — fields: tenantId, memberId, projectId, assignedBy
- [x] T027 [US3] Create AdminAssignmentService in backend/src/main/java/com/worklog/application/service/AdminAssignmentService.java — @Service @Transactional, inject MemberProjectAssignmentRepository, MemberRepository. Implement: listByMember(memberId), listByProject(projectId), createAssignment(command) with duplicate check, deactivateAssignment(assignmentId), activateAssignment(assignmentId). For SUPERVISOR role: validate that the memberId is a direct report (member.managerId == supervisorMemberId), throw 403 otherwise
- [x] T028 [US3] Create AdminAssignmentController in backend/src/main/java/com/worklog/api/AdminAssignmentController.java — @RestController @RequestMapping("/api/v1/admin/assignments"). Endpoints: GET /members/{memberId}/assignments (aliased under /admin), GET /projects/{projectId}/assignments, POST / (create), PATCH /{id}/deactivate, PATCH /{id}/activate. @PreAuthorize with assignment.* permissions. Include project name/code and member name in response DTOs
- [x] T029 [P] [US3] Create AssignmentManager component in frontend/app/components/admin/AssignmentManager.tsx — dual-view component: "by member" (select member, see their project assignments) and "by project" (select project, see assigned members). Add/remove assignment buttons. Supervisor view: member dropdown restricted to direct reports only. Activate/deactivate toggle per assignment
- [x] T030 [US3] Create admin assignments page in frontend/app/admin/assignments/page.tsx — compose AssignmentManager component. Load member list and project list on mount. Use admin context to determine if user is Tenant Admin (full access) or Supervisor (restricted to direct reports)
- [ ] T030a [US3] Write integration tests for AdminAssignmentController in backend/src/test/kotlin/com/worklog/api/AdminAssignmentControllerTest.kt — test create assignment, duplicate assignment (409), deactivate/activate, list by member and by project, verify supervisor direct-report restriction (403 for non-direct-report), verify @PreAuthorize with assignment.* permissions

**Checkpoint**: Assignment management works for both Tenant Admin and Supervisor. US1–US3 independently functional.

---

## Phase 6: User Story 4 — Supervisor Approves Daily Entries (Priority: P4)

**Goal**: Supervisor views saved entries from direct reports automatically, approves/rejects individually or in bulk, recalls approvals before monthly sign-off. Members receive in-app notifications.

**Independent Test**: As a regular user, save work log entries. Log in as their supervisor, navigate to /worklog/daily-approval, see entries grouped by date. Approve one, reject another with comment, bulk-approve remaining. Verify NotificationBell shows notifications for the member.

### Implementation for User Story 4

- [x] T031 [P] [US4] Create DailyEntryApproval domain entity in backend/src/main/java/com/worklog/domain/dailyapproval/DailyEntryApproval.java — fields: id (DailyEntryApprovalId), workLogEntryId (UUID), memberId (MemberId), supervisorId (MemberId), status (DailyApprovalStatus), comment (String max 500), createdAt, updatedAt. Factory: create(workLogEntryId, memberId, supervisorId, status, comment). Methods: recall() — validates status is APPROVED, sets to RECALLED. Validation: comment required when status is REJECTED
- [x] T032 [P] [US4] Create DailyEntryApprovalId value object in backend/src/main/java/com/worklog/domain/dailyapproval/DailyEntryApprovalId.java — UUID-based value object following existing pattern (e.g., MemberId, ProjectId)
- [x] T033 [P] [US4] Create DailyApprovalStatus enum in backend/src/main/java/com/worklog/domain/dailyapproval/DailyApprovalStatus.java — values: APPROVED, REJECTED, RECALLED
- [x] T034 [US4] Create InAppNotification domain entity in backend/src/main/java/com/worklog/domain/notification/InAppNotification.java — fields: id (NotificationId), recipientMemberId (MemberId), type (NotificationType), referenceId (UUID), title (String max 200), message (String max 500), isRead (boolean), createdAt. Factory: create(recipientMemberId, type, referenceId, title, message). Methods: markRead()
- [x] T035 [P] [US4] Create NotificationId value object in backend/src/main/java/com/worklog/domain/notification/NotificationId.java and NotificationType enum in backend/src/main/java/com/worklog/domain/notification/NotificationType.java — types: DAILY_APPROVED, DAILY_REJECTED, DAILY_RECALLED, MONTHLY_SUBMITTED, MONTHLY_APPROVED, MONTHLY_REJECTED
- [x] T036 [US4] Create DailyEntryApprovalRepository in backend/src/main/kotlin/com/worklog/infrastructure/repository/DailyEntryApprovalRepository.kt — Spring Data JDBC interface extending CrudRepository. Custom queries: findByWorkLogEntryIdAndStatusNot(entryId, RECALLED), findBySupervisorIdAndStatus(supervisorId, status), findByMemberIdAndWorkLogEntryIdIn(memberId, entryIds)
- [x] T037 [P] [US4] Create InAppNotificationRepository in backend/src/main/kotlin/com/worklog/infrastructure/repository/InAppNotificationRepository.kt — Spring Data JDBC interface. Custom queries: findByRecipientMemberIdOrderByCreatedAtDesc(memberId, pageable), countByRecipientMemberIdAndIsReadFalse(memberId)
- [x] T038 [US4] Create NotificationService in backend/src/main/java/com/worklog/application/service/NotificationService.java — @Service, inject InAppNotificationRepository. Implement: createNotification(recipientMemberId, type, referenceId, title, message), listNotifications(memberId, isRead, pageable), markRead(notificationId), markAllRead(memberId), getUnreadCount(memberId)
- [x] T039 [US4] Create NotificationController in backend/src/main/java/com/worklog/api/NotificationController.java — @RestController @RequestMapping("/api/v1/notifications"). Endpoints: GET / (list with pagination, isRead filter, include unreadCount in response), PATCH /{id}/read, PATCH /read-all. Resolve recipientMemberId from SecurityContext
- [x] T040 [US4] Create ApproveDailyEntryCommand, RejectDailyEntryCommand, RecallDailyApprovalCommand records in backend/src/main/java/com/worklog/application/command/ — Approve: entryIds (List<UUID>), supervisorId, comment (nullable). Reject: entryId, supervisorId, comment (required). Recall: approvalId, supervisorId
- [x] T041 [US4] Create DailyApprovalService in backend/src/main/java/com/worklog/application/service/DailyApprovalService.java — @Service @Transactional. Implement: getDailyEntries(supervisorId, dateFrom, dateTo, memberId filter) — query work_log_entries_projection joined with daily_entry_approvals for supervisor's direct reports, group by date and member. approveEntries(command) — create DailyEntryApproval records with APPROVED status for each entryId, validate supervisor is manager, call NotificationService for each member. rejectEntry(command) — create REJECTED record, notify member. recallApproval(command) — validate monthly not approved via MonthlyApproval lookup, set status to RECALLED, notify member
- [x] T042 [US4] Create DailyApprovalController in backend/src/main/java/com/worklog/api/DailyApprovalController.java — @RestController @RequestMapping("/api/v1/worklog/daily-approvals"). DTOs: DailyGroupResponse (date, members list with entries), ApproveRequest (entryIds, comment), RejectRequest (entryId, comment). Endpoints: GET / (list grouped), POST /approve (bulk), POST /reject, POST /{approvalId}/recall. @PreAuthorize with daily_approval.* permissions
- [x] T043 [P] [US4] Create DailyApprovalDashboard component in frontend/app/components/admin/DailyApprovalDashboard.tsx — display entries grouped by date then by team member. Each entry row shows: project code/name, hours, comment, approval status badge (unapproved/approved/rejected). Checkboxes for bulk selection. Action buttons: Approve Selected, Reject (opens comment modal), Recall (on approved entries). Date range picker for filtering
- [x] T044 [P] [US4] Create NotificationBell component in frontend/app/components/shared/NotificationBell.tsx — icon button in app header showing unread notification count badge. On click, opens dropdown with recent notifications (title, message, time ago, read/unread indicator). "Mark all read" link. Each notification clickable to navigate to related entity
- [x] T045 [P] [US4] Create useNotifications hook in frontend/app/hooks/useNotifications.ts — poll api.notification.list() every 3 seconds for unread count. Expose: notifications, unreadCount, markRead(id), markAllRead(), isLoading. Use useCallback + setInterval pattern. Stop polling when tab is not visible (document.hidden)
- [x] T046 [US4] Create daily approval page in frontend/app/worklog/daily-approval/page.tsx — compose DailyApprovalDashboard. Load entries via api.dailyApproval.getEntries(). Handle approve/reject/recall actions with feedback. Show loading and empty states ("No pending entries for your team")
- [x] T047 [US4] Integrate NotificationBell into app header — add NotificationBell component to the shared header/layout used across all authenticated pages (frontend/app/worklog/layout.tsx or equivalent shared layout). Wire with useNotifications hook
- [ ] T047a [US4] Write integration tests for DailyApprovalController in backend/src/test/kotlin/com/worklog/api/DailyApprovalControllerTest.kt — test approve (single and bulk), reject with comment (400 if comment missing), recall (success and 400 when monthly already approved), list grouped by date/member, verify @PreAuthorize with daily_approval.* permissions, verify supervisor can only approve direct reports
- [ ] T047b [US4] Write integration tests for NotificationController in backend/src/test/kotlin/com/worklog/api/NotificationControllerTest.kt — test list with pagination and isRead filter, unreadCount in response, markRead, markAllRead, verify notifications are scoped to authenticated user only

**Checkpoint**: Daily approval workflow fully functional. Supervisor can review, approve, reject, recall. Members see notifications. US1–US4 independently work.

---

## Phase 7: User Story 5 — Supervisor Approves Monthly Records (Priority: P5)

**Goal**: Enhance existing monthly approval with daily approval status warnings and summary view showing project breakdown and absence data.

**Independent Test**: As a member, submit monthly record. Log in as supervisor, view submitted record, see summary with hours/project breakdown, verify daily rejection warnings appear for unapproved entries, approve or reject.

### Implementation for User Story 5

- [x] T048 [US5] Extend ApprovalService in backend/src/main/java/com/worklog/application/service/ApprovalService.java — add method getMonthlyApprovalDetail(approvalId) that returns existing approval data enriched with: total hours, project-wise hour breakdown, absence summary, list of daily entry approval statuses (approved/rejected/unapproved counts), list of entries with unresolved daily rejections. Query daily_entry_approvals table for entries in the approval's workLogEntryIds set
- [x] T049 [US5] Extend ApprovalController in backend/src/main/java/com/worklog/api/ApprovalController.java — add GET /api/v1/worklog/approvals/{id}/detail endpoint returning MonthlyApprovalDetailResponse record with: approval status, member name, fiscal month dates, total work hours, total absence hours, project breakdown list (projectCode, projectName, hours), daily approval summary (approvedCount, rejectedCount, unapprovedCount), unresolvedEntries list (entryId, date, projectCode, dailyRejectionComment). Send MONTHLY_SUBMITTED notification to supervisor when submission occurs
- [x] T050 [P] [US5] Create MonthlyApprovalSummary component in frontend/app/components/worklog/MonthlyApprovalSummary.tsx — display: member name, fiscal month range, total hours, project breakdown table (project code, name, hours, percentage), absence summary, daily approval status bar (green=approved, red=rejected, gray=unapproved with counts), warning banner for unresolved daily rejections listing each affected entry with date and project
- [x] T051 [US5] Update monthly approval page in frontend/app/worklog/approval/page.tsx — when supervisor clicks a submitted monthly record, load detail via api.approval.getDetail(id) and display MonthlyApprovalSummary component in a detail view. Show approve/reject buttons with reject reason modal. After action, send notification to member via NotificationService integration
- [ ] T051a [US5] Write integration tests for extended ApprovalController detail endpoint in backend/src/test/kotlin/com/worklog/api/ApprovalControllerDetailTest.kt — test monthly detail response includes daily approval status summary (approved/rejected/unapproved counts), unresolved daily rejections list, project breakdown, and absence data

**Checkpoint**: Monthly approval enhanced with daily status integration. US1–US5 independently functional.

---

## Phase 8: User Story 6 — System Admin Manages Tenants (Priority: P6)

**Goal**: System Admin can view, create, edit, deactivate, and activate tenants across the system.

**Independent Test**: Log in as admin@miometry.dev (SYSTEM_ADMIN), navigate to /admin/tenants, create a tenant with code "NEWCO", edit name, deactivate, verify duplicate code validation works.

### Implementation for User Story 6

- [x] T052 [US6] Create AdminTenantService in backend/src/main/java/com/worklog/application/service/AdminTenantService.java — @Service @Transactional. Implement: listTenants(status filter, pagination) querying tenant projection or event store, createTenant(code, name) using Tenant.create() aggregate factory + event store persist, updateTenant(id, name) using Tenant.update() + event store, deactivateTenant(id) using Tenant.deactivate() + event store, activateTenant(id). Handle TenantCreated/Updated/Deactivated/Activated events
- [x] T053 [US6] Create AdminTenantController in backend/src/main/java/com/worklog/api/AdminTenantController.java — @RestController @RequestMapping("/api/v1/admin/tenants"). DTOs: CreateTenantRequest(code, name), UpdateTenantRequest(name), TenantResponse. Endpoints: GET / (list with status filter, pagination), POST / (create), PUT /{id} (update name), PATCH /{id}/deactivate, PATCH /{id}/activate. @PreAuthorize with tenant.* permissions. Return 409 on duplicate code
- [x] T054 [P] [US6] Create TenantList component in frontend/app/components/admin/TenantList.tsx — table with columns: code, name, status (active/inactive badge), createdAt, actions (edit/deactivate/activate). Status filter toggle. Pagination
- [x] T055 [P] [US6] Create TenantForm component in frontend/app/components/admin/TenantForm.tsx — form with code (input, disabled on edit) and name (input). Duplicate code error handling (409 response)
- [x] T056 [US6] Create admin tenants page in frontend/app/admin/tenants/page.tsx — compose TenantList and TenantForm. Tenant Admin context check (redirect if not SYSTEM_ADMIN). Load via api.admin.tenants.list()
- [ ] T056a [US6] Write integration tests for AdminTenantController in backend/src/test/kotlin/com/worklog/api/AdminTenantControllerTest.kt — test list with status filter, create with duplicate code validation (409), update name, deactivate/activate, verify @PreAuthorize with tenant.* permissions

**Checkpoint**: System Admin can manage tenants. US1–US6 independently functional.

---

## Phase 9: User Story 7 — System Admin Manages Users Globally (Priority: P7)

**Goal**: System Admin can view all users across tenants, change roles, lock/unlock accounts, and initiate password resets. Last Tenant Admin protection enforced.

**Independent Test**: Log in as admin@miometry.dev, navigate to /admin/users, filter by tenant/role/status, change a user's role, lock an account, unlock it, initiate password reset.

### Implementation for User Story 7

- [x] T057 [US7] Create AdminUserService in backend/src/main/java/com/worklog/application/service/AdminUserService.java — @Service @Transactional, inject UserRepository, RoleRepository, MemberRepository. Implement: listUsers(tenantId, roleId, accountStatus, search, pagination) with cross-tenant query joining users + members + roles, changeRole(userId, newRoleId) with last-Tenant-Admin protection (count TENANT_ADMIN role users in same tenant, reject if count == 1 and target is the last one), lockUser(userId, durationMinutes), unlockUser(userId), initiatePasswordReset(userId) delegating to existing password reset flow
- [x] T058 [US7] Create AdminUserController in backend/src/main/java/com/worklog/api/AdminUserController.java — @RestController @RequestMapping("/api/v1/admin/users"). DTOs: UserListResponse (id, email, name, roleName, tenantName, accountStatus, lastLoginAt), ChangeRoleRequest(roleId), LockRequest(durationMinutes). Endpoints: GET / (list with filters), PUT /{id}/role (change role, 409 on last admin), PATCH /{id}/lock, PATCH /{id}/unlock, POST /{id}/password-reset. @PreAuthorize with user.* permissions
- [x] T059 [P] [US7] Create UserList component in frontend/app/components/admin/UserList.tsx — table with columns: email, name, role (badge), tenant, account status (color-coded badge), last login. Filters: tenant dropdown, role dropdown, status dropdown, search input. Pagination. Row actions: change role (dropdown), lock/unlock toggle, reset password button
- [x] T060 [US7] Create admin users page in frontend/app/admin/users/page.tsx — compose UserList component. SYSTEM_ADMIN role check. Load via api.admin.users.list(). Handle role change with confirmation dialog (especially for last admin warning), lock/unlock with duration input, password reset with confirmation
- [ ] T060a [US7] Write integration tests for AdminUserController in backend/src/test/kotlin/com/worklog/api/AdminUserControllerTest.kt — test list with filters (tenant, role, status, search), role change, last-Tenant-Admin protection (409 response), lock/unlock, password reset initiation, verify @PreAuthorize with user.* permissions

**Checkpoint**: System Admin can manage all users globally. All 7 user stories independently functional.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories and ensure production readiness.

- [x] T061 [P] Add confirmation dialogs for all destructive admin actions — add reusable ConfirmDialog component in frontend/app/components/shared/ConfirmDialog.tsx, integrate into deactivate member, deactivate project, deactivate tenant, lock user, and role change actions across all admin pages
- [x] T062 [P] Add empty state illustrations and messages to all admin list components — "No members found" for MemberList, "No projects found" for ProjectList, "No pending entries" for DailyApprovalDashboard, etc. with clear call-to-action buttons
- [x] T063 Run backend format and static analysis checks — execute `./gradlew formatAll && ./gradlew checkFormat && ./gradlew detekt` from backend/, fix any formatting or detekt violations in new code
- [x] T064 Run frontend lint and format checks — execute `npm run check:ci` from frontend/, fix any Biome lint or format violations in new code
- [ ] T065 Validate quickstart.md — follow the setup guide in specs/015-admin-management/quickstart.md end-to-end, verify all test users can log in with correct roles, verify admin navigation shows correct menu items per role, verify at least one operation per user story works

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **User Stories (Phases 3–9)**: All depend on Phase 2 completion
  - US1 (P1): No cross-story dependencies
  - US2 (P2): No cross-story dependencies
  - US3 (P3): No cross-story dependencies (uses existing Member + Project models)
  - US4 (P4): No cross-story dependencies (introduces DailyEntryApproval + Notification system)
  - US5 (P5): Depends on US4 (uses DailyEntryApproval for warning data)
  - US6 (P6): No cross-story dependencies
  - US7 (P7): No cross-story dependencies
- **Polish (Phase 10)**: Depends on all desired user stories being complete

### Within Each User Story

- Commands/DTOs before Services
- Services before Controllers
- Controllers before Frontend pages
- Backend and Frontend components marked [P] can run in parallel within same story

### Parallel Opportunities

- Phase 1: T001, T002, T003 can all run in parallel (different migration files)
- Phase 2: T011, T012 can run in parallel after T010 (different frontend files)
- Phase 3+: After Phase 2 completes, US1, US2, US3, US4, US6, US7 can all start in parallel (US5 waits for US4)
- Within each story: Backend command records (marked [P]) can be created in parallel, frontend components (marked [P]) can be created in parallel

---

## Parallel Example: User Story 1

```bash
# Launch backend commands in parallel (different files):
Task T013: "Create InviteMemberCommand in application/command/InviteMemberCommand.java"
Task T014: "Create UpdateMemberCommand in application/command/UpdateMemberCommand.java"

# After T015 (service), launch frontend components in parallel:
Task T017: "Create MemberList component in components/admin/MemberList.tsx"
Task T018: "Create MemberForm component in components/admin/MemberForm.tsx"
```

## Parallel Example: User Story 4

```bash
# Launch domain entities in parallel (different files):
Task T031: "Create DailyEntryApproval entity in domain/dailyapproval/"
Task T032: "Create DailyEntryApprovalId value object"
Task T033: "Create DailyApprovalStatus enum"
Task T035: "Create NotificationId and NotificationType"

# Launch repositories in parallel (different files):
Task T036: "Create DailyEntryApprovalRepository"
Task T037: "Create InAppNotificationRepository"

# Launch frontend components in parallel:
Task T043: "Create DailyApprovalDashboard component"
Task T044: "Create NotificationBell component"
Task T045: "Create useNotifications hook"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (4 tasks)
2. Complete Phase 2: Foundational (10 tasks)
3. Complete Phase 3: User Story 1 — Tenant Admin Member Management (10 tasks incl. tests)
4. **STOP and VALIDATE**: Test member CRUD independently
5. Deploy/demo if ready — **24 tasks to MVP**

### Incremental Delivery

1. Setup + Foundational → Foundation ready (14 tasks)
2. Add US1 → Member management works → Demo (24 tasks)
3. Add US2 → Project management works → Demo (32 tasks)
4. Add US3 → Assignments work → Demo (38 tasks)
5. Add US4 → Daily approval + notifications work → Demo (57 tasks)
6. Add US5 → Monthly approval enhanced → Demo (62 tasks)
7. Add US6 → Tenant management works → Demo (68 tasks)
8. Add US7 → Global user management works → Demo (73 tasks)
9. Polish → Production ready (78 tasks)

### Parallel Team Strategy

With multiple developers after Phase 2 completes:

- **Developer A**: US1 (P1) → US2 (P2) → US3 (P3) — Admin CRUD stories
- **Developer B**: US4 (P4) → US5 (P5) — Approval workflow stories
- **Developer C**: US6 (P6) → US7 (P7) — System admin stories

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in same phase
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable (except US5 depends on US4)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All new controllers must have @PreAuthorize annotations matching contracts/api-contracts.md
- All new frontend components must use Tailwind CSS consistent with existing design
- All new entities must follow existing naming conventions (see data-model.md)
