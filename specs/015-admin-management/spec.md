# Feature Specification: Admin Management

**Feature Branch**: `015-admin-management`
**Created**: 2026-02-20
**Status**: Draft
**Input**: User description: "管理機能を作成したい。システム管理者のテナント管理とユーザー管理、テナント管理者のユーザー管理とプロジェクト管理、ユーザーとプロジェクトのアサイン管理。組織の上司のユーザーとプロジェクトのアサイン管理、日次記録の承認機能、月次記録の承認機能"

## Clarifications

### Session 2026-02-20

- Q: Daily approval and monthly approval relationship — is daily approval a prerequisite for monthly submission? → A: Daily approval is optional. Members can submit monthly records even if daily entries have not been individually approved, but the system displays warnings for unapproved or rejected entries during monthly review.
- Q: When do work log entries become available for daily approval by the supervisor? → A: Saved entries are automatically visible on the supervisor's dashboard without requiring an explicit submission action from the member.
- Q: Can a tenant have multiple Tenant Admins, and who assigns the Tenant Admin role? → A: Multiple Tenant Admins per tenant are allowed. Both System Admins and existing Tenant Admins can assign the Tenant Admin role to other members within the same tenant.
- Q: Can a supervisor recall (revoke) a daily entry approval after it has been granted? → A: Recall is allowed only before the monthly record is approved. Once the monthly record is approved, daily approvals become permanent and cannot be recalled.
- Q: Should the system prevent demotion of the last Tenant Admin in a tenant? → A: Yes. The system must maintain at least one Tenant Admin per tenant at all times. The last remaining Tenant Admin cannot be demoted or have their role changed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tenant Admin Manages Users Within Their Tenant (Priority: P1)

A tenant administrator needs to manage the users (members) belonging to their tenant. They can view a list of all members, invite new members, edit member details (display name, email, organization assignment, manager assignment), and deactivate members who have left the organization. This is the most fundamental admin capability, as without user management no other admin functions are meaningful.

**Why this priority**: User management is the foundation of all other administrative operations. Projects, assignments, and approvals all depend on having properly managed user accounts within a tenant.

**Independent Test**: Can be fully tested by logging in as a tenant admin, navigating to user management, creating a new member, editing their details, and deactivating them. Delivers value by enabling tenant-level user lifecycle management.

**Acceptance Scenarios**:

1. **Given** a tenant admin is logged in, **When** they navigate to the user management screen, **Then** they see a list of all members in their tenant with name, email, organization, status, and manager information.
2. **Given** a tenant admin is on the user management screen, **When** they invite a new member by providing email, display name, organization, and optional manager, **Then** the member is created in the system and associated with the tenant.
3. **Given** a tenant admin is viewing a member's details, **When** they edit the member's display name, email, organization, or manager assignment, **Then** the changes are saved and reflected in the member list.
4. **Given** a tenant admin is viewing an active member, **When** they deactivate the member, **Then** the member's status changes to inactive, they can no longer log time, and their existing records remain intact.
5. **Given** a tenant admin is viewing an inactive member, **When** they reactivate the member, **Then** the member regains access and can resume logging time.

---

### User Story 2 - Tenant Admin Manages Projects (Priority: P2)

A tenant administrator needs to manage projects within their tenant. They can view all projects, create new projects with a code and name, set validity periods (start/end dates), and activate or deactivate projects. Projects define the work categories that members can log time against.

**Why this priority**: Project management is essential for time entry. Members need assigned projects to log their work, making this a prerequisite for meaningful time tracking.

**Independent Test**: Can be fully tested by logging in as a tenant admin, creating a new project with code, name, and validity period, then verifying it appears in the project list. Delivers value by enabling project lifecycle management.

**Acceptance Scenarios**:

1. **Given** a tenant admin is logged in, **When** they navigate to the project management screen, **Then** they see a list of all projects in their tenant with code, name, status, and validity period.
2. **Given** a tenant admin is on the project management screen, **When** they create a new project with code, name, and optional validity dates, **Then** the project is created and available for member assignment.
3. **Given** a tenant admin is viewing a project, **When** they edit the project name or validity period, **Then** the changes are saved.
4. **Given** a tenant admin is viewing an active project, **When** they deactivate the project, **Then** members can no longer log new time against it, but existing entries remain.
5. **Given** a tenant admin creates a project with a code that already exists in the tenant, **When** they try to save, **Then** a validation error is shown explaining the code must be unique.

