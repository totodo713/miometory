# Tasks: AuditLog Persistence Bug Fix

**Input**: Design documents from `/specs/010-fix-auditlog-persistence/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle II mandates tests for every bug fix.

**Organization**: Tasks grouped by user story. Foundational phase contains shared implementation changes required by multiple stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app (backend only)**: `backend/src/main/java/com/worklog/`, `backend/src/test/kotlin/com/worklog/`

---

## Phase 1: Setup

**Purpose**: Verify development environment is ready for changes

- [x] T001 Verify project builds cleanly on feature branch by running `./gradlew build` from `backend/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core entity and converter changes that MUST be complete before ANY user story can be validated. These address the root causes of all three bugs.

**Why foundational**: AuditLog `Persistable<UUID>` (US3 fix) and JSONB/INET converters (US1 fix) are both required for `CrudRepository.save()` to succeed. Neither fix works in isolation.

- [x] T002 [P] Implement `Persistable<UUID>` on AuditLog entity: add `@Transient boolean isNew` field, `@PersistenceCreator` annotation on constructor (sets `isNew = false`), `isNew()` method, and update factory methods to set `isNew = true` in `backend/src/main/java/com/worklog/domain/audit/AuditLog.java`
- [x] T003 [P] Create `StringToJsonbWritingConverter` (`@WritingConverter @Component`) that converts `String` to `PGobject` with type `"jsonb"`, handling null input. Include class-level Javadoc documenting purpose and conversion semantics. In `backend/src/main/java/com/worklog/infrastructure/persistence/StringToJsonbWritingConverter.java`
- [x] T004 [P] Create `JsonbToStringReadingConverter` (`@ReadingConverter @Component`) that converts `PGobject` to `String` via `getValue()`, handling null input. Include class-level Javadoc documenting purpose and conversion semantics. In `backend/src/main/java/com/worklog/infrastructure/persistence/JsonbToStringReadingConverter.java`
- [x] T005 [P] Create `StringToInetWritingConverter` (`@WritingConverter @Component`) that converts `String` to `PGobject` with type `"inet"`, handling null input. Include class-level Javadoc documenting purpose and conversion semantics. In `backend/src/main/java/com/worklog/infrastructure/persistence/StringToInetWritingConverter.java`
- [x] T006 [P] Create `InetToStringReadingConverter` (`@ReadingConverter @Component`) that converts `PGobject` to `String` via `getValue()`, handling null input. Include class-level Javadoc documenting purpose and conversion semantics. In `backend/src/main/java/com/worklog/infrastructure/persistence/InetToStringReadingConverter.java`
- [x] T007 Register all 4 new converters in `jdbcCustomConversions()` bean alongside existing UserId/RoleId converters in `backend/src/main/java/com/worklog/infrastructure/persistence/PersistenceConfig.java`

**Checkpoint**: Foundation ready — `AuditLog` entity can now be saved via `CrudRepository.save()` with correct type mapping and INSERT behavior. User story validation can begin.

---

## Phase 3: User Story 1 — Audit Events Are Reliably Recorded on Login (Priority: P1) 🎯 MVP

**Goal**: Verify that audit log entries persist with correctly formatted JSONB details and INET ip_address data, for both successful and failed login attempts.

**Independent Test**: Save an AuditLog via repository with JSONB details and INET ip_address, then read it back and verify data integrity (no PSQLException, no data corruption).

**Requirements**: FR-001, FR-002, FR-005, FR-006 | **Success Criteria**: SC-001, SC-003, SC-004

### Tests for User Story 1

- [x] T008 [P] [US1] Create unit tests for all 4 converters (null handling, valid String→PGobject round-trip, IPv4 and IPv6 for INET, valid JSON for JSONB) in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/JsonbInetConverterTest.kt`
- [x] T009 [US1] Create integration tests for AuditLog repository: save with JSONB details and verify queryable, save with IPv4/IPv6 INET ip_address and verify stored correctly, save with null details/ip_address, save via `createUserAction()` and `createSystemEvent()` factory methods in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/AuditLogRepositoryTest.kt`

**Checkpoint**: AuditLog entries persist and read back with correct JSONB and INET data. SC-003 and SC-004 validated.

---

## Phase 4: User Story 2 — Audit Logging Does Not Disrupt Primary Operations (Priority: P1)

**Goal**: Isolate audit log persistence into a separate transaction so failures never roll back the calling operation (login).

**Independent Test**: Simulate an audit log persistence failure during login and verify the login still succeeds with a valid session.

**Requirements**: FR-004 | **Success Criteria**: SC-002

### Implementation for User Story 2

