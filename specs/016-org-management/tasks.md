# Tasks: Organization Management

**Input**: Design documents from `/specs/016-org-management/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Included per Constitution II (Testing Discipline). Test tasks are placed at the end of each user story phase.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/com/worklog/` (domain, api, application, infrastructure)
- **Frontend**: `frontend/app/` (pages, components, services)
- **Migrations**: `backend/src/main/resources/db/migration/`
- **Backend Tests**: `backend/src/test/java/com/worklog/`
- **Frontend Tests**: `frontend/tests/unit/components/admin/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database migration for new organization management permissions

- [x] T001 Create V19 migration to seed organization permissions (organization.view, organization.create, organization.update, organization.deactivate) and grant to TENANT_ADMIN and SUPERVISOR roles in backend/src/main/resources/db/migration/V19__organization_management_permissions.sql

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T002 Add synchronous projection update (updateProjection method) to OrganizationRepository following the JdbcWorkLogRepository.updateProjection() pattern — INSERT/UPDATE on the organization projection table within the same @Transactional boundary as event_store append in backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java

**Checkpoint**: Foundation ready — organization creates/updates are now visible in projection table queries

---

## Phase 3: User Story 1 — Organization CRUD Management (Priority: P1) MVP

**Goal**: Tenant administrators can create, view, update, deactivate, and reactivate organizations with hierarchical structure (up to 6 levels)

**Independent Test**: Create organizations with parent-child hierarchy, verify list with search/filter/pagination, update name, deactivate/reactivate — all visible in admin panel

### Implementation for User Story 1

#### Backend

- [x] T003 [P] [US1] Create CreateOrganizationCommand (code, name, parentId) and UpdateOrganizationCommand (name) DTOs in backend/src/main/java/com/worklog/application/command/
- [x] T004 [US1] Create AdminOrganizationService with methods: listOrganizations (paginated, search, filter by status/parent), createOrganization (validate code uniqueness, depth limit, parent exists), updateOrganization (validate active status), deactivateOrganization (warn if has active children), activateOrganization in backend/src/main/java/com/worklog/application/service/AdminOrganizationService.java
- [x] T005 [US1] Create AdminOrganizationController with endpoints: GET /api/v1/admin/organizations (list), POST /api/v1/admin/organizations (create, returns 201 with id), PUT /api/v1/admin/organizations/{id} (update, returns 204), PATCH /api/v1/admin/organizations/{id}/deactivate (returns 200 with warnings), PATCH /api/v1/admin/organizations/{id}/activate (returns 204) with @PreAuthorize annotations per contracts/organization-api.md in backend/src/main/java/com/worklog/api/AdminOrganizationController.java

#### Frontend

- [x] T006 [P] [US1] Add organization CRUD API client methods to frontend/app/services/api.ts: listOrganizations(page, size, search, isActive, parentId), createOrganization(code, name, parentId), updateOrganization(id, name), deactivateOrganization(id), activateOrganization(id) — following existing API client patterns
- [x] T007 [P] [US1] Create OrganizationList component with paginated table (code, name, level, parent name, status, member count), debounced search (300ms), status filter dropdown, create button, edit/deactivate/activate action buttons in frontend/app/components/admin/OrganizationList.tsx
- [x] T008 [P] [US1] Create OrganizationForm modal component for create/edit organization with fields: code (create only, alphanumeric+underscore, max 32 chars), name (max 256 chars), parent organization dropdown (active orgs only) with validation and error display in frontend/app/components/admin/OrganizationForm.tsx
- [x] T009 [US1] Create organization management page integrating OrganizationList and OrganizationForm with refreshKey pattern for data reload after mutations in frontend/app/admin/organizations/page.tsx
- [x] T010 [P] [US1] Add 組織 navigation item to AdminNav component linking to /admin/organizations in frontend/app/components/admin/AdminNav.tsx

#### Tests for User Story 1

- [x] T011 [P] [US1] Create AdminOrganizationServiceTest with tests for: create org with valid/invalid input, code uniqueness rejection, hierarchy depth limit (6 levels), update name on active org, reject update on inactive org, deactivate with active children warning, activate inactive org, list with search/filter/pagination in backend/src/test/kotlin/com/worklog/application/service/AdminOrganizationServiceTest.kt
- [x] T012 [P] [US1] Create AdminOrganizationControllerTest with integration tests for: GET list (pagination, search, status filter), POST create (201 with id, 400 validation error, 409 code conflict, 400 max depth), PUT update (204, 404 not found, 400 inactive), PATCH deactivate (200 with warnings, 400 already inactive), PATCH activate (204, 400 already active) with @PreAuthorize verification in backend/src/test/kotlin/com/worklog/api/AdminOrganizationControllerTest.kt
- [x] T013 [P] [US1] Create OrganizationList.test.tsx (list rendering, search debounce, pagination, status filter, empty state, action button callbacks) and OrganizationForm.test.tsx (create/edit modes, code validation pattern, parent dropdown, error display, ESC close) in frontend/tests/unit/components/admin/

**Checkpoint**: Organization CRUD is fully functional and tested — create hierarchy, search/filter list, update names, deactivate/reactivate

---

## Phase 4: User Story 2 — Supervisor Assignment within Organizations (Priority: P2)

**Goal**: Tenant administrators can assign/change/remove managers for members, transfer members between organizations, and create new members within organizations — with full circular reference prevention

**Independent Test**: View organization members, assign a manager, verify circular chain rejection (A→B→C→A), remove manager, transfer member to another org (manager cleared), create new member

### Implementation for User Story 2

#### Backend

- [x] T014 [US2] Modify Member domain model — remove final modifier from organizationId, add changeOrganization(OrganizationId) method that updates organizationId, calls removeManager(), and updates timestamp in backend/src/main/java/com/worklog/domain/member/Member.java
- [x] T015 [P] [US2] Add wouldCreateCircularReference(MemberId memberId, MemberId proposedManagerId) method to JdbcMemberRepository using iterative chain traversal with visited set as documented in research.md Decision 3 in backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java
- [x] T016 [US2] Add member management operations to AdminOrganizationService: listMembersByOrganization (paginated, with manager name and manager active status join for FR-013), assignManager (validate active, self-assignment, circular reference), removeManager, transferMember (validate target org active, different org, calls member.changeOrganization) with corresponding command DTOs (AssignManagerCommand, TransferMemberCommand) in backend/src/main/java/com/worklog/application/
- [x] T017 [P] [US2] Add GET /api/v1/admin/organizations/{id}/members endpoint (paginated, filter by isActive, response includes managerIsActive field per FR-013) to AdminOrganizationController with @PreAuthorize("hasAuthority('organization.view')") in backend/src/main/java/com/worklog/api/AdminOrganizationController.java
- [x] T018 [P] [US2] Add PUT /api/v1/admin/members/{id}/manager (assign, 204), DELETE /api/v1/admin/members/{id}/manager (remove, 204), PUT /api/v1/admin/members/{id}/organization (transfer, 204) endpoints to existing AdminMemberController and enhance POST /api/v1/admin/members to validate managerId circular reference per contracts/member-manager-api.md in backend/src/main/java/com/worklog/api/AdminMemberController.java

#### Frontend

- [x] T019 [P] [US2] Add member management API client methods to frontend/app/services/api.ts: listOrganizationMembers(orgId, page, size, isActive), assignManager(memberId, managerId), removeManager(memberId), transferMember(memberId, organizationId)
- [x] T020 [P] [US2] Create MemberManagerForm modal component with member selector dropdown (active members in tenant), manager assignment confirmation, transfer organization selector, circular reference error display, and member creation form (email, displayName, optional manager) in frontend/app/components/admin/MemberManagerForm.tsx
- [x] T021 [US2] Add organization members detail view to organizations page — members table (name, email, manager name, status, inactive manager warning indicator per FR-013), assign/change manager button, remove manager button, transfer organization button, create member button — shown when an organization is selected in frontend/app/admin/organizations/page.tsx

#### Tests for User Story 2

- [x] T022 [P] [US2] Add manager operation tests to AdminOrganizationMemberControllerTest: circular reference detection (self-assignment, A→B→A, A→B→C→A at depth 3+), assign manager (204 + error cases), remove manager (204 + no manager error), transfer member (204 + same org error + inactive org error + clears manager), list members by org (pagination, managerIsActive field), inactive manager flagging (FR-013) in backend/src/test/kotlin/com/worklog/api/AdminOrganizationMemberControllerTest.kt
- [x] T023 [P] [US2] Create MemberManagerForm.test.tsx with tests for: manager selector dropdown rendering, assignment confirmation flow, circular reference error display, transfer org selector, member creation form validation (email format, required fields), disabled submit on invalid input in frontend/tests/unit/components/admin/MemberManagerForm.test.tsx

**Checkpoint**: Full supervisor assignment lifecycle works and tested — assign, change, remove managers; transfer members; create members; circular references blocked; inactive manager flagging visible

---

## Phase 5: User Story 3 — Organization Tree Visualization (Priority: P3)

**Goal**: Tenant administrators can view the organization hierarchy as an interactive collapsible tree with node selection showing details

**Independent Test**: Create a multi-level org hierarchy, switch to tree view, verify nesting/indentation, collapse/expand nodes, click node to see members and managers

### Implementation for User Story 3

#### Backend

- [x] T024 [US3] Add tree building logic to AdminOrganizationService (query all orgs for tenant, build recursive tree structure, include member counts) and GET /api/v1/admin/organizations/tree endpoint with optional includeInactive query param to AdminOrganizationController per contracts/organization-api.md

#### Frontend

- [x] T025 [P] [US3] Add organization tree API client method to frontend/app/services/api.ts: getOrganizationTree(includeInactive)
- [x] T026 [US3] Create OrganizationTree component with recursive TreeNode rendering, left padding by level (pl-{level*4}), expand/collapse chevron toggle, status badge (ACTIVE/INACTIVE), member count indicator, click handler to select organization in frontend/app/components/admin/OrganizationTree.tsx
- [x] T027 [US3] Add list/tree tab toggle (リスト表示 / ツリー表示) to organizations page — tree view shows OrganizationTree component, clicking a tree node shows organization details and members panel in frontend/app/admin/organizations/page.tsx

#### Tests for User Story 3

- [x] T028 [P] [US3] Create OrganizationTree.test.tsx with tests for: tree rendering with nested hierarchy (3+ levels), collapse/expand toggle interaction, node selection callback, status badge display (active/inactive), member count indicator, includeInactive toggle, empty tree state in frontend/tests/unit/components/admin/OrganizationTree.test.tsx

**Checkpoint**: Tree visualization is fully interactive and tested — hierarchy visible, collapse/expand works, node click shows details

---

## Phase 6: User Story 4 — Fiscal Year and Monthly Period Pattern Assignment (Priority: P4)

**Goal**: Tenant administrators can assign fiscal year patterns and monthly period patterns to organizations for correct work period calculation

**Independent Test**: Select an organization, assign a fiscal year pattern and monthly period pattern from dropdowns, verify patterns are displayed in organization details

### Implementation for User Story 4

#### Backend

- [x] T029 [US4] Add pattern assignment logic to AdminOrganizationService (validate patterns exist, call Organization.assignPatterns event) and PUT /api/v1/admin/organizations/{id}/patterns endpoint to AdminOrganizationController per contracts/organization-api.md

#### Frontend

- [x] T030 [P] [US4] Add pattern assignment API client method to frontend/app/services/api.ts: assignOrganizationPatterns(orgId, fiscalYearPatternId, monthlyPeriodPatternId) and listFiscalYearPatterns(), listMonthlyPeriodPatterns() if not already present
- [x] T031 [US4] Add pattern assignment form to organization detail view — fiscal year pattern dropdown, monthly period pattern dropdown, save button — shown in the organization edit or detail panel in frontend/app/admin/organizations/page.tsx

**Checkpoint**: Pattern assignment works — select patterns, save, verify displayed in org details

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Formatting, linting, performance validation, and end-to-end verification

- [x] T032 Run backend formatting and static analysis checks in backend/ (./gradlew formatAll && ./gradlew detekt) and fix any violations
- [x] T033 Run frontend formatting and linting checks in frontend/ (npm run check:ci) and fix any violations
- [ ] T034 Validate performance targets: seed 100+ organizations via data-dev.sql or test script, verify GET /api/v1/admin/organizations/tree responds under 2 seconds (SC-003), seed 500+ organizations and verify GET /api/v1/admin/organizations?search= responds under 1 second (SC-005), document results
- [ ] T035 Validate quickstart.md scenarios 1-6 against implementation — verify all described user flows work end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (migration must exist before projection sync)
- **User Stories (Phase 3+)**: All depend on Phase 2 completion (projection sync required for list queries)
  - User stories proceed sequentially in priority order (P1 → P2 → P3 → P4)
  - US2 extends files created in US1 (AdminOrganizationController, AdminOrganizationService, api.ts, page.tsx)
  - US3 extends files created in US1/US2 (same controller, service, api.ts, page.tsx)
  - US4 extends files created in US1/US3 (same controller, service, api.ts, page.tsx)
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — No dependencies on other stories
- **US2 (P2)**: Depends on US1 completion (extends AdminOrganizationController, AdminOrganizationService, page.tsx)
- **US3 (P3)**: Depends on US1 completion (extends same files); can run in parallel with US2 on backend but shares page.tsx
- **US4 (P4)**: Depends on US1 completion (extends same files); can run in parallel with US2/US3 on backend but shares page.tsx

### Within Each User Story

- Command DTOs before service (models before business logic)
- Service before controller (business logic before HTTP layer)
- Backend API client before frontend components (contract before consumer)
- Components before page (parts before assembly)
- Implementation before tests (tests validate completed implementation)

### Parallel Opportunities

**Phase 3 (US1)**:
- T003 (commands) + T006 (frontend api) + T007 (list) + T008 (form) + T010 (nav) can all run in parallel
- T004 (service) depends on T003
- T005 (controller) depends on T004
- T009 (page) depends on T007, T008
- T011 (service test) + T012 (controller test) + T013 (frontend tests) can run in parallel after implementation

**Phase 4 (US2)**:
- T014 (member model) + T015 (circular detection) can run in parallel
- T016 (service) depends on T014, T015
- T017 (org controller) + T018 (member controller) can run in parallel after T016
- T019 (frontend api) + T020 (modal) can run in parallel with backend tasks
- T021 (page update) depends on T019, T020
- T022 (backend tests) + T023 (frontend test) can run in parallel after implementation

**Phase 5 (US3)**:
- T025 (frontend api) can run in parallel with T024 (backend)
- T026 (tree component) depends on T025
- T027 (page update) depends on T026
- T028 (frontend test) after implementation

**Phase 6 (US4)**:
- T030 (frontend api) can run in parallel with T029 (backend)
- T031 (page update) depends on T030

---

## Parallel Example: User Story 1

```bash
# Launch parallel backend + frontend tasks after T002 completes:
Task T003: "Create command DTOs in backend/src/main/java/com/worklog/application/command/"
Task T006: "Add organization API client methods to frontend/app/services/api.ts"
Task T007: "Create OrganizationList component in frontend/app/components/admin/OrganizationList.tsx"
Task T008: "Create OrganizationForm modal in frontend/app/components/admin/OrganizationForm.tsx"
Task T010: "Add 組織 nav item to AdminNav in frontend/app/components/admin/AdminNav.tsx"