---

### User Story 3 - Tenant Admin and Supervisor Manage User-Project Assignments (Priority: P3)

A tenant administrator can assign any member to any project within the tenant. An organization supervisor (manager) can assign their direct reports to projects. Assignments control which projects a member can log time against. Assignments can be activated or deactivated without being deleted.

**Why this priority**: Assignments connect users to projects and are required before members can log time. This bridges user management (P1) and project management (P2).

**Independent Test**: Can be fully tested by logging in as a tenant admin, selecting a member, assigning them to a project, then verifying the member can see that project in their time entry form. Delivers value by enabling granular access control for time logging.

**Acceptance Scenarios**:

1. **Given** a tenant admin is viewing a member's profile, **When** they assign the member to a project, **Then** the assignment is created and the member can log time against that project.
2. **Given** a tenant admin is viewing a member's assignments, **When** they deactivate an assignment, **Then** the member can no longer log new time against that project, but existing entries remain.
3. **Given** an organization supervisor is logged in, **When** they view their team members, **Then** they see only their direct reports and can manage project assignments for those members.
4. **Given** a supervisor tries to assign a member who is not their direct report, **When** they attempt to save, **Then** the system prevents the action and shows an error.
5. **Given** a tenant admin is viewing a project, **When** they view the project's assigned members, **Then** they see all members assigned to that project with assignment status.

---

### User Story 4 - Supervisor Approves Daily Work Log Entries (Priority: P4)

An organization supervisor (manager) reviews and approves their direct reports' daily work log entries. Saved entries are automatically visible on the supervisor's daily approval dashboard without requiring an explicit submission action from the member. The supervisor can review individual entries for a given day, approve or reject entries with optional comments. Rejected entries are returned to the member for correction.

**Why this priority**: Daily approval provides granular oversight of time entries. It enables early correction of errors rather than waiting until monthly submission, improving data quality.

**Independent Test**: Can be fully tested by having a member submit work log entries for a day, then logging in as their supervisor, reviewing the entries, and approving or rejecting them with comments. Delivers value by enabling timely, entry-level quality control.

**Acceptance Scenarios**:

1. **Given** a supervisor is logged in, **When** they navigate to the daily approval dashboard, **Then** they see a list of their direct reports with pending entries grouped by date.
2. **Given** a supervisor selects a team member and a date, **When** they view the entries for that day, **Then** they see all work log entries with project, hours, and description.
3. **Given** a supervisor is reviewing a daily entry, **When** they approve the entry, **Then** the entry status changes to approved and the member is notified.
4. **Given** a supervisor is reviewing a daily entry, **When** they reject the entry with a comment, **Then** the entry status changes to rejected, the member is notified, and they can edit and resubmit.
5. **Given** a supervisor has multiple pending entries, **When** they select several entries and approve them in bulk, **Then** all selected entries are approved at once.
6. **Given** an entry has already been approved, **When** the supervisor views it, **Then** it is displayed as read-only and cannot be modified.

---

### User Story 5 - Supervisor Approves Monthly Records (Priority: P5)

An organization supervisor reviews and approves their direct reports' monthly time records. When a member submits their monthly record for approval, the supervisor receives a notification and can review the complete month's entries, total hours, and project breakdown. The supervisor can approve or reject the entire monthly submission.

**Why this priority**: Monthly approval is the formal sign-off process for payroll and billing. It builds on the daily approval workflow (P4) and ensures complete monthly records are validated.

**Independent Test**: Can be fully tested by having a member submit their monthly record, then logging in as their supervisor, reviewing the summary, and approving or rejecting with a reason. Delivers value by enabling formal monthly approval workflows.

**Acceptance Scenarios**:

