# Implementation Plan: Organization Management

**Branch**: `016-org-management` | **Date**: 2026-02-21 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/016-org-management/spec.md`

## Summary

Implement a tenant administrator UI for managing organizations (departments/teams) and supervisor (manager) assignments within the existing Miometry time entry system. The feature adds CRUD management for hierarchical organizations, member-to-manager assignment with full transitive circular reference detection, organization tree visualization, and fiscal/monthly period pattern assignment. The backend extends the existing event-sourced Organization aggregate and JDBC-based Member model; the frontend adds new admin panel pages following established patterns.

## Technical Context

**Language/Version**: Java 21 (domain layer), Kotlin 2.3.0 (infrastructure/config), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.5.9, Spring Data JDBC, Spring Security, Next.js 16.x, React 19.x, Tailwind CSS
**Storage**: PostgreSQL 17 with JSONB event store (event sourcing for Organization), direct JDBC (Member projection)
**Testing**: JUnit 5 + Testcontainers (backend), Vitest + React Testing Library (frontend)
**Target Platform**: Web application (Linux server backend, browser frontend)
**Project Type**: Web application (backend + frontend)
**Performance Goals**: Organization tree loads in <2s for 100 orgs, search returns in <1s for 500 orgs
**Constraints**: 6-level hierarchy depth, full transitive circular reference detection, tenant isolation
**Scale/Scope**: Up to 500 organizations per tenant, standard admin panel with 3 new pages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | Follow existing patterns: Spotless formatting, Detekt (Kotlin), Biome (frontend). All new code peer-reviewed. Inline documentation for public APIs. |
| II. Testing Discipline | PASS | Unit tests for domain logic (circular ref detection, org hierarchy validation), integration tests with Testcontainers for repositories, frontend component tests with Vitest. Test pyramid followed. |
| III. Consistent UX | PASS | Reuse existing admin panel patterns (list/form/modal). Japanese UI labels consistent with existing admin pages. Status badges, pagination, debounced search follow established conventions. |
| IV. Performance | PASS | SC-003 (<2s for 100 orgs), SC-005 (<1s search for 500 orgs). Covered by existing indexed projection tables. Recursive CTE for circular detection bounded by member count. |
| Additional Constraints | PASS | No new external dependencies. All technologies already in use. Flyway migration follows existing V-numbering. |

**Gate Result: PASS** — No violations. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/016-org-management/
├── plan.md              # This file
├── research.md          # Phase 0: Technical decisions
├── data-model.md        # Phase 1: Entity definitions
├── quickstart.md        # Phase 1: Integration scenarios
├── contracts/           # Phase 1: API specifications
│   ├── organization-api.md
│   └── member-manager-api.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── api/
│   │   ├── AdminOrganizationController.java     # NEW: Organization CRUD endpoints
│   │   └── AdminMemberController.java           # MODIFY: Add manager/transfer endpoints
│   ├── application/
│   │   ├── command/
│   │   │   ├── UpdateOrganizationCommand.java    # NEW
│   │   │   └── TransferMemberCommand.java        # NEW
│   │   └── service/
│   │       └── AdminOrganizationService.java     # NEW: Organization admin service
│   ├── domain/
│   │   ├── member/
│   │   │   └── Member.java                       # MODIFY: Make organizationId mutable
│   │   └── organization/
│   │       └── (existing aggregate, events)      # EXISTING: No changes needed
│   └── infrastructure/
│       └── repository/
│           ├── OrganizationRepository.java       # MODIFY: Add projection sync
│           └── JdbcMemberRepository.java         # MODIFY: Add circular detection
├── src/main/kotlin/com/worklog/infrastructure/
│   └── config/
│       └── (existing config)                     # EXISTING: No changes needed
├── src/main/resources/db/migration/
│   └── V19__organization_management_permissions.sql  # NEW: Permissions seed
└── src/test/
    └── java/com/worklog/
        ├── api/AdminOrganizationControllerTest.java  # NEW
        └── application/service/AdminOrganizationServiceTest.java  # NEW

frontend/
├── app/
│   ├── admin/
│   │   └── organizations/
│   │       └── page.tsx                          # NEW: Organization management page
│   ├── components/admin/
│   │   ├── AdminNav.tsx                          # MODIFY: Add 組織 nav item
│   │   ├── OrganizationList.tsx                  # NEW: Organization list table
│   │   ├── OrganizationForm.tsx                  # NEW: Organization create/edit modal
│   │   ├── OrganizationTree.tsx                  # NEW: Tree visualization
│   │   └── MemberManagerForm.tsx                 # NEW: Manager assignment modal
│   └── services/
│       └── api.ts                                # MODIFY: Add organization endpoints
└── tests/unit/components/admin/
    ├── OrganizationList.test.tsx                  # NEW
    ├── OrganizationForm.test.tsx                  # NEW
    ├── OrganizationTree.test.tsx                  # NEW
    └── MemberManagerForm.test.tsx                 # NEW
```

**Structure Decision**: Web application pattern. Backend follows existing DDD layers (api → application → domain → infrastructure). Frontend follows existing admin panel patterns (page → list/form components → api service). All new files align with established naming conventions and directory structure.

## Complexity Tracking

> No constitution violations — this section is intentionally empty.
