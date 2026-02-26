# Build Integrity Verifier — Project Memory

## Project: Miometry (worker-mio worktree)

### Build Commands
- Backend: `./gradlew build` in `backend/` (Kotlin 2.3.0 + Java 21, Spring Boot 3.5.9)
- Frontend: `npm run build` / `npx biome ci` in `frontend/` (Next.js 16.x, TypeScript strict mode)
- Backend tests: `./gradlew test jacocoTestReport` — coverage target 80%+ LINE per package
- Frontend tests: `npm test -- --run` (Vitest + RTL)
- Format check: backend `./gradlew checkFormat && ./gradlew detekt`, frontend `npx biome ci`

### Key Architecture Points
- Flyway migrations in `backend/src/main/resources/db/migration/` — sequential V{N}__ prefix required
- Test migrations (repeatable): `backend/src/test/resources/db/testdata/R__test_*.sql`
- IntegrationTestBase adds `classpath:db/testdata` to flyway locations for tests
- Permission system: role_permissions table, `@PreAuthorize("hasPermission(null, 'resource.action')")`
- V18 seeds SYSTEM_ADMIN, TENANT_ADMIN, SUPERVISOR roles with fixed UUIDs (aa000000-...)
- CustomPermissionEvaluator caches DB lookups 60s — migration changes take effect only for new sessions

### Frontend Build Patterns
- `@/` alias resolves to `frontend/app/`
- All API errors extend `ApiError` base class; `ForbiddenError` (403) and `UnauthorizedError` (401) exported from `services/api.ts`
- i18n: next-intl, messages in `frontend/messages/{en,ja}.json`
- AdminContext (from AdminProvider) carries: role, permissions[], tenantId, tenantName, memberId
- biome.json: `noUnusedImports: error` — unused imports will fail CI

### Recurring Risk Patterns
- Dynamic translation key access (e.g. `tn(card.titleKey)`) does not cause runtime errors if all keys exist, but next-intl does not type-check dynamic keys at compile time — manual verification required
- Permission cache in CustomPermissionEvaluator means migration-based permission changes apply only after cache TTL (60s) or new sessions — not a build risk, but deployment sequencing concern
- SYSTEM_ADMIN role has no tenant_id (no member record) — endpoints that call `resolveUserTenantId()` will throw if accessed by SYSTEM_ADMIN; this is intentional enforcement

### Confirmed Safe Patterns (from permission boundary UX review)
- Adding optional props (`onForbidden?`) to existing component interfaces is backward-compatible
- Inserting error-class inheritance in api.ts before existing error classes is safe
- V22 DELETE migration on role_permissions uses subquery against roles/permissions tables — no hardcoded UUIDs, idempotent on repeated runs if rows already absent
