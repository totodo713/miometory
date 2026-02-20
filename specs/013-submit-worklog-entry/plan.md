# Implementation Plan: Submit Work Log Entries

**Branch**: `013-submit-worklog-entry` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/013-submit-worklog-entry/spec.md`

## Summary

Add per-day work log entry submission and recall capabilities. Members can submit all DRAFT entries for a specific date to SUBMITTED status (making them read-only), and recall SUBMITTED entries back to DRAFT before manager action. The calendar view gains per-day status indicators. This operates directly on existing WorkLogEntry aggregates alongside the existing monthly batch approval workflow.

## Technical Context

**Language/Version**: Java 21 (domain), Kotlin 2.3.0 (infrastructure), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.5.9, Next.js 16.x, React 19.x, Tailwind CSS, Zustand
**Storage**: PostgreSQL 17 with JSONB event store (event sourcing)
**Testing**: JUnit 5 + Testcontainers (backend), Vitest + React Testing Library (frontend)
**Target Platform**: Linux server (backend), Web browser (frontend)
**Project Type**: Web application (frontend + backend)
**Performance Goals**: Submit/recall under 3 seconds end-to-end (SC-001)
**Constraints**: Atomic all-or-nothing per day, optimistic locking via aggregate versioning
**Scale/Scope**: Single-tenant dev usage, small number of entries per day (typically 1-5)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | Uses existing domain patterns (aggregate, events, service layer). No new architectural complexity. Peer review via PR. |
| II. Testing Discipline | PASS | Plan includes unit tests for domain logic, integration tests for API endpoints, and frontend component tests. Test pyramid respected. |
| III. Consistent UX | PASS | Follows existing status badge patterns, confirmation dialog patterns (CopyPreviousMonthDialog), and inline error messaging. Calendar indicator extends existing status color scheme. |
| IV. Performance Requirements | PASS | SC-001 defines 3-second target. Batch status transition is bounded by daily entry count (typically 1-5). No new heavy queries. |
| Additional Constraints | PASS | No new external dependencies. Uses existing Spring Boot, Next.js, and PostgreSQL stack. |
| Development Workflow | PASS | Feature branch with spec/plan/tasks artifacts. Automated formatting and linting. |

**Gate Result**: PASS — no violations.

## Project Structure

### Documentation (this feature)

```text
specs/013-submit-worklog-entry/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-contracts.md # REST API contract definitions
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── api/
│   │   └── WorkLogController.java              # New submit/recall endpoints
│   ├── api/dto/
│   │   ├── SubmitDailyEntriesRequest.java      # New request DTO
│   │   └── RecallDailyEntriesRequest.java      # New request DTO
│   ├── application/
│   │   ├── command/
│   │   │   ├── SubmitDailyEntriesCommand.java  # New command
│   │   │   └── RecallDailyEntriesCommand.java  # New command
│   │   └── service/
│   │       └── WorkLogEntryService.java        # New submitDailyEntries() and recallDailyEntries() methods
│   └── domain/worklog/
│       └── WorkLogEntry.java                   # Existing: uses changeStatus() method
└── src/test/

frontend/
├── app/
│   ├── components/worklog/
│   │   ├── DailyEntryForm.tsx                  # Add Submit/Recall buttons
│   │   ├── Calendar.tsx                        # Already has status indicators (no changes needed)
│   │   └── SubmitDailyButton.tsx               # New component for submit/recall actions
│   ├── services/
│   │   └── api.ts                              # New submitDailyEntries() and recallDailyEntries() API methods
│   └── types/
│       └── worklog.ts                          # No changes needed (types already exist)
└── __tests__/
```

**Structure Decision**: Web application structure. Backend changes are concentrated in the API and application service layers — the domain model already supports the required status transitions. Frontend changes add submit/recall UI to the existing DailyEntryForm and create a new SubmitDailyButton component.