1. **Given** a member has submitted their monthly record, **When** the supervisor navigates to the monthly approval screen, **Then** they see a list of submitted monthly records from their direct reports.
2. **Given** a supervisor selects a submitted monthly record, **When** they view the details, **Then** they see a summary including total hours, project breakdown, daily entries, and any absences.
3. **Given** a supervisor is reviewing a monthly record, **When** they approve it, **Then** the record status changes to approved, it becomes permanently read-only, and the member is notified.
4. **Given** a supervisor is reviewing a monthly record, **When** they reject it with a reason, **Then** the record returns to the member for correction, the rejection reason is visible, and the member can edit and resubmit.
5. **Given** a monthly record contains entries that were rejected at the daily level but not corrected, **When** the supervisor reviews the monthly record, **Then** they see warnings highlighting unresolved daily rejections.

---

### User Story 6 - System Admin Manages Tenants (Priority: P6)

A system administrator manages all tenants in the system. They can view a list of tenants, create new tenants with a unique code and name, edit tenant names, and activate or deactivate tenants. Deactivating a tenant prevents all users within that tenant from accessing the system.

**Why this priority**: Tenant management is a system-level operation needed for onboarding new organizations. It is lower priority than tenant-internal operations because the system already has tenants set up, and this is an infrequent operation.

**Independent Test**: Can be fully tested by logging in as a system admin, creating a new tenant, verifying it appears in the list, then deactivating it and confirming its users lose access. Delivers value by enabling multi-tenant lifecycle management.

**Acceptance Scenarios**:

1. **Given** a system admin is logged in, **When** they navigate to the tenant management screen, **Then** they see a list of all tenants with code, name, and status.
2. **Given** a system admin is on the tenant management screen, **When** they create a new tenant with a unique code and name, **Then** the tenant is created in active status.
3. **Given** a system admin is viewing a tenant, **When** they edit the tenant name, **Then** the name is updated.
4. **Given** a system admin is viewing an active tenant, **When** they deactivate the tenant, **Then** all members within the tenant lose access and cannot log time or access the system.
5. **Given** a system admin creates a tenant with a code that already exists, **When** they try to save, **Then** a validation error is shown explaining the code must be unique.

---

### User Story 7 - System Admin Manages Users Globally (Priority: P7)

A system administrator can manage all user accounts across all tenants. They can view all users, assign roles, lock or unlock accounts, and initiate password resets. This provides system-wide oversight for security and support operations.

**Why this priority**: Global user management is a support and security function. It complements tenant-level management (P1) but is less frequently used since most user management is done at the tenant level.

**Independent Test**: Can be fully tested by logging in as a system admin, viewing all users across tenants, locking an account, and verifying that user can no longer log in. Delivers value by enabling system-wide user administration and security response.

**Acceptance Scenarios**:

1. **Given** a system admin is logged in, **When** they navigate to the global user management screen, **Then** they see a list of all user accounts across all tenants with email, name, role, tenant, and account status.
2. **Given** a system admin is viewing a user account, **When** they change the user's role, **Then** the role is updated and the user's permissions change accordingly.
3. **Given** a system admin is viewing a locked user account, **When** they unlock the account, **Then** the user can log in again.
4. **Given** a system admin is viewing a user account, **When** they initiate a password reset, **Then** a password reset email is sent to the user.
5. **Given** a system admin is searching for a user, **When** they filter by tenant, role, or account status, **Then** the list is filtered to show matching users only.

---

### Edge Cases

- What happens when a tenant admin deactivates a member who has pending (unsubmitted) work log entries? The entries remain as drafts associated with the member, but cannot be submitted until the member is reactivated.
- What happens when a supervisor is deactivated or their role changes? Their direct reports' pending approvals are reassigned to the next manager in the organizational hierarchy. If no manager exists, a tenant admin must manually reassign.
- What happens when a project is deactivated while members have in-progress entries against it? Existing entries remain valid, but no new entries can be created for that project.
- How does the system handle a member who belongs to multiple organizations? A member belongs to exactly one organization at a time. Reassignment is done via the tenant admin editing the member's organization.
- What happens when a system admin deactivates a tenant that has pending monthly approvals? All pending approvals are frozen in their current state. When the tenant is reactivated, the approval workflow resumes from where it was paused.
- What happens when a supervisor approves a daily entry but then rejects the monthly record? Individual daily approvals remain, but the monthly rejection returns all entries to editable state. The member can modify entries and resubmit the entire month.
- What happens when a Tenant Admin tries to demote the last remaining Tenant Admin? The system prevents the action and displays an error explaining that at least one Tenant Admin must exist per tenant.
- What happens when a supervisor recalls a daily approval after the member has already submitted their monthly record? The monthly submission remains in submitted state, but the recalled entry is flagged as unapproved. The supervisor reviewing the monthly record sees a warning about the recalled entry.

