# Research: Fix Signup API Role Instantiation Error

**Date**: 2026-02-17
**Branch**: `009-fix-signup-role-error`

## R1: Spring Data JDBC Entity Instantiation Strategy

**Decision**: Annotate the existing 5-argument rehydration constructor on `Role.java` with `@PersistenceCreator` from `org.springframework.data.annotation`.

**Rationale**: Spring Data JDBC uses a ranked strategy for instantiation:
1. If `@PersistenceCreator` is present, use that constructor exclusively.
2. Otherwise, prefer a no-arg constructor and set fields via reflection/setters.
3. If only parameterized constructors exist and none is annotated, Spring Data falls back to `ReflectionEntityInstantiator` which requires a no-arg constructor — causing the observed `BeanInstantiationException`.

The `Role` entity has two parameterized constructors (4-arg and 5-arg) and no no-arg constructor. Spring Data JDBC cannot disambiguate which to use, so it falls back to requiring a default constructor. Annotating the 5-arg constructor with `@PersistenceCreator` resolves the ambiguity explicitly.

**Alternatives considered**:
- **Add a protected no-arg constructor**: Works but bypasses validation logic, allows uninitialized `final` fields, and requires making `final` fields non-final. Violates domain model integrity.
- **Convert Role to a Kotlin data class**: Would solve the constructor issue but contradicts the project pattern where domain logic is in Java and infrastructure is in Kotlin.
- **Use `@PersistenceConstructor` (deprecated alias)**: Functionally equivalent but `@PersistenceCreator` is the current canonical annotation in Spring Data 3.x.

## R2: Impact on Existing Dual-Constructor Pattern

**Decision**: No changes to the existing dual-constructor pattern. Only the 5-arg rehydration constructor receives the annotation.

**Rationale**: The project uses a consistent dual-constructor pattern across entities:
- 4-arg constructor for domain creation (delegates to full constructor)
- 5-arg constructor for persistence rehydration (all fields)

Adding `@PersistenceCreator` to the 5-arg constructor does not change the 4-arg constructor's behavior. Factory methods (`Role.create()`) and tests that use the 4-arg constructor remain unaffected.

**Alternatives considered**:
- **Annotate both constructors**: Invalid — only one `@PersistenceCreator` is allowed per entity.
- **Merge into a single constructor with defaults**: Would break the clean separation between creation and rehydration concerns.

## R3: Custom Converter Compatibility

**Decision**: No changes needed to existing `RoleIdToUuidConverter` / `UuidToRoleIdConverter`.

**Rationale**: The converters are already registered in `PersistenceConfig` and correctly convert between `RoleId` and `UUID`. Once Spring Data JDBC knows which constructor to use (via `@PersistenceCreator`), it will apply the registered converters to map the `UUID` column value to the `RoleId` parameter automatically.

## R4: Regression Test Strategy

**Decision**: Add an integration test using `@SpringBootTest` with Testcontainers to verify the full signup flow works end-to-end.

**Rationale**: The bug is a persistence-layer issue that only manifests when actually loading from a real database. Unit tests with mocked repositories would not catch this class of error. An integration test with Testcontainers matches the project's existing testing pattern (see `backend/src/test/java/com/worklog/`).

**Alternatives considered**:
- **Unit test with mocked repository**: Would not catch the actual instantiation failure since mocks bypass Spring Data JDBC mapping.
- **Manual testing only**: Violates Constitution Principle II (Testing Discipline) requiring automated assertions for every bug fix.

## R5: AuditLog Entity Assessment

**Decision**: No change needed for `AuditLog.java`.

**Rationale**: `AuditLog` also uses `@Table` + Spring Data JDBC, but it has only a single constructor (7-arg). Spring Data JDBC can unambiguously select a single constructor without `@PersistenceCreator`. The issue with `Role` specifically arises from having two constructors (4-arg and 5-arg), which causes Spring Data JDBC to fall back to looking for a no-arg constructor rather than guessing which parameterized constructor to use.

**Alternatives considered**:
- **Add `@PersistenceCreator` to AuditLog preventively**: Unnecessary since single-constructor entities are handled correctly. Adding it would be noise without functional benefit.