- [x] T010 [P] [US2] Create `AuditLogService` with `@Service` and `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `logEvent()` method. Move audit log creation logic from `AuthServiceImpl.logAuditEvent()`. Include `try/catch` with SLF4J error logging (replace `System.err.println`). Include class-level and method-level Javadoc. In `backend/src/main/java/com/worklog/application/audit/AuditLogService.java`
- [x] T011 [US2] Refactor `AuthServiceImpl`: replace `AuditLogRepository` dependency with `AuditLogService`, remove private `logAuditEvent()` method, update all call sites to use `auditLogService.logEvent()`, update constructor in `backend/src/main/java/com/worklog/application/auth/AuthServiceImpl.java`
- [x] T012 [US2] Update existing `AuthServiceTest`: replace `AuditLogRepository` mock with `AuditLogService` mock, update constructor call, verify `auditLogService.logEvent()` is called at correct points in `backend/src/test/kotlin/com/worklog/application/auth/AuthServiceTest.kt`

### Tests for User Story 2

- [x] T013 [P] [US2] Create unit tests for `AuditLogService`: verify `logEvent()` creates AuditLog and calls repository save, verify exception in save is caught and logged (not propagated), verify null userId handling for system events in `backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceTest.kt`
- [x] T014 [US2] Create integration test for transaction isolation: within a `@Transactional` test, call `AuditLogService.logEvent()` with a setup that causes save failure (e.g., invalid data), verify the outer transaction is NOT marked rollback-only in `backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceTest.kt`

**Checkpoint**: Login operations complete successfully even when audit log persistence fails. SC-002 validated.

---

## Phase 5: User Story 3 — New Audit Log Entries Are Always Created (Priority: P2)

**Goal**: Verify that every AuditLog save results in a new record (INSERT), never an UPDATE of an existing record.

**Independent Test**: Create multiple AuditLog instances via factory methods, save each, and verify each has a distinct database row with unique ID.

**Requirements**: FR-003 | **Success Criteria**: SC-001

### Tests for User Story 3

- [x] T015 [P] [US3] Create unit tests for AuditLog Persistable contract: `createUserAction()` returns entity with `isNew() == true`, `createSystemEvent()` returns entity with `isNew() == true`, constructor with `@PersistenceCreator` returns entity with `isNew() == false`, `getId()` returns non-null UUID in `backend/src/test/kotlin/com/worklog/domain/audit/AuditLogTest.kt`
- [x] T016 [US3] Add integration tests to AuditLogRepositoryTest: save two AuditLogs with same event type and verify both exist as separate rows, verify `count()` increases by 1 for each save (never stays same from UPDATE), verify repository `findByEventType()` returns saved entries in `backend/src/test/kotlin/com/worklog/infrastructure/persistence/AuditLogRepositoryTest.kt`

**Checkpoint**: All saves produce new records. Combined with Phase 3, audit trail integrity is guaranteed. SC-001 validated.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation across all stories

- [x] T017 Run full backend test suite (`./gradlew test`) and verify all tests pass including pre-existing tests
- [x] T018 Run format check (`./gradlew checkFormat`), static analysis (`./gradlew detekt`), and fix any violations with `./gradlew formatAll`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 completion (converters + Persistable must be in place)
- **US2 (Phase 4)**: Depends on Phase 2 completion (AuditLog must be saveable for service tests)
- **US3 (Phase 5)**: Depends on Phase 2 completion (Persistable changes are already in place, this validates them)
- **Polish (Phase 6)**: Depends on all story phases being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — No dependencies on other stories
- **US2 (P1)**: Can start after Phase 2 — No dependencies on other stories. **Can run in parallel with US1.**
- **US3 (P2)**: Can start after Phase 2 — No dependencies on other stories. **Can run in parallel with US1 and US2.**

### Within Each User Story

- Tests can be written first (TDD) for US3 unit tests (T015) and US1 converter tests (T008)
- Integration tests (T009, T014, T016) require foundational implementation to be complete
- US2 implementation (T010-T012) must be sequential: Service → Refactor → Update tests

### Task-Level Dependencies

```
T001 (build)
  └─→ T002, T003, T004, T005, T006  (all parallel — different files)
        └─→ T007 (PersistenceConfig — depends on T003-T006)
              └─→ T008 (converter tests — parallel with T009, T010, T015)
              └─→ T009 (repository integration tests)
              └─→ T010 (AuditLogService — parallel with T008, T009, T015)
                    └─→ T011 (AuthServiceImpl refactor)
                          └─→ T012 (AuthServiceTest update)
              └─→ T013 (AuditLogService unit tests — after T010)
              └─→ T014 (transaction isolation test — after T010)
              └─→ T015 (AuditLog unit tests — parallel with T008, T009, T010)
              └─→ T016 (INSERT-only integration test — after T009 exists)
                    └─→ T017, T018 (polish — after all stories)
```

### Parallel Opportunities

```
# After T001 completes — 5 parallel tasks:
T002 (AuditLog.java), T003 (JsonbWriter), T004 (JsonbReader), T005 (InetWriter), T006 (InetReader)

# After Phase 2 completes — 4 parallel tasks across stories:
T008 [US1] (converter tests), T010 [US2] (AuditLogService), T015 [US3] (AuditLog tests), T009 [US1] (repo tests)

# Within US2 after T010 — 2 parallel tracks:
Track A: T011 → T012  (implementation + existing test update)
Track B: T013, T014   (new tests)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (converters + Persistable)
3. Complete Phase 3: US1 tests
4. **STOP and VALIDATE**: AuditLog saves work with correct JSONB/INET data
5. This alone fixes the primary PSQLException blocking E2E tests

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready (saves work mechanically)
2. Add US1 tests → Validate type mapping correctness (MVP!)
3. Add US2 → Transaction isolation prevents login disruption
4. Add US3 → Insert-only behavior fully validated
5. Polish → Full regression + formatting

### Parallel Execution (Fastest Path)

1. Phase 2: Launch T002-T006 in parallel (5 tasks, ~same time as 1)
2. T007 immediately after converters complete
3. Launch T008 + T009 + T010 + T015 in parallel (4 tasks across 3 stories)
4. Complete remaining sequential tasks per story
5. Polish

---

## Notes

- All implementation follows existing codebase patterns (see research.md for decisions and rationale)
- Converters follow `UserIdToUuidConverter` pattern exactly (same annotations, structure, package)
- `Persistable<UUID>` follows `Role` entity's `@PersistenceCreator` pattern (commit 181823e)
- `AuditLogService` follows `AuthServiceImpl` pattern (`@Service` + `@Transactional`)
- No schema changes — all fixes are application-layer only
- Two audit tables exist (`audit_log` and `audit_logs`) — only `audit_logs` is affected
- Commit after each phase completion for clean git history