## Requirements *(mandatory)*

### Functional Requirements

#### Role & Permission Management

- **FR-001**: System MUST enforce three administrative role levels: System Administrator (global), Tenant Administrator (tenant-scoped), and Organization Supervisor (organization-scoped).
- **FR-002**: System MUST restrict each role's actions to their scope — System Admin can act across all tenants, Tenant Admin only within their assigned tenant, and Supervisors only over their direct reports.
- **FR-003**: System MUST prevent users from performing actions beyond their assigned role's permissions.
- **FR-004**: System MUST allow multiple Tenant Admins per tenant. Both System Admins and existing Tenant Admins can assign the Tenant Admin role to other members within the same tenant.
- **FR-005**: System MUST prevent demotion or removal of the last remaining Tenant Admin in a tenant. At least one Tenant Admin must exist per active tenant at all times.

#### Tenant Management (System Admin)

- **FR-010**: System MUST allow System Admins to view, create, edit, and deactivate tenants.
- **FR-011**: System MUST enforce unique tenant codes across the system.
- **FR-012**: System MUST prevent all member access within a deactivated tenant.

#### User Management (System Admin - Global)

- **FR-020**: System MUST allow System Admins to view all user accounts across all tenants.
- **FR-021**: System MUST allow System Admins to change user roles, lock/unlock accounts, and initiate password resets.
- **FR-022**: System MUST allow System Admins to filter users by tenant, role, and account status.

#### User Management (Tenant Admin - Tenant-scoped)

- **FR-030**: System MUST allow Tenant Admins to view all members within their tenant.
- **FR-031**: System MUST allow Tenant Admins to invite new members by specifying email, display name, organization, and optional manager.
- **FR-032**: System MUST allow Tenant Admins to edit member details including display name, email, organization assignment, and manager assignment.
- **FR-033**: System MUST allow Tenant Admins to deactivate and reactivate members.
- **FR-034**: System MUST preserve all historical records (work logs, absences) when a member is deactivated.

#### Project Management (Tenant Admin)

- **FR-040**: System MUST allow Tenant Admins to view, create, edit, and deactivate projects within their tenant.
- **FR-041**: System MUST enforce unique project codes within a tenant.
- **FR-042**: System MUST allow Tenant Admins to set optional validity periods (start and end dates) for projects.
- **FR-043**: System MUST prevent new time entries against deactivated or expired projects.

#### Assignment Management (Tenant Admin & Supervisor)

- **FR-050**: System MUST allow Tenant Admins to assign any member to any project within the tenant.
- **FR-051**: System MUST allow Organization Supervisors to assign their direct reports to projects.
- **FR-052**: System MUST prevent Supervisors from managing assignments for members outside their direct reports.
- **FR-053**: System MUST allow deactivation and reactivation of assignments without deletion.
- **FR-054**: Members MUST only be able to log time against projects they are actively assigned to.

#### Daily Work Log Approval (Supervisor)

- **FR-060**: System MUST automatically display saved work log entries from direct reports on the Supervisor's daily approval dashboard, grouped by date, without requiring an explicit submission action from the member.
- **FR-061**: System MUST allow Supervisors to approve or reject individual work log entries with optional comments.
- **FR-062**: System MUST allow Supervisors to approve multiple entries in bulk.
- **FR-063**: System MUST notify members when their entries are approved or rejected.
- **FR-064**: Rejected entries MUST be returned to the member for correction and resubmission.
- **FR-065**: Approved entries MUST become read-only and cannot be modified without recall by the approver.
- **FR-066**: Supervisors MUST be able to recall (revoke) a daily entry approval only if the corresponding monthly record has not yet been approved. Once the monthly record is approved, daily approvals become permanent.
- **FR-067**: When a daily approval is recalled, the entry returns to unapproved state and becomes editable by the member.

#### Monthly Record Approval (Supervisor)

