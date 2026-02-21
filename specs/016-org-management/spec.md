# Feature Specification: Organization Management

**Feature Branch**: `016-org-management`
**Created**: 2026-02-21
**Status**: Draft
**Input**: User description: "テナント管理者に、「supervisorがアサイン管理・承認を行う」組織を管理する機能を作りたい"

## Clarifications

### Session 2026-02-21

- Q: FR-014 references "moving a member to a different organization" but the current Member domain model has `organizationId` as immutable (final). Should we support organization transfer? → A: Yes, make `organizationId` mutable and include member organization transfer in this scope. This requires a domain model change to the Member aggregate.
- Q: FR-008 shows only depth-2 circular detection (A→B→A). Should the system detect longer transitive circular chains (A→B→C→A)? → A: Yes, detect all transitive circular chains at any depth (full cycle detection through the entire manager chain).
- Q: Can a member be assigned as their own manager (self-assignment)? → A: No, self-assignment is prohibited. It is the simplest form of circular reference and must be explicitly prevented.
- Q: What is explicitly out of scope for this feature? → A: Member creation is in scope (needed to assign members to organizations). Out of scope: permanent deletion of organizations (deactivation only), member deletion, and bulk import/export of organizations or members.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Organization CRUD Management (Priority: P1)

As a tenant administrator, I want to create, view, update, and deactivate organizations within my tenant so that I can establish the hierarchical structure where supervisors manage their teams.

An organization represents a department or team unit. Each organization belongs to a tenant, can have a parent organization (forming a tree hierarchy up to 6 levels deep), and contains members who report to a supervisor (manager).

**Why this priority**: Without organizations, there is no structural foundation for assigning supervisors, members, or managing approvals. This is the prerequisite for all other stories.

**Independent Test**: Can be fully tested by creating, viewing, editing, and deactivating organizations in the admin panel. The org tree structure is visible and manageable.

**Acceptance Scenarios**:

1. **Given** a tenant administrator is logged in, **When** they navigate to the organization management page, **Then** they see a list of all organizations in their tenant with name, code, level, parent, and status.
2. **Given** a tenant administrator is on the organization management page, **When** they create a new organization with a code, name, and optional parent, **Then** the organization appears in the list with ACTIVE status and the correct hierarchy level.
3. **Given** an organization exists, **When** the tenant administrator updates its name, **Then** the change is reflected immediately in the list.
4. **Given** an active organization exists, **When** the tenant administrator deactivates it, **Then** its status changes to INACTIVE and its members can no longer submit new work logs under that organization.
5. **Given** an inactive organization exists, **When** the tenant administrator reactivates it, **Then** its status returns to ACTIVE.
6. **Given** the organization list is displayed, **When** the administrator searches or filters by name/code/status, **Then** only matching organizations are shown.

---

### User Story 2 - Supervisor Assignment within Organizations (Priority: P2)

As a tenant administrator, I want to assign a supervisor (manager) to members within an organization so that the supervisor can manage project assignments and approve time entries for their subordinates.

The supervisor is a member within the same tenant who is designated as the manager of other members. When a member's manager is set, that manager becomes the supervisor responsible for approving the member's daily and monthly time entries.

**Why this priority**: Assigning supervisors is the core purpose of the feature. Without supervisor relationships, approval workflows cannot function correctly.

**Independent Test**: Can be tested by selecting a member and assigning/changing their manager via the organization management UI. The manager relationship is visible in the member list.

**Acceptance Scenarios**:

1. **Given** a tenant administrator views an organization's member list, **When** they select a member, **Then** they can see the member's current manager (supervisor) assignment.
2. **Given** a member has no manager assigned, **When** the administrator assigns a manager from the same tenant, **Then** the member's manager is updated and the supervisor can now approve the member's entries.
3. **Given** a member already has a manager, **When** the administrator changes the manager to a different member, **Then** the new manager takes over approval responsibilities.
4. **Given** a member has a manager assigned, **When** the administrator removes the manager assignment, **Then** the member has no supervisor and their entries require a manager to be assigned before approval.
5. **Given** the administrator tries to assign a manager, **When** the selected manager would create a circular reporting chain (A manages B, B manages A), **Then** the system rejects the assignment with a clear error message.
6. **Given** a member belongs to organization A, **When** the administrator transfers the member to organization B, **Then** the member's organization is updated to B and the member's manager assignment is cleared.
7. **Given** the administrator is viewing an organization's member list, **When** they create a new member with email, display name, and optional manager, **Then** the member appears in the organization's member list with ACTIVE status.

---

### User Story 3 - Organization Tree Visualization (Priority: P3)

As a tenant administrator, I want to see the organization hierarchy as a tree structure so that I can understand the reporting relationships and organizational levels at a glance.

**Why this priority**: While functional management works with a flat list, a tree view greatly improves usability for tenants with complex organizational structures.

**Independent Test**: Can be tested by creating a multi-level org hierarchy and viewing it in tree format. Parent-child relationships are visually clear.

**Acceptance Scenarios**:

1. **Given** multiple organizations exist with parent-child relationships, **When** the administrator views the organization tree, **Then** organizations are displayed in a hierarchical tree with proper indentation/nesting.
2. **Given** the tree view is displayed, **When** the administrator clicks on an organization node, **Then** they see the organization's details including its members and their supervisor assignments.
3. **Given** the tree has multiple levels, **When** the administrator collapses a parent node, **Then** all child organizations are hidden; expanding reveals them again.

