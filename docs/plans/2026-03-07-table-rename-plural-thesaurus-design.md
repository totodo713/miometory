# Design: Table Name Pluralization + Thesaurus Rename (pattern→rule, entry→rule)

Ref: [Issue #126](https://github.com/totodo713/miometory/issues/126)
Base plan: `docs/plans/2026-03-02-table-rename-plural-thesaurus-plan.md`

## Decisions

| Item | Decision |
|------|----------|
| `event_store` / `snapshot_store` | Keep as-is (ES infrastructure convention) |
| `pattern` → `rule` | Rename |
| `entry` → `rule` (holiday_calendar) | Rename |
| `preset` | Keep as-is |
| `daily_rejection_log` | Pluralize only (`daily_rejection_logs`) |
| `audit_log` (V2) | DROP. Migrate `JdbcAuditLogger` to `audit_logs` |
| Java domain class names | Rename to match table names |
| Package names (`fiscalyear`, `monthlyperiod`) | Keep as-is |
| Event store data | Dev seed only — rewrite `data-dev.sql`, no backward compat needed |
| Migration version | **V34** (V31–V33 already exist) |

## Approach

Bottom-up, single PR:

```
V34 migration → JdbcAuditLogger migration → SQL string update →
Domain class rename → API path change → Frontend update → i18n
```

## Step 1: Flyway Migration V34

File: `backend/src/main/resources/db/migration/V34__rename_tables_to_plural_and_thesaurus.sql`

1. `DROP TABLE IF EXISTS audit_log` — remove deprecated table
2. `ALTER TABLE ... RENAME TO` — 11 tables (children first for FK order)
3. `ALTER INDEX ... RENAME TO` — index names to match new table names
4. `ALTER TABLE ... RENAME CONSTRAINT` — constraint names to match

Table mapping:

| # | Old | New | Type |
|---|-----|-----|------|
| 1 | `holiday_calendar_entry` | `holiday_calendar_rules` | plural + thesaurus |
| 2 | `holiday_calendar_entry_preset` | `holiday_calendar_rule_presets` | plural + thesaurus |
| 3 | `holiday_calendar` | `holiday_calendars` | plural |
| 4 | `holiday_calendar_preset` | `holiday_calendar_presets` | plural |
| 5 | `fiscal_year_pattern` | `fiscal_year_rules` | plural + thesaurus |
| 6 | `fiscal_year_pattern_preset` | `fiscal_year_rule_presets` | plural + thesaurus |
| 7 | `monthly_period_pattern` | `monthly_period_rules` | plural + thesaurus |
| 8 | `monthly_period_pattern_preset` | `monthly_period_rule_presets` | plural + thesaurus |
| 9 | `daily_rejection_log` | `daily_rejection_logs` | plural |
| 10 | `tenant` | `tenants` | plural |
| 11 | `organization` | `organizations` | plural |
| 12 | `audit_log` | DROP | deprecated |

Unchanged: `event_store`, `snapshot_store`, `system_default_settings` (already plural)

Note: PostgreSQL `ALTER TABLE RENAME` is metadata-only (instant). FK references auto-track, but index/constraint names require manual rename.

## Step 2: JdbcAuditLogger → audit_logs

File: `backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java`

Column mapping:

| audit_log (old) | audit_logs (new) | Strategy |
|---|---|---|
| `tenant_id` | — | Move into `details` JSONB |
| `user_id` | `user_id` | Direct |
| `action` | `event_type` | Column rename |
| `resource_type` | — | Move into `details` JSONB |
| `resource_id` | — | Move into `details` JSONB |
| `details` | `details` | Merge with above fields in JSONB |
| — | `ip_address` | `null` (server-side, unavailable) |
| — | `retention_days` | Default `365` |
| `created_at` | `timestamp` | Adapt to existing schema |

## Step 3: Backend SQL String Update (~15 files)

Replace table names in all SQL string literals. Key caution: only replace table name contexts (`FROM`, `INTO`, `UPDATE`, `JOIN`), not column names like `tenant_id`.

Primary files:
- `TenantRepository` — `tenant` → `tenants`
- `OrganizationRepository` — `organization` → `organizations`
- `FiscalYearPatternRepository` — `fiscal_year_pattern` → `fiscal_year_rules`
- `MonthlyPeriodPatternRepository` — `monthly_period_pattern` → `monthly_period_rules`
- `HolidayCalendarEntryRepository` — `holiday_calendar_entry` → `holiday_calendar_rules`, `holiday_calendar` → `holiday_calendars`
- `JdbcDailyRejectionLogRepository` — `daily_rejection_log` → `daily_rejection_logs`
- `AdminTenantService`, `AdminOrganizationService`, `AdminMasterDataService`, `SystemSettingsService`
- `data-dev.sql` — all INSERT statements
- `R__test_infrastructure.sql` — test data

## Step 4: Domain Class Rename (~13 classes + ~9 tests)

### Pattern → Rule

| Old Class | New Class |
|---|---|
| `FiscalYearPattern` | `FiscalYearRule` |
| `FiscalYearPatternCreated` | `FiscalYearRuleCreated` |
| `FiscalYearPatternId` | `FiscalYearRuleId` |
| `FiscalYearPatternRepository` | `FiscalYearRuleRepository` |
| `FiscalYearPatternController` | `FiscalYearRuleController` |
| `MonthlyPeriodPattern` | `MonthlyPeriodRule` |
| `MonthlyPeriodPatternCreated` | `MonthlyPeriodRuleCreated` |
| `MonthlyPeriodPatternId` | `MonthlyPeriodRuleId` |
| `MonthlyPeriodPatternRepository` | `MonthlyPeriodRuleRepository` |
| `MonthlyPeriodPatternController` | `MonthlyPeriodRuleController` |
| `SystemDefaultFiscalYearPattern` | `SystemDefaultFiscalYearRule` |
| `SystemDefaultMonthlyPeriodPattern` | `SystemDefaultMonthlyPeriodRule` |
| `TenantDefaultPatternsAssigned` | `TenantDefaultRulesAssigned` |

### Entry → Rule

| Old Class | New Class |
|---|---|
| `HolidayCalendarEntryRepository` | `HolidayCalendarRuleRepository` |

### Reference Updates

Import/type/variable updates needed in:
- Controllers: `AdminTenantController`, `OrganizationController`, `AdminMasterDataController`, `TenantSettingsController`
- Services: `DateInfoService`, `HolidayResolutionService`, `AdminOrganizationService`
- Domain: `Tenant.java`, `Organization.java`
- Config: `SecurityConfig.kt`
- Tests: ~15 files (controllers, domain, fixtures, integration)

### Event Store Seed Data

Update `data-dev.sql` event_store entries: `FiscalYearPatternCreated` → `FiscalYearRuleCreated`, etc.

## Step 5: API Endpoint Path Changes

| Old Path | New Path | Controller |
|---|---|---|
| `/api/v1/tenants/{tenantId}/fiscal-year-patterns` | `/api/v1/tenants/{tenantId}/fiscal-year-rules` | `FiscalYearRuleController` |
| `/api/v1/tenants/{tenantId}/monthly-period-patterns` | `/api/v1/tenants/{tenantId}/monthly-period-rules` | `MonthlyPeriodRuleController` |
| `/api/admin/master-data/fiscal-year-pattern-presets` | `/api/admin/master-data/fiscal-year-rule-presets` | `AdminMasterDataController` |
| `/api/admin/master-data/monthly-period-pattern-presets` | `/api/admin/master-data/monthly-period-rule-presets` | `AdminMasterDataController` |
| `/api/admin/master-data/holiday-calendar-entry-presets` | `/api/admin/master-data/holiday-calendar-rule-presets` | `AdminMasterDataController` |

Also update: `SecurityConfig.kt` path patterns, `openapi.yaml` spec.

## Step 6: Frontend Update

### File Renames

| Old | New |
|---|---|
| `FiscalYearPatternForm.tsx` | `FiscalYearRuleForm.tsx` |
| `FiscalYearPatternForm.test.tsx` | `FiscalYearRuleForm.test.tsx` |
| `MonthlyPeriodPatternForm.tsx` | `MonthlyPeriodRuleForm.tsx` |
| `MonthlyPeriodPatternForm.test.tsx` | `MonthlyPeriodRuleForm.test.tsx` |

### api.ts Updates

- Path strings: `fiscal-year-patterns` → `fiscal-year-rules`, etc.
- Method names: `listFiscalYearPatterns` → `listFiscalYearRules`, etc.
- Type names: `FiscalYearPatternOption` → `FiscalYearRuleOption`, etc.

### Component Reference Updates

- `MasterDataTabs.tsx` — import paths and component names
- `TenantSettingsSection.tsx` — pattern references
- Admin pages: `organizations/page.tsx`, `tenants/page.tsx`

### E2E Test Updates

- `tenant-onboarding.spec.ts` — API mock paths and response types
- `settings-inheritance.spec.ts` — same
- `fixtures/auth.ts` — shared mock helpers
- Inline `page.route()` mocks in spec files

## Step 7: i18n Key Check

Review `frontend/messages/en.json` and `ja.json` for "pattern" keys. Update display text values where "rule" is more appropriate. Both files must be updated together (key-parity test enforces this).

## Verification

1. `./gradlew flywayMigrate` — migration succeeds
2. `./gradlew build` — no compile errors
3. `./gradlew test` — all pass
4. `npm run build` — no errors
5. `npm test` — all pass
6. `npx playwright test --project=chromium` — E2E pass
7. `./gradlew spotlessCheck` + `npx biome ci` — lint/format pass

## Risks

- **Wide blast radius**: ~30 backend files, ~15 frontend files, ~15 test files. Full test suite catches regressions.
- **Breaking API change**: Internal-only API, frontend updated simultaneously. No external consumers.
- **SQL string replacement**: Risk of replacing column names (`tenant_id`). Mitigated by context-aware replacement and build verification.
