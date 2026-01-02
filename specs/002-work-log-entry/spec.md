# Feature Specification: Work-Log Entry System

**Feature Branch**: `002-work-log-entry`  
**Created**: 2026-01-02  
**Status**: Draft  
**Input**: User description: "specs/002-work-log-entry を読んで仕様定義して"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Daily Time Entry (Priority: P1)

Engineers need to record how they spend their work hours on a daily basis, tracking time spent on different projects with precision. This forms the foundation of the entire work-log system and enables accurate project billing, resource allocation, and workload analysis.

**Why this priority**: Without the ability to enter daily time records, no other features can function. This is the core value proposition - capturing accurate time data.

**Independent Test**: An engineer can log into the system, select a specific date, enter 8 hours distributed across 2-3 projects in 15-minute increments, save the entries, and see them reflected in a monthly calendar view. The total hours for that day equals 8 hours.

**Acceptance Scenarios**:

1. **Given** an engineer views the current month calendar, **When** they click on any date, **Then** a detailed entry form opens showing that specific date
2. **Given** the entry form is open, **When** they select a project and enter time in 15-minute increments (0.25h, 0.5h, 0.75h, etc.), **Then** the system accepts the entry
3. **Given** multiple projects are assigned to a single day, **When** the total hours exceed 24 hours, **Then** the system displays a validation warning and prevents saving
4. **Given** entries are saved for a date, **When** the user returns to the calendar view, **Then** that date displays the total hours worked
5. **Given** time entries exist, **When** the user reopens the entry form for that date, **Then** all previously entered data is displayed and editable

---

### User Story 2 - Multi-Project Time Allocation (Priority: P1)

Engineers frequently work on multiple projects throughout a single day and need to accurately record how their time was divided between these different activities. This ensures each project is billed correctly and resource utilization is tracked accurately.

**Why this priority**: Real work patterns involve context switching between projects. Without multi-project support, time tracking would be inaccurate and frustrate users.

**Independent Test**: An engineer can enter a single day's work showing 4.5 hours on Project A, 2.5 hours on Project B, and 1 hour on internal tasks, save all three entries simultaneously, and verify the calendar shows 8 total hours for that day.

**Acceptance Scenarios**:

1. **Given** an entry form is open for a specific date, **When** the user adds multiple project entries with their respective hours, **Then** the system displays the running total of hours
2. **Given** time is distributed across multiple projects, **When** the combined total is less than or equal to 24 hours, **Then** the system allows saving
3. **Given** multiple projects are entered, **When** the user wants to remove one project, **Then** the system removes only that entry and updates the total
4. **Given** saved multi-project entries, **When** viewed in the monthly summary, **Then** each project shows its accumulated hours across all days

---

### User Story 3 - Absence Recording (Priority: P1)

Engineers need to record vacation days, sick leave, and special leave separate from project work hours. This information is essential for HR tracking, compliance, and understanding why certain days have no project hours recorded.

**Why this priority**: Leave tracking is a legal requirement in most jurisdictions and essential for payroll processing. It must be independent from work hours to avoid confusion.

**Independent Test**: An engineer can mark a date as "Paid Leave (8 hours)", save it, and verify that date shows as leave in the calendar with no expectation of project hours being entered.

**Acceptance Scenarios**:

1. **Given** an entry form is open, **When** the user selects an absence type (paid leave, sick leave, special leave) and enters hours, **Then** the system records it separately from project work
2. **Given** a full day (8 hours) is marked as absence, **When** viewing the calendar, **Then** that date is visually distinguished as an absence day
3. **Given** partial absence is recorded (4 hours), **When** the user also enters 4 hours of project work, **Then** the system accepts both totaling 8 hours
4. **Given** absence is recorded, **When** generating monthly summaries, **Then** absence hours are calculated separately from project hours

---

### User Story 4 - Monthly Time Approval Workflow (Priority: P1)

