# Tasks: Daily Approval Layout Improvement

**Input**: Design documents from `/specs/017-daily-approval-layout/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, quickstart.md

**Tests**: Included — the constitution requires tests for every new feature (Principle II).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create shared utilities needed by all user stories

- [X] T001 Create date formatting utility with `formatDateJapanese()` function using `Intl.DateTimeFormat("ja-JP")` in `frontend/app/lib/date-format.ts`
- [X] T002 [P] Create unit tests for date formatting utility covering standard dates, edge dates (year boundaries, leap year), and weekday correctness in `frontend/tests/unit/lib/date-format.test.ts`

---

## Phase 2: User Story 1 - Structured Entry List with Clear Visual Hierarchy (Priority: P1) 🎯 MVP

**Goal**: Refactor the DailyApprovalDashboard layout to have prominent date headers in Japanese format, visually distinct member sections, properly aligned table columns, truncated long comments with tooltips, and per-member hour subtotals.

**Independent Test**: Load the daily approval page with sample data and verify date sections, member groups, column alignment, comment truncation, and subtotal rows are all correctly displayed.

### Tests for User Story 1

- [X] T003 [P] [US1] Write unit tests for DailyApprovalDashboard: date header rendering in Japanese format, member section grouping with visual headers, column alignment structure, comment truncation with title tooltip, per-member hour subtotal row, and empty state rendering when no entries exist in `frontend/tests/unit/components/admin/DailyApprovalDashboard.test.tsx`

### Implementation for User Story 1

- [X] T004 [US1] Refactor date group headers in DailyApprovalDashboard to use `formatDateJapanese()` with prominent styling (larger text, stronger background, left border accent) in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T005 [US1] Refactor member sections with clearly labeled member name headers, visual card-style separation (background, border, padding), and spacing between member groups in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T006 [US1] Improve table column alignment: set explicit column widths, add proper padding, align hours right, truncate long comments with CSS `truncate` class and `title` attribute for tooltip in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T007 [US1] Add per-member hour subtotal row at the bottom of each member's entry table, summing `entry.hours` and displaying as "合計: X.Xh" right-aligned in the hours column in `frontend/app/components/admin/DailyApprovalDashboard.tsx`

**Checkpoint**: Date headers show Japanese format with weekday, member sections are visually distinct, columns are aligned, comments truncate with tooltip, subtotals display correctly.

---

## Phase 3: User Story 2 - Status Badges and Action Buttons with Consistent Styling (Priority: P2)

**Goal**: Apply distinct colored badges for each approval status and style action buttons consistently with proper padding, sizing, and hover states.

**Independent Test**: View entries in pending, approved, and rejected statuses and verify badges have distinct colors and action buttons are consistently styled.

### Tests for User Story 2

- [X] T008 [P] [US2] Add unit tests for status badge rendering (yellow/amber for pending, green for approved, red for rejected) and action button visibility per status in `frontend/tests/unit/components/admin/DailyApprovalDashboard.test.tsx`

### Implementation for User Story 2

- [X] T009 [US2] Restyle status badges with consistent sizing (px-2.5 py-1), rounded-full, distinct background/text colors (amber-100/amber-800 for pending, green-100/green-800 for approved, red-100/red-800 for rejected); for rejected entries, display the rejection comment as a tooltip on the badge in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T010 [US2] Restyle action buttons (差戻, 取消) with consistent padding (px-3 py-1.5), rounded-md, border, font-medium, and clear hover/focus states matching the application's design system in `frontend/app/components/admin/DailyApprovalDashboard.tsx`

**Checkpoint**: Status badges are visually distinct and correctly colored, action buttons are consistently styled with proper hover states.

---

## Phase 4: User Story 3 - Summary Statistics and Filter Bar (Priority: P3)

**Goal**: Add summary statistics cards (pending, approved, rejected counts) above the entry list and improve the filter bar layout with better alignment and styling.

**Independent Test**: Load the page with entries in various statuses and verify counts are accurate, update on filter change, and filter bar is well-laid-out.

### Tests for User Story 3

- [X] T011 [P] [US3] Add unit tests for summary statistics cards (correct counts per status, count updates on data change) and filter bar layout in `frontend/tests/unit/components/admin/DailyApprovalDashboard.test.tsx`

### Implementation for User Story 3

- [X] T012 [US3] Add summary statistics cards section above the entry list, computing pending/approved/rejected counts from loaded groups data, displayed as three cards with count and label in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T013 [US3] Restyle filter bar: wrap date inputs and bulk approve button in a flex container with proper alignment, add labels with consistent typography, style date inputs with matching border/focus rings in `frontend/app/components/admin/DailyApprovalDashboard.tsx`

**Checkpoint**: Summary cards show correct counts, filter bar is well-aligned, bulk approve button is visually prominent.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Responsiveness, empty state, and final validation

- [X] T014 Add responsive wrapper with `overflow-x-auto` for table on viewports below 1024px, improve empty state display with descriptive icon and text, and restyle reject modal to match application modal pattern (centered overlay, backdrop, consistent button styling) in `frontend/app/components/admin/DailyApprovalDashboard.tsx`
- [X] T015 Run Biome lint/format check and fix any issues across all modified files (`npx biome check --write`)
- [X] T016 Run all tests and verify pass (`npm test -- --run`)
- [ ] T017 Run quickstart.md validation scenarios manually against dev environment, including SC-002 review efficiency validation (review 10 entries and confirm improved scan speed) *(manual — requires dev environment)*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **User Story 1 (Phase 2)**: Depends on T001 (date utility) from Setup
- **User Story 2 (Phase 3)**: No dependency on US1 — can run in parallel
- **User Story 3 (Phase 4)**: No dependency on US1 or US2 — can run in parallel
- **Polish (Phase 5)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Depends on Setup (T001). Core layout refactor.
- **User Story 2 (P2)**: Independent. Badge/button styling only.
- **User Story 3 (P3)**: Independent. Summary cards and filter bar.

### Within Each User Story

- Tests written first (to establish expectations)
- Implementation follows tests
- All implementation tasks within a story are sequential (same file)

### Parallel Opportunities

- T001 and T002 can run in parallel (different files)
- T003, T008, T011 (test tasks) can run in parallel with each other
- US2 and US3 implementation can run in parallel with US1 (but all touch same file, so sequential execution recommended)

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: User Story 1 (T003-T007)
3. **STOP and VALIDATE**: Test visual hierarchy independently
4. Deploy/demo if ready

### Incremental Delivery

1. Setup → Foundation ready
2. Add User Story 1 → Test independently → Visual hierarchy complete (MVP!)
3. Add User Story 2 → Test independently → Badges and buttons polished
4. Add User Story 3 → Test independently → Summary stats and filter bar added
5. Polish → Responsive, lint, final validation

---

## Notes

- All user story tasks touch the same file (`DailyApprovalDashboard.tsx`), so parallel execution within implementation is not practical — execute sequentially
- Test tasks (T003, T008, T011) CAN be written in parallel since they go into the same test file but test independent behaviors
- The date utility (T001) is the only new file; everything else is refactoring existing code
- No backend changes required
- Commit after each user story checkpoint for incremental delivery
