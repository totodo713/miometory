# Implementation Plan: Edit Rejected Work Log Entries

**Branch**: `014-edit-rejected-entry` | **Date**: 2026-02-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/014-edit-rejected-entry/spec.md`

## Summary

Enable members and proxy managers to edit, add, and delete work log entries and absences after monthly or daily rejection. The feature builds on the existing rejection → DRAFT transition for monthly approval (entries already become editable) and introduces a new daily rejection mechanism. Key additions: prominent rejection reason display throughout the correction cycle, new daily rejection endpoint, proxy submission support for daily submit/recall, and visual rejection indicators on the calendar.

## Technical Context

**Language/Version**: Java 21 (domain), Kotlin 2.3.0 (infrastructure/tests), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.5.9, Spring Data JDBC, Next.js 16.x, React 19.x, Tailwind CSS, Zustand
**Storage**: PostgreSQL 17 with JSONB event store + projection tables, Flyway migrations
**Testing**: JUnit 5, Spring Boot Test, Testcontainers 1.21.1, MockK (backend); Vitest, React Testing Library (frontend)
**Target Platform**: Linux server (backend), Web browser (frontend)
**Project Type**: Web application (frontend + backend)
**Performance Goals**: <200ms p95 for entry CRUD and status transitions; calendar load <500ms
**Constraints**: Optimistic locking for concurrent edits; event sourcing append-only; fiscal month 21st-20th pattern
**Scale/Scope**: Existing user base; changes touch ~15 backend files, ~8 frontend files, 1 new migration

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | PASS | Changes follow existing patterns (DDD, event sourcing, projections). New code uses established conventions (record DTOs, domain events, service orchestration). Inline documentation will be added. |
| II. Testing Discipline | PASS | Unit tests for new domain events/service methods, integration tests for new endpoints with Testcontainers, frontend component tests with React Testing Library. Test pyramid respected. |
| III. Consistent UX | PASS | Rejection indicators follow existing status color scheme (REJECTED=red). Entry form reuses existing DailyEntryForm with rejection context added. Proxy mode uses established Zustand patterns. Error messages follow existing patterns. |
| IV. Performance | PASS | New daily_rejection_log query uses indexed (member_id, work_date). Calendar enrichment adds one lightweight query per load. No heavy joins or N+1 patterns introduced. |
| Additional Constraints | PASS | No new external dependencies. All changes use existing technology stack. PostgreSQL 17, Flyway migration follows V-numbering convention. |
| Development Workflow | PASS | Feature branch, spec → plan → tasks workflow. Each change maps to a documented requirement (FR-001 through FR-017). |

**Post-Design Re-Check**: PASS — No violations introduced during Phase 1 design. The `daily_rejection_log` is a simple projection table, consistent with existing projection patterns (`work_log_entries_projection`, `monthly_approvals_projection`).

## Project Structure

### Documentation (this feature)

```text
specs/014-edit-rejected-entry/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0: Technical research and decisions
├── data-model.md        # Phase 1: Entity definitions and state transitions
├── quickstart.md        # Phase 1: Development and testing quickstart
├── contracts/           # Phase 1: API contracts
│   └── api-contracts.md # REST endpoint specifications
├── checklists/          # Quality checklists
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── api/
│   │   ├── WorkLogController.java          # Modified: add reject-daily endpoint
│   │   ├── ApprovalController.java         # Modified: add member-facing approval GET
│   │   ├── dto/                            # New DTOs for daily rejection
│   │   │   ├── RejectDailyEntriesRequest.java
│   │   │   ├── RejectDailyEntriesResponse.java
│   │   │   ├── DailyRejectionResponse.java
│   │   │   └── MemberApprovalResponse.java
│   │   └── GlobalExceptionHandler.java     # Modified: new error codes
│   ├── application/
│   │   ├── approval/ApprovalService.java   # Modified: proxy monthly submission
│   │   └── service/WorkLogEntryService.java # Modified: daily reject, proxy submit/recall
│   ├── domain/
│   │   └── worklog/events/                 # New: DailyEntriesRejected event (if aggregate-emitted)
│   └── infrastructure/
│       └── persistence/
│           └── JdbcDailyRejectionLogRepository.kt  # New: daily rejection log persistence
├── src/main/resources/db/migration/
│   └── V15__daily_rejection_log.sql                # New: migration for daily_rejection_log table
└── src/test/
    ├── java/com/worklog/
    │   ├── api/
    │   │   ├── WorkLogControllerRejectDailyTest.java
    │   │   └── ApprovalControllerMemberViewTest.java
    │   ├── application/
    │   │   ├── WorkLogEntryServiceDailyRejectTest.java
    │   │   └── ApprovalServiceProxySubmitTest.java
    │   └── domain/
    │       └── (existing tests cover status transitions)
    └── kotlin/com/worklog/infrastructure/
        └── JdbcDailyRejectionLogRepositoryTest.kt

frontend/
├── app/
│   ├── components/worklog/
│   │   ├── Calendar.tsx              # Modified: rejection indicators
│   │   ├── DailyEntryForm.tsx        # Modified: show rejection reason, editing support
│   │   ├── MonthlySummary.tsx        # Modified: prominent rejection reason banner
│   │   ├── SubmitButton.tsx          # Modified: proxy submission support
│   │   ├── SubmitDailyButton.tsx     # Modified: proxy submit, rejection indicators
│   │   └── RejectionBanner.tsx       # New: reusable rejection reason display component
│   ├── services/
│   │   ├── api.ts                    # Modified: new API endpoints
│   │   └── worklogStore.ts           # Modified: rejection state if needed
│   └── hooks/
│       └── useRejectionStatus.ts     # New: hook to query rejection status
└── __tests__/
    ├── components/worklog/
    │   ├── Calendar.rejection.test.tsx
    │   ├── DailyEntryForm.rejection.test.tsx
    │   ├── MonthlySummary.rejection.test.tsx
    │   └── RejectionBanner.test.tsx
    └── hooks/
        └── useRejectionStatus.test.ts
```

**Structure Decision**: Web application structure (frontend + backend) matching existing repository layout. All changes extend existing files and patterns. New files created only where new concepts are introduced (daily rejection log, rejection banner component, rejection status hook).

## Complexity Tracking

No constitution violations. No complexity justification needed.