---

### User Story 4 - Fiscal Year and Monthly Period Pattern Assignment (Priority: P4)

As a tenant administrator, I want to assign fiscal year patterns and monthly period patterns to each organization so that the system calculates work periods correctly for that organization's members.

**Why this priority**: Pattern assignment is important for correct date calculations but is a configuration step that can be done after the core org structure is in place.

**Independent Test**: Can be tested by assigning a fiscal year pattern (e.g., 21st-20th month cycle) to an organization and verifying that the organization's members see correct period boundaries.

**Acceptance Scenarios**:

1. **Given** an active organization exists, **When** the administrator assigns a fiscal year pattern and monthly period pattern, **Then** the organization's date calculations reflect those patterns.
2. **Given** an organization has patterns assigned, **When** the administrator changes the patterns, **Then** the new patterns take effect for future period calculations.
3. **Given** an organization has no patterns assigned, **When** members view their worklog, **Then** the system uses the tenant's default patterns.

---

### Edge Cases

- What happens when a parent organization is deactivated while it has active child organizations? The system should warn the administrator but allow deactivation; child organizations remain active.
- What happens when a supervisor (manager) is deactivated or removed from the organization? Members managed by that supervisor should be flagged as having no active manager.
- What happens when an organization's code is duplicated within the same tenant? The system must reject the creation with a clear error.
- How does the system handle moving a member from one organization to another? The member's organization changes but their manager assignment is cleared (since the manager may not be in the new organization).
- What happens when the maximum hierarchy depth (6 levels) is exceeded? The system rejects the creation with a message explaining the depth limit.
- What happens when an administrator tries to assign a member as their own manager? The system rejects the assignment with a clear error message (self-assignment is the simplest circular reference).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow tenant administrators to create organizations with a unique code, name, and optional parent organization.
- **FR-002**: System MUST enforce the organization hierarchy depth limit of 6 levels.
- **FR-003**: System MUST allow tenant administrators to update an organization's name.
- **FR-004**: System MUST allow tenant administrators to deactivate and reactivate organizations.
- **FR-005**: System MUST display a paginated, searchable list of organizations with filtering by status.
- **FR-006**: System MUST allow tenant administrators to view members within an organization.
- **FR-007**: System MUST allow tenant administrators to assign or change a member's manager (supervisor) to any active member within the same tenant.
- **FR-008**: System MUST prevent circular manager relationships by detecting all transitive circular chains at any depth (e.g., A→B→A, A→B→C→A, or longer chains), including self-assignment (a member cannot be their own manager). The system traverses the full manager chain before allowing assignment.
- **FR-009**: System MUST display the organization hierarchy as a tree structure.
- **FR-010**: System MUST allow tenant administrators to assign fiscal year and monthly period patterns to organizations.
- **FR-011**: System MUST validate that organization codes are unique within a tenant.
- **FR-012**: System MUST warn when deactivating a parent organization that has active children.
- **FR-013**: System MUST display a visual warning indicator in the members list for members whose assigned manager is inactive. The member list API response MUST include the manager's active status to enable this indicator.
- **FR-014**: System MUST clear a member's manager assignment when the member is moved to a different organization.
- **FR-015**: System MUST allow tenant administrators to transfer a member from one organization to another within the same tenant. This requires changing the Member domain model to make `organizationId` mutable.
- **FR-016**: System MUST allow tenant administrators to create new members within an organization, specifying email, display name, and optional manager.

### Key Entities

- **Organization**: Represents a department or team unit within a tenant. Has a code (immutable, unique per tenant), name, level (1-6), status (ACTIVE/INACTIVE), optional parent organization, and optional fiscal/monthly period pattern references. Forms a hierarchical tree structure.
- **Member**: Represents an individual within an organization. Has an email, display name, organization reference (mutable, allowing transfers between organizations), and optional manager (supervisor) reference to another member.
- **Supervisor (Manager)**: Not a separate entity but a role relationship — a member who is designated as the manager of other members. Responsible for project assignment management, daily entry approval, and monthly approval workflows.

## Assumptions

- Organization codes cannot be changed after creation (immutable, used for event sourcing identification).
- A member can have at most one manager at any given time.
- Supervisors can manage members across different organizations within the same tenant (the manager does not need to be in the same organization as the subordinate).
- The existing approval workflow (daily and monthly) already uses the member's manager relationship. This feature formalizes the UI for managing these relationships.
- Tenant administrators have the `tenant_admin` role with permissions to manage organizations and member assignments.
- The organization management UI is accessible from the admin panel under a new "組織" (Organization) tab.
- **Terminology**: "manager" is the canonical code/API term for the supervisor relationship. Japanese UI uses "マネージャー". "Supervisor" is used only in business-context descriptions and is synonymous with "manager" throughout this specification.

## Out of Scope

- Permanent deletion of organizations (deactivation/reactivation only).
- Deletion of members (deactivation via existing member management).
- Bulk import/export of organizations or members.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tenant administrators can create a new organization in under 1 minute.
- **SC-002**: Tenant administrators can assign a supervisor to a member in under 30 seconds.
- **SC-003**: The organization tree view loads and displays up to 100 organizations within 2 seconds.
- **SC-004**: 100% of circular manager relationships are detected and prevented before saving.
- **SC-005**: Organization search and filtering returns results within 1 second for tenants with up to 500 organizations.
- **SC-006**: All organization CRUD operations are reflected immediately in the UI without requiring a page refresh.