- **FR-070**: System MUST allow Supervisors to view submitted monthly records from their direct reports.
- **FR-071**: System MUST display a monthly summary including total hours, project breakdown, and absence information.
- **FR-072**: System MUST allow Supervisors to approve or reject an entire monthly submission.
- **FR-073**: Rejection MUST require a reason (up to 1000 characters) and return the record to the member for correction.
- **FR-074**: Approved monthly records MUST become permanently read-only.
- **FR-075**: System MUST highlight unresolved daily rejections within a monthly record being reviewed.

#### Navigation & Access

- **FR-080**: System MUST provide a dedicated admin section accessible only to users with admin roles.
- **FR-081**: System MUST show admin menu items based on the logged-in user's role (System Admin sees tenant management, Tenant Admin sees user/project management, Supervisor sees approval dashboards).

### Key Entities

- **Admin Role**: Defines the level of administrative access (System Admin, Tenant Admin, Supervisor). Each role maps to a set of permissions controlling what actions the user can perform and within what scope.
- **Tenant**: Top-level organizational boundary. Has a unique code and name. Can be active or inactive. All members, projects, and records exist within a tenant.
- **Member**: An individual within a tenant who logs time. Belongs to one organization, may have a manager (supervisor). Can be active or inactive.
- **Project**: A work category within a tenant that members log time against. Has a unique code within the tenant, optional validity period, and active/inactive status.
- **Member-Project Assignment**: Links a member to a project, controlling which projects a member can log time against. Can be activated or deactivated.
- **Daily Entry Approval**: A supervisor's decision (approve/reject with comment) on an individual work log entry for a specific day.
- **Monthly Approval**: A supervisor's decision (approve/reject with reason) on a member's entire monthly time record submission.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tenant admins can complete the full user lifecycle (invite, edit, deactivate) within 3 minutes per member.
- **SC-002**: Tenant admins can create a new project and assign members to it within 2 minutes.
- **SC-003**: Supervisors can review and approve a team member's daily entries in under 1 minute per person per day.
- **SC-004**: Supervisors can review and approve a monthly record in under 3 minutes.
- **SC-005**: 95% of admin actions provide immediate visual feedback (success/failure) without requiring page refresh.
- **SC-006**: All admin actions are restricted to the actor's scope — no user can access data or perform actions outside their authorized tenant/organization boundary.
- **SC-007**: Members are notified of approval decisions (daily and monthly) within 10 seconds of the supervisor's action.
- **SC-008**: System admins can find and act on any user account within 30 seconds using search and filtering.
- **SC-009**: When a tenant is deactivated, all members within it are blocked from system access immediately.
- **SC-010**: All approval and management actions are recorded in the audit trail for compliance review.

## Assumptions

- The system already has role and permission infrastructure (roles, permissions, and role_permissions tables). This feature extends it with predefined admin roles and specific permissions.
- Members belong to exactly one organization at a time. Organization reassignment is managed by the tenant admin.
- "Direct reports" refers to members whose manager field points to the supervisor's member record. The supervisor hierarchy is single-level (a supervisor manages only their immediate direct reports, not transitive subordinates).
- Daily entry approval is a new concept that operates at the individual work log entry level. It is optional and not a prerequisite for monthly submission. Members can submit monthly records with unapproved daily entries, but the system warns the supervisor about unapproved or rejected entries during monthly review.
- Notifications for approval actions (FR-063, SC-007) are delivered as in-app notifications. Email notifications are out of scope for this feature.
- Bulk operations (FR-062) apply to the currently visible/selected entries within a single day view, not cross-day bulk actions.
- The admin section is a new navigation area within the existing application, not a separate application.

## Dependencies

- Existing authentication system (login, session management) must be functional.
- Existing role/permission model (roles, permissions, role_permissions tables) must be in place.
- Existing member, project, and member-project assignment domain models must be functional.
- Existing monthly approval workflow domain model must be functional.

## Out of Scope

- Email-based notifications (only in-app notifications are included).
- Organization hierarchy management (creating/editing organizations) — this is a separate feature.
- Audit log dashboard for viewing historical admin actions.
- Self-service role requests (users requesting admin access).
- Delegation of approval authority (supervisor assigning a temporary proxy approver).
- Cross-tenant operations (a tenant admin acting on another tenant's data).