# Then sequentially:
Task T004: "Create AdminOrganizationService" (depends on T003)
Task T005: "Create AdminOrganizationController" (depends on T004)
Task T009: "Create organizations page" (depends on T007, T008)

# Then parallel tests:
Task T011: "AdminOrganizationServiceTest"
Task T012: "AdminOrganizationControllerTest"
Task T013: "OrganizationList.test.tsx + OrganizationForm.test.tsx"
```

## Parallel Example: User Story 2

```bash
# Launch parallel domain changes:
Task T014: "Modify Member.java — make organizationId mutable"
Task T015: "Add circular reference detection to JdbcMemberRepository"
Task T019: "Add member management API client methods to frontend/app/services/api.ts"
Task T020: "Create MemberManagerForm modal in frontend/app/components/admin/MemberManagerForm.tsx"

# Then sequentially:
Task T016: "Add member management operations to AdminOrganizationService" (depends on T014, T015)
Task T017: "Add GET /organizations/{id}/members to AdminOrganizationController" (depends on T016)
Task T018: "Add manager/transfer endpoints to AdminMemberController" (depends on T016)
Task T021: "Add members detail view to organizations page" (depends on T019, T020)

# Then parallel tests:
Task T022: "Backend manager operation tests"
Task T023: "MemberManagerForm.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (V19 migration)
2. Complete Phase 2: Foundational (projection sync)
3. Complete Phase 3: User Story 1 (Organization CRUD + tests)
4. **STOP and VALIDATE**: Create org hierarchy, search/filter, deactivate/reactivate, run tests
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Projection sync ready
2. Add US1 → Organization CRUD works + tests pass → Deploy/Demo (MVP!)
3. Add US2 → Supervisor assignment + member transfer + tests pass → Deploy/Demo
4. Add US3 → Tree visualization + tests pass → Deploy/Demo
5. Add US4 → Pattern assignment → Deploy/Demo
6. Polish → Format, lint, performance validation, quickstart verification

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- US2-US4 extend files created in US1 — sequential story execution recommended
- Organization is event-sourced (events + projection); Member is direct JDBC
- Circular reference detection uses iterative chain traversal (research.md Decision 3)
- Frontend follows existing admin panel patterns: list/form/modal, debounced search, refreshKey, ESC close
- All endpoints require @PreAuthorize with corresponding permission annotations
- FR-013 (inactive manager flagging): managerIsActive field in member list response + yellow warning indicator in UI
- Tests follow Test Pyramid: unit tests for domain logic, integration tests for controllers, component tests for frontend
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