After entering time for a month, engineers must submit their time logs for approval by their manager or designated approver. Once approved, the time entries become locked to prevent retroactive changes, ensuring data integrity for billing and payroll.

**Why this priority**: Time approval is critical for billing clients, processing payroll, and maintaining audit trails. Without approval workflow, time data cannot be trusted for financial purposes.

**Independent Test**: An engineer completes all entries for a month, clicks "Submit for Approval", their manager reviews the submitted hours, clicks "Approve", and the engineer's time entries for that month become read-only with an "Approved" status indicator.

**Acceptance Scenarios**:

1. **Given** a month's entries are complete, **When** the engineer submits for approval, **Then** all entries for that month change status to "Submitted" and become read-only for the engineer
2. **Given** entries are submitted, **When** a manager with approval authority reviews them, **Then** the manager can approve or reject the entire month
3. **Given** entries are rejected, **When** the engineer receives the rejection, **Then** entries become editable again with feedback visible
4. **Given** entries are approved, **When** anyone views that month, **Then** it displays "Approved" status and cannot be edited by anyone except authorized administrators
5. **Given** submission deadline has passed, **When** an engineer tries to submit late entries, **Then** the system accepts submission but flags it as "Late Submission" for manager awareness

---

### User Story 5 - Bulk Data Import/Export (Priority: P2)

Engineers and administrators need to efficiently import large amounts of time data from spreadsheets or export time data for external analysis, backup, or integration with other systems (payroll, accounting, etc.).

**Why this priority**: Manual entry for large datasets is error-prone and time-consuming. Bulk operations enable efficient data migration, backup, and integration with existing workflows.

**Independent Test**: An engineer downloads a CSV template, fills in 20 days of time entries in Excel, uploads the file, and sees all 20 entries appear in their calendar. Later, they export a month's data to CSV and verify all entries are present with correct values.

**Acceptance Scenarios**:

1. **Given** the system provides a CSV template, **When** a user downloads it, **Then** the template includes column headers for date, project code, hours, and comments
2. **Given** a properly formatted CSV file with time entries, **When** uploaded, **Then** the system validates each row and imports valid entries
3. **Given** the CSV contains errors (invalid project codes, invalid hours), **When** uploaded, **Then** the system reports specific row numbers and error messages without importing invalid data
4. **Given** time entries exist for a month, **When** the user exports to CSV, **Then** the file contains all entries with date, project, hours, and comments in readable format
5. **Given** a large CSV file (1000+ rows), **When** imported, **Then** the system processes it within 10 seconds and provides progress feedback

---

### User Story 6 - Copy Previous Month's Projects (Priority: P2)

Engineers often work on the same set of projects across multiple months. Rather than re-entering the same project list every month, they need a quick way to copy their previous month's project list as a starting template, then adjust hours as needed.

**Why this priority**: This dramatically reduces data entry time and prevents errors from retyping project codes. It's a common workflow optimization requested by frequent users.

**Independent Test**: An engineer who worked on Projects A, B, and C in January can click "Copy from Previous Month" in February, see Projects A, B, and C appear as selectable options (with zero hours), and then fill in February's actual hours for each project.

**Acceptance Scenarios**:

