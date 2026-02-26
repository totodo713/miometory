# QA UX Guardian Memory (Backend Context)

## Permission System
- Roles: USER, SUPERVISOR, TENANT_ADMIN, SYSTEM_ADMIN
- Role IDs seeded in V18 migration (USER=00000000-...-02, ADMIN=00000000-...-01)
- Permissions stored in role_permissions table
- Backend enforces permissions at API level (returns 403 for forbidden)
- Frontend ForbiddenError catches 403 and shows AccessDenied component

## Test Patterns
- Integration tests extend `IntegrationTestBase` (Testcontainers)
- Admin tests extend `AdminIntegrationTestBase`
- Use `SecurityMockMvcRequestPostProcessors.user()` for auth in tests
- Test data uses UUID-based unique values to prevent constraint violations
