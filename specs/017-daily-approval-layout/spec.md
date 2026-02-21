# Feature Specification: Daily Approval Layout Improvement

**Feature Branch**: `017-daily-approval-layout`
**Created**: 2026-02-21
**Status**: Draft
**Input**: User description: "日次承認画面の表示レイアウトをきれいにしたい。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Structured Entry List with Clear Visual Hierarchy (Priority: P1)

As a supervisor reviewing daily work logs, I want each entry clearly organized with a readable table layout, proper spacing, and visual distinction between dates and members so that I can quickly scan and process approval requests without eye strain.

Currently, the daily approval screen displays entries in a flat, dense layout with minimal visual separation between date groups and member sections. The table lacks proper column alignment and the overall density makes it hard to scan.

**Why this priority**: This is the core improvement — making the existing data readable and well-organized directly addresses the user's request. Without a clear visual hierarchy, supervisors struggle to review entries efficiently.

**Independent Test**: Can be fully tested by loading the daily approval page with sample data and verifying that date sections, member groups, and entry rows are visually distinct, properly spaced, and easy to scan.

**Acceptance Scenarios**:

1. **Given** the supervisor opens the daily approval page with entries across multiple dates, **When** the page loads, **Then** each date group is visually separated with a prominent date header.
2. **Given** a date group contains entries from multiple members, **When** viewing the group, **Then** each member's entries are grouped under a clearly labeled member section with visual separation.
3. **Given** entries exist in the table, **When** the supervisor scans the columns, **Then** project code, project name, hours, comment, status, and actions are properly aligned and evenly spaced.
4. **Given** an entry has a long comment, **When** displayed in the table, **Then** the comment is truncated with an ellipsis and the full text is accessible via hover tooltip.

---

### User Story 2 - Status Badges and Action Buttons with Consistent Styling (Priority: P2)

As a supervisor, I want approval statuses (pending, approved, rejected) to be displayed as clearly styled badges, and action buttons (approve, reject, recall) to be visually distinct and appropriately sized so that I can quickly identify each entry's status and take action.

**Why this priority**: Status clarity and actionability are essential for an approval workflow. Clear badges reduce cognitive load and consistent button styling prevents accidental actions.

**Independent Test**: Can be fully tested by viewing entries in various statuses and verifying that badges use distinct colors and that action buttons are consistently styled and clearly labeled.

**Acceptance Scenarios**:

1. **Given** an entry with "pending" status, **When** displayed, **Then** it shows a yellow/amber badge labeled "未承認" and both the checkbox and reject button are available.
2. **Given** an entry with "approved" status, **When** displayed, **Then** it shows a green badge labeled "承認済" and only the recall button is available.
3. **Given** an entry with "rejected" status, **When** displayed, **Then** it shows a red badge labeled "差戻" with the rejection comment accessible.
4. **Given** action buttons are available, **When** displayed, **Then** they use consistent padding, font size, and hover states that match the application's design system.

---

### User Story 3 - Summary Statistics and Filter Bar (Priority: P3)

As a supervisor, I want to see a summary of pending, approved, and rejected entry counts at the top of the page, along with an improved filter bar, so that I can quickly understand the overall workload and filter entries efficiently.

**Why this priority**: Summary statistics provide immediate context and reduce unnecessary scrolling. An improved filter bar makes the date selection more intuitive.

**Independent Test**: Can be fully tested by loading the page with entries in various statuses and verifying that counts are accurate and that filters update the displayed entries correctly.

**Acceptance Scenarios**:

1. **Given** the daily approval page loads with entries, **When** displayed, **Then** summary cards show the count of pending, approved, and rejected entries.
2. **Given** the filter bar is displayed, **When** the supervisor selects a date range, **Then** the date inputs are clearly labeled and aligned horizontally with the bulk approve button.
3. **Given** entries are filtered by date, **When** the summary counts update, **Then** they reflect only the filtered entries.
4. **Given** the supervisor selects entries for bulk approval, **When** the bulk approve button appears, **Then** it shows the count of selected entries and is styled prominently.

---

### Edge Cases

- What happens when there are no entries for the selected date range? An empty state message is displayed clearly with an icon and descriptive text.
- What happens when a date group has only one member with one entry? The layout remains consistent without collapsing or looking broken.
- What happens on narrow screens (mobile/tablet)? The table remains readable, with horizontal scrolling if necessary rather than breaking the layout.
- What happens when the rejection comment is very long? It is truncated in the table cell and the full text is visible via tooltip or expand action.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST display daily entries grouped by date, with each date section having a prominent header showing the date in Japanese format with year and day-of-week (e.g., "2026年2月21日(土)").
- **FR-002**: Within each date group, the system MUST sub-group entries by member, with each member's name displayed as a section header.
- **FR-003**: The entry table MUST display columns for: selection checkbox (for pending entries), project code and name, hours, comment, status badge, and actions. Each member section MUST display a subtotal row showing the member's total hours for that date (e.g., "合計: 8.0h").
- **FR-004**: Status badges MUST use distinct colors: yellow/amber for pending, green for approved, red for rejected. Rejected entries MUST display the rejection comment via tooltip on the badge.
- **FR-005**: Comments MUST be displayed as a single line truncated via CSS overflow when exceeding the column width, with the full text accessible via hover tooltip (`title` attribute).
- **FR-006**: The bulk approve button MUST display the count of selected entries and be visually prominent.
- **FR-007**: The page MUST display summary statistics (pending count, approved count, rejected count) above the entry list.
- **FR-008**: The filter bar MUST contain properly labeled date range inputs with consistent styling.
- **FR-009**: The reject modal MUST be styled consistently with the application's modal pattern (centered overlay with backdrop).
- **FR-010**: The layout MUST be responsive, maintaining readability on screens 1024px and wider, with horizontal scroll for narrower viewports.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Supervisors can identify the status of any entry within 1 second of looking at it (visual clarity of status badges).
- **SC-002**: The time to review and approve a set of 10 entries is reduced by at least 20% compared to the current layout (measured by task completion time).
- **SC-003**: 100% of entry text content is readable without needing to click or expand any element (except for truncated long comments which require hover).
- **SC-004**: The page renders with proper spacing and alignment with zero visual overflow or broken layout at standard desktop resolutions (1280px and above).

## Clarifications

### Session 2026-02-21

- Q: What date header display format should be used? → A: Japanese format with year and day-of-week (e.g., "2026年2月21日(土)")
- Q: Should each member section display a total hours subtotal? → A: Yes, show subtotal row per member (e.g., "合計: 8.0h")

## Assumptions

- The backend API response structure remains unchanged; this feature is a frontend-only visual improvement.
- The existing functionality (approve, reject, recall, bulk approve, date filtering) remains the same; only the visual presentation changes.
- The application's existing color scheme and design tokens (Tailwind CSS utility classes) will be used for consistent styling.
- The data model (DailyGroup > MemberEntryGroup > EntryRow) remains the same.