1. **Given** an engineer has entries for the previous month, **When** they click "Copy from Previous Month" in the current month, **Then** all unique projects from the previous month appear as options
2. **Given** previous month's projects are copied, **When** the engineer views the entry form, **Then** project names are pre-filled but hours are set to zero
3. **Given** projects are copied, **When** the engineer enters new hours, **Then** only the new hours are saved (previous month's hours remain unchanged)
4. **Given** the previous month had no entries, **When** "Copy from Previous Month" is clicked, **Then** the system informs the user no previous data exists and allows manual entry

---

### User Story 7 - Manager Proxy Entry (Priority: P2)

Managers need to enter time on behalf of their direct reports when engineers are unavailable (vacation, sick leave, forgot to enter) to ensure timely and complete time tracking for billing and payroll purposes.

**Why this priority**: Missing time entries cause billing delays and payroll issues. Allowing managers to enter data on behalf of their team with proper audit trails solves this operational problem.

**Independent Test**: A manager can select one of their direct reports from a dropdown list, switch to "Proxy Entry Mode", enter time entries as if they were that engineer, save the entries, and the system records who actually entered the data (the manager) while attributing the work hours to the engineer.

**Acceptance Scenarios**:

1. **Given** a manager views the system, **When** they access a list of their direct reports, **Then** only engineers who report directly to them appear in the list
2. **Given** a manager selects a direct report, **When** they enter time on that person's behalf, **Then** the entry is saved with the engineer's ID but records the manager as the "Entered By" user
3. **Given** proxy entries are made, **When** the engineer views their own time log, **Then** they see the entries with an indicator showing who entered the data
4. **Given** a manager tries to enter time for someone outside their team, **Then** the system denies permission and displays an authorization error
5. **Given** proxy entries exist, **When** generating audit reports, **Then** the system clearly shows which entries were proxy-entered and by whom

---

### Edge Cases

- **What happens when an engineer changes projects mid-month?** System allows different projects on different dates; monthly summary shows all projects worked during the month
- **How does the system handle fiscal year boundaries?** Time entries respect the organization's configured fiscal year pattern (e.g., 21st of month to 20th)
- **What if an engineer needs to edit an entry after submission but before approval?** Engineer must request rejection from approver, then edit, then resubmit
- **How are holidays handled?** System maintains a holiday calendar; dates marked as holidays are visually distinct and typically have zero hour expectations
- **What happens if total daily hours are less than 8?** System allows any total from 0-24 hours; managers can see completion rates but it's not blocked
- **How is overtime handled?** System accepts entries over 8 hours/day (up to 24h); reporting distinguishes regular vs overtime hours
- **What if two users edit the same entry simultaneously?** System uses optimistic locking; last save wins, with conflict notification to the losing user
- **How are entries handled during team member transitions (new hire, resignation)?** New members start with empty logs; departing members' approved data remains archived and read-only

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to enter time worked on a specific date, with time specified in 0.25-hour (15-minute) increments
- **FR-002**: System MUST allow users to allocate a single day's hours across multiple projects simultaneously
- **FR-003**: System MUST validate that total hours entered for a single date do not exceed 24 hours
- **FR-004**: System MUST display a monthly calendar view showing fiscal month periods (e.g., 21st of previous month to 20th of current month)
- **FR-005**: System MUST visually distinguish weekends, holidays, and weekdays in the calendar view
- **FR-006**: System MUST allow users to record absences (paid leave, sick leave, special leave, other) separately from project work hours
- **FR-007**: System MUST support submission of monthly time entries for approval
- **FR-008**: System MUST change time entry status from "Draft" to "Submitted" upon submission, making them read-only to the submitter
- **FR-009**: System MUST allow designated approvers to approve or reject submitted time entries
- **FR-010**: System MUST change time entry status to "Approved" upon approval, making them permanently read-only
- **FR-011**: System MUST allow designated approvers to reject entries with feedback, returning status to "Draft" for editing
- **FR-012**: System MUST provide CSV file import for bulk time entry creation
- **FR-013**: System MUST validate each row of imported CSV data and report specific errors (row number, error description)
- **FR-014**: System MUST provide CSV file export of time entries for a specified month
- **FR-015**: System MUST allow users to copy their previous month's project list (without hours) as a template for the current month
- **FR-016**: System MUST allow managers to enter time entries on behalf of their direct reports
- **FR-017**: System MUST record both the attributed user (whose time it is) and the actual user who entered the data (for proxy entries)
- **FR-018**: System MUST restrict proxy entry permission to only direct reporting relationships
- **FR-019**: System MUST provide a monthly summary showing total hours per project and percentage distribution
- **FR-020**: System MUST display expected working hours for the month (based on business days) vs actual hours entered
- **FR-021**: System MUST maintain a holiday calendar that can be referenced for determining expected working days
- **FR-022**: System MUST support responsive design for access from desktop computers, tablets, and mobile phones
- **FR-023**: System MUST display calendar entries with visual indicators for status (draft, submitted, approved, rejected)
- **FR-024**: System MUST allow users to add optional comments to time entries
- **FR-025**: System MUST preserve historical time entry data even after employee role changes or termination

### Key Entities

- **Time Entry**: Represents hours worked by a person on a specific project on a specific date, including the amount of time (in 0.25h increments), optional comment, entry status (draft/submitted/approved/rejected), and who entered the data
- **Absence**: Represents non-working time (vacation, sick leave, etc.) for a person on a specific date, including absence type and hours
- **Project**: Represents a billable or trackable work initiative that time can be charged against, having a unique code and name
- **Organization Member**: Represents a person who enters time, including their reporting structure (manager relationship) and roles (regular member, manager, approver)
- **Fiscal Month Period**: Represents the organization's definition of monthly periods, which may not align with calendar months (e.g., 21st to 20th)
- **Holiday**: Represents a non-working day in the organization's calendar, used for calculating expected working hours

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Engineers can complete a full month's time entry (20 working days, 2-3 projects per day) in under 15 minutes after the first month (using "Copy from Previous Month" feature)
- **SC-002**: Time entry accuracy improves by 40% compared to paper-based or email-based time tracking (measured by reduction in corrections and resubmissions)
- **SC-003**: 95% of monthly time submissions are completed by the deadline without manager intervention
- **SC-004**: Managers can complete approval of 10 team members' monthly time entries in under 10 minutes
- **SC-005**: CSV import successfully processes 100 time entries in under 10 seconds with clear error reporting for any invalid data
- **SC-006**: Calendar view loads and displays a full month's data (30 entries) within 1 second
- **SC-007**: System supports 100 concurrent users entering time without performance degradation
- **SC-008**: Mobile users can complete time entry for a single day in under 2 minutes using a phone screen
- **SC-009**: Zero approved time entries are accidentally modified (read-only enforcement works 100% of the time)
- **SC-010**: Audit reports can trace 100% of proxy-entered data back to the manager who entered it

## Assumptions *(optional)*

- Engineers have access to devices with modern web browsers (desktop, tablet, or mobile)
- Organizations have already defined their fiscal year pattern and monthly period structure
- Project codes are pre-configured and managed by administrators before engineers start entering time
- Approval hierarchy (who reports to whom) is maintained in the system or integrated from an external HR system
- Holiday calendar is maintained by administrators and updated at least annually
- Time entry is typically done daily or weekly, not months in arrears
- Managers have time to review and approve entries within a reasonable period (e.g., 5 business days after month end)
- CSV files for import follow a documented template format
- Users have basic familiarity with calendar interfaces and spreadsheet software

## Out of Scope *(optional)*

The following are explicitly **not** included in this feature:

- **Email/Slack notifications**: No automated reminders when entries are due, approved, or rejected (future phase)
- **Analytics dashboard**: No graphs, charts, or trend analysis of time data (future phase)
- **Project budget tracking**: No comparison of actual hours vs budgeted hours or cost tracking (future phase)
- **Excel/PDF export formats**: Only CSV export is supported; other formats are future enhancements
- **Detailed approval comments/discussion threads**: Only simple rejection reason text is supported; full comment threads are out of scope
- **Integration with external systems**: No direct integration with payroll, accounting, or project management systems (CSV export enables manual integration)
- **Time entry templates beyond project copy**: No saved templates for recurring patterns or favorites
- **Forecasting/planning**: No ability to enter planned/estimated hours, only actual hours worked
- **Multi-currency or billing rate tracking**: System tracks hours only, not dollar values
- **Detailed audit log UI**: While audit data is recorded, there's no dedicated UI for viewing complete audit history (administrators can query the database)
