# Table Rename Pluralization + Thesaurus Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Pluralize all DB table names and rename pattern→rule, entry→rule across the full stack.

**Architecture:** Flyway migration renames tables → backend code updates (SQL strings, domain classes, API paths) → frontend updates (api client, components, i18n, E2E tests). Single PR, bottom-up approach.

**Tech Stack:** PostgreSQL/Flyway, Spring Boot/Kotlin/Java, Next.js/React/TypeScript, Playwright

**Design Doc:** `docs/plans/2026-03-07-table-rename-plural-thesaurus-design.md`
**Issue:** [#126](https://github.com/totodo713/miometory/issues/126)

---

### Task 1: Flyway Migration V34

**Files:**
- Create: `backend/src/main/resources/db/migration/V34__rename_tables_to_plural_and_thesaurus.sql`

**Step 1: Create the migration file**

```sql
-- V34: Rename tables to plural form + thesaurus (pattern→rule, entry→rule)
-- Ref: https://github.com/totodo713/miometory/issues/126

-- ============================================================
-- 1. DROP deprecated audit_log (V2)
-- ============================================================
DROP TABLE IF EXISTS audit_log;

-- ============================================================
-- 2. RENAME TABLES (children first for FK ordering)
-- ============================================================

-- Holiday calendar children → parents
ALTER TABLE holiday_calendar_entry RENAME TO holiday_calendar_rules;
ALTER TABLE holiday_calendar_entry_preset RENAME TO holiday_calendar_rule_presets;
ALTER TABLE holiday_calendar RENAME TO holiday_calendars;
ALTER TABLE holiday_calendar_preset RENAME TO holiday_calendar_presets;

-- Fiscal year
ALTER TABLE fiscal_year_pattern RENAME TO fiscal_year_rules;
ALTER TABLE fiscal_year_pattern_preset RENAME TO fiscal_year_rule_presets;

-- Monthly period
ALTER TABLE monthly_period_pattern RENAME TO monthly_period_rules;
ALTER TABLE monthly_period_pattern_preset RENAME TO monthly_period_rule_presets;

-- Daily rejection log
ALTER TABLE daily_rejection_log RENAME TO daily_rejection_logs;

-- Core tables (renamed last — many FKs point to these)
ALTER TABLE tenant RENAME TO tenants;
ALTER TABLE organization RENAME TO organizations;

-- ============================================================
-- 3. RENAME INDEXES
-- ============================================================

-- tenant (V2)
ALTER INDEX idx_tenant_code RENAME TO idx_tenants_code;
ALTER INDEX idx_tenant_status RENAME TO idx_tenants_status;

-- organization (V2)
ALTER INDEX idx_organization_tenant_id RENAME TO idx_organizations_tenant_id;
ALTER INDEX idx_organization_parent_id RENAME TO idx_organizations_parent_id;
ALTER INDEX idx_organization_status RENAME TO idx_organizations_status;

-- organization pattern refs (V3)
ALTER INDEX idx_organization_fiscal_year_pattern RENAME TO idx_organizations_fiscal_year_rule;
ALTER INDEX idx_organization_monthly_period_pattern RENAME TO idx_organizations_monthly_period_rule;

-- fiscal_year_pattern (V2)
ALTER INDEX idx_fiscal_year_pattern_tenant_id RENAME TO idx_fiscal_year_rules_tenant_id;
ALTER INDEX idx_fiscal_year_pattern_organization_id RENAME TO idx_fiscal_year_rules_organization_id;

-- monthly_period_pattern (V2)
ALTER INDEX idx_monthly_period_pattern_tenant_id RENAME TO idx_monthly_period_rules_tenant_id;
ALTER INDEX idx_monthly_period_pattern_organization_id RENAME TO idx_monthly_period_rules_organization_id;

-- holiday_calendar (V23)
ALTER INDEX idx_hc_tenant_id RENAME TO idx_holiday_calendars_tenant_id;

-- holiday_calendar_entry (V23)
ALTER INDEX idx_hce_calendar_id RENAME TO idx_holiday_calendar_rules_calendar_id;

-- holiday_calendar_entry_preset (V23)
ALTER INDEX idx_hcep_calendar_id RENAME TO idx_holiday_calendar_rule_presets_calendar_id;

-- ============================================================
-- 4. RENAME CONSTRAINTS
-- ============================================================

-- organization (V2)
ALTER TABLE organizations RENAME CONSTRAINT uq_organization_tenant_code TO uq_organizations_tenant_code;
ALTER TABLE organizations RENAME CONSTRAINT chk_organization_level TO chk_organizations_level;

-- fiscal_year_rules (V2)
ALTER TABLE fiscal_year_rules RENAME CONSTRAINT chk_fiscal_year_start_month TO chk_fiscal_year_rules_start_month;
ALTER TABLE fiscal_year_rules RENAME CONSTRAINT chk_fiscal_year_start_day TO chk_fiscal_year_rules_start_day;

-- monthly_period_rules (V2)
ALTER TABLE monthly_period_rules RENAME CONSTRAINT chk_monthly_period_start_day TO chk_monthly_period_rules_start_day;

-- fiscal_year_rule_presets (V23)
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT chk_fyp_preset_start_month TO chk_fyr_preset_start_month;
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT chk_fyp_preset_start_day TO chk_fyr_preset_start_day;
ALTER TABLE fiscal_year_rule_presets RENAME CONSTRAINT uq_fyp_preset_name TO uq_fyr_preset_name;

-- monthly_period_rule_presets (V23)
ALTER TABLE monthly_period_rule_presets RENAME CONSTRAINT chk_mpp_preset_start_day TO chk_mpr_preset_start_day;
ALTER TABLE monthly_period_rule_presets RENAME CONSTRAINT uq_mpp_preset_name TO uq_mpr_preset_name;

-- holiday_calendar_presets (V23)
ALTER TABLE holiday_calendar_presets RENAME CONSTRAINT uq_hc_preset_name TO uq_holiday_calendar_presets_name;

-- holiday_calendars (V23)
ALTER TABLE holiday_calendars RENAME CONSTRAINT uq_hc_tenant_name TO uq_holiday_calendars_tenant_name;

-- holiday_calendar_rules (V23 — formerly holiday_calendar_entry)
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_entry_type TO chk_hcr_entry_type;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_month TO chk_hcr_month;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_day TO chk_hcr_day;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_nth TO chk_hcr_nth;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_dow TO chk_hcr_dow;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_fixed TO chk_hcr_fixed;
ALTER TABLE holiday_calendar_rules RENAME CONSTRAINT chk_hce_nth_weekday TO chk_hcr_nth_weekday;

-- holiday_calendar_rule_presets (V23 — formerly holiday_calendar_entry_preset)
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_entry_type TO chk_hcrp_entry_type;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_month TO chk_hcrp_month;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_day TO chk_hcrp_day;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_nth TO chk_hcrp_nth;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_dow TO chk_hcrp_dow;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_fixed TO chk_hcrp_fixed;
ALTER TABLE holiday_calendar_rule_presets RENAME CONSTRAINT chk_hcep_nth_weekday TO chk_hcrp_nth_weekday;

-- daily_rejection_logs (V15)
ALTER TABLE daily_rejection_logs RENAME CONSTRAINT uq_daily_rejection_member_date TO uq_daily_rejection_logs_member_date;
```

**Step 2: Commit**

```bash
git add backend/src/main/resources/db/migration/V34__rename_tables_to_plural_and_thesaurus.sql
git commit -m "refactor(db): add V34 migration for table rename pluralization + thesaurus"
```

---

### Task 2: JdbcAuditLogger → audit_logs Migration

**Files:**
- Modify: `backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java`
- Modify: `backend/src/main/java/com/worklog/eventsourcing/AuditLogger.java` (interface — check if method signature changes)

**Step 1: Read current JdbcAuditLogger implementation**

Read `backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java` to understand the current INSERT.

Current SQL (lines 36-39):
```java
INSERT INTO audit_log (tenant_id, user_id, action, resource_type, resource_id, details, created_at)
VALUES (?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
```

**Step 2: Update JdbcAuditLogger to write to audit_logs**

Replace the `log()` method body to target `audit_logs` with column mapping:
- `action` → `event_type`
- `tenant_id`, `resource_type`, `resource_id` → merged into `details` JSONB
- Add `ip_address` (null) and `retention_days` (365)
- `created_at` → `timestamp`

New SQL:
```java
String detailsWithContext = mergeDetails(tenantId, resourceType, resourceId, details);
jdbcTemplate.update("""
    INSERT INTO audit_logs (user_id, event_type, ip_address, timestamp, details, retention_days)
    VALUES (?, ?, NULL, CURRENT_TIMESTAMP, ?::jsonb, 365)
    """, userId, action, detailsWithContext);
```

Add a private helper to merge context into details JSONB:
```java
private String mergeDetails(UUID tenantId, String resourceType, UUID resourceId, Map<String, Object> details) {
    var merged = new java.util.HashMap<>(details != null ? details : Map.of());
    merged.put("tenantId", tenantId != null ? tenantId.toString() : null);
    merged.put("resourceType", resourceType);
    merged.put("resourceId", resourceId != null ? resourceId.toString() : null);
    try {
        return objectMapper.writeValueAsString(merged);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        return "{}";
    }
}
```

**Step 3: Update AuditLogger tests**

Read and update `backend/src/test/kotlin/com/worklog/eventsourcing/AuditLoggerTest.kt` to verify the new SQL and column mapping.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java
git add backend/src/test/kotlin/com/worklog/eventsourcing/AuditLoggerTest.kt
git commit -m "refactor: migrate JdbcAuditLogger from audit_log to audit_logs table"
```

---

### Task 3: Backend Repository SQL String Updates

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java` (lines 91-102, 169)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java` (lines 92-104, 176)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/FiscalYearPatternRepository.java` (lines 86, 109-114)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/MonthlyPeriodPatternRepository.java` (lines 86, 109-113)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/HolidayCalendarEntryRepository.java` (lines 19-26)
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyRejectionLogRepository.java` (lines 43-50, 62-66, 78-83)

**Step 1: Update TenantRepository SQL strings**

Replace table name in SQL literals:
- `INSERT INTO tenant (` → `INSERT INTO tenants (`
- `SELECT ... FROM tenant WHERE` → `SELECT ... FROM tenants WHERE`

**CAUTION**: Do NOT change column names like `tenant_id` — only table name references after FROM/INTO/UPDATE/JOIN.

**Step 2: Update OrganizationRepository SQL strings**

- `INSERT INTO organization` → `INSERT INTO organizations`
- `SELECT ... FROM organization WHERE` → `SELECT ... FROM organizations WHERE`

**Step 3: Update FiscalYearPatternRepository SQL strings**

- `SELECT id FROM fiscal_year_pattern WHERE` → `SELECT id FROM fiscal_year_rules WHERE`
- `INSERT INTO fiscal_year_pattern (` → `INSERT INTO fiscal_year_rules (`

**Step 4: Update MonthlyPeriodPatternRepository SQL strings**

- `SELECT id FROM monthly_period_pattern WHERE` → `SELECT id FROM monthly_period_rules WHERE`
- `INSERT INTO monthly_period_pattern (` → `INSERT INTO monthly_period_rules (`

**Step 5: Update HolidayCalendarEntryRepository SQL strings**

- `FROM holiday_calendar_entry e` → `FROM holiday_calendar_rules e`
- `JOIN holiday_calendar c` → `JOIN holiday_calendars c`

**Step 6: Update JdbcDailyRejectionLogRepository SQL strings**

- All `daily_rejection_log` → `daily_rejection_logs` (3 SQL statements)

**Step 7: Commit**

```bash
cd backend
git add src/main/java/com/worklog/infrastructure/repository/
git commit -m "refactor: update repository SQL strings for renamed tables"
```

---

### Task 4: Backend Service SQL String Updates

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java` (lines 43-44, 146-147, 245-254, 356, 362-366, 395, 462, 477, 491, 515-516)
- Modify: `backend/src/main/java/com/worklog/application/service/AdminTenantService.java` (lines 49-50, 85, 96-100, 144, 154)
- Modify: `backend/src/main/java/com/worklog/application/service/AdminOrganizationService.java` (lines 56-64, 140-143, 229, 290, 344, 547-549, 557-559)
- Modify: `backend/src/main/java/com/worklog/application/service/SystemSettingsService.java` (if any direct SQL)

**Step 1: Update AdminMasterDataService SQL strings**

Replace all table name references:
- `fiscal_year_pattern_preset` → `fiscal_year_rule_presets` (lines 43, 44, 462 + INSERT/UPDATE statements)
- `monthly_period_pattern_preset` → `monthly_period_rule_presets` (lines 146, 147, 477 + INSERT/UPDATE)
- `holiday_calendar_preset` → `holiday_calendar_presets` (lines 356, 395, 491)
- `holiday_calendar_entry_preset` → `holiday_calendar_rule_presets` (lines 245-254, 362-366, 515-516)

**Step 2: Update AdminTenantService SQL strings**

- `FROM tenant WHERE` → `FROM tenants WHERE`
- `FROM organization WHERE` → `FROM organizations WHERE`
- `INSERT INTO tenant` → `INSERT INTO tenants` (if present in projection)

**Step 3: Update AdminOrganizationService SQL strings**

- `organization` → `organizations` in all FROM/JOIN contexts
- `fiscal_year_pattern` → `fiscal_year_rules` (lines 547-549)
- `monthly_period_pattern` → `monthly_period_rules` (lines 557-559)

**Step 4: Update SystemSettingsService (if direct SQL exists)**

Read file and update any SQL string references.

**Step 5: Commit**

```bash
cd backend
git add src/main/java/com/worklog/application/service/
git commit -m "refactor: update service SQL strings for renamed tables"
```

---

### Task 5: Domain Class Renames — FiscalYearPattern → FiscalYearRule

**Files (git mv):**
- `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPattern.java` → `FiscalYearRule.java`
- `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPatternCreated.java` → `FiscalYearRuleCreated.java`
- `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPatternId.java` → `FiscalYearRuleId.java`
- `backend/src/main/java/com/worklog/infrastructure/repository/FiscalYearPatternRepository.java` → `FiscalYearRuleRepository.java`
- `backend/src/main/java/com/worklog/api/FiscalYearPatternController.java` → `FiscalYearRuleController.java`

**Step 1: Rename files with git mv**

```bash
cd backend/src/main/java/com/worklog
git mv domain/fiscalyear/FiscalYearPattern.java domain/fiscalyear/FiscalYearRule.java
git mv domain/fiscalyear/FiscalYearPatternCreated.java domain/fiscalyear/FiscalYearRuleCreated.java
git mv domain/fiscalyear/FiscalYearPatternId.java domain/fiscalyear/FiscalYearRuleId.java
git mv infrastructure/repository/FiscalYearPatternRepository.java infrastructure/repository/FiscalYearRuleRepository.java
git mv api/FiscalYearPatternController.java api/FiscalYearRuleController.java
```

**Step 2: Update class names and internal references in each file**

In each renamed file, replace:
- `FiscalYearPattern` → `FiscalYearRule` (class name, constructor, references)
- `FiscalYearPatternCreated` → `FiscalYearRuleCreated`
- `FiscalYearPatternId` → `FiscalYearRuleId`
- `"FiscalYearPattern"` → `"FiscalYearRule"` (aggregate type string in FiscalYearRule.java line 171)
- `CreateFiscalYearPatternRequest` → `CreateFiscalYearRuleRequest` (DTO in controller)

**Step 3: Rename test files**

```bash
cd backend/src/test/kotlin/com/worklog
git mv domain/fiscalyear/FiscalYearPatternTest.kt domain/fiscalyear/FiscalYearRuleTest.kt
git mv api/FiscalYearPatternControllerTest.kt api/FiscalYearRuleControllerTest.kt
git mv api/FiscalYearPatternControllerAuthTest.kt api/FiscalYearRuleControllerAuthTest.kt
git mv fixtures/FiscalYearPatternFixtures.kt fixtures/FiscalYearRuleFixtures.kt
```

**Step 4: Update test file contents**

Replace all `FiscalYearPattern` → `FiscalYearRule` references in each test file.

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename FiscalYearPattern to FiscalYearRule"
```

---

### Task 6: Domain Class Renames — MonthlyPeriodPattern → MonthlyPeriodRule

**Files (git mv):**
- `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPattern.java` → `MonthlyPeriodRule.java`
- `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPatternCreated.java` → `MonthlyPeriodRuleCreated.java`
- `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPatternId.java` → `MonthlyPeriodRuleId.java`
- `backend/src/main/java/com/worklog/infrastructure/repository/MonthlyPeriodPatternRepository.java` → `MonthlyPeriodRuleRepository.java`
- `backend/src/main/java/com/worklog/api/MonthlyPeriodPatternController.java` → `MonthlyPeriodRuleController.java`

**Step 1: Rename files with git mv**

```bash
cd backend/src/main/java/com/worklog
git mv domain/monthlyperiod/MonthlyPeriodPattern.java domain/monthlyperiod/MonthlyPeriodRule.java
git mv domain/monthlyperiod/MonthlyPeriodPatternCreated.java domain/monthlyperiod/MonthlyPeriodRuleCreated.java
git mv domain/monthlyperiod/MonthlyPeriodPatternId.java domain/monthlyperiod/MonthlyPeriodRuleId.java
git mv infrastructure/repository/MonthlyPeriodPatternRepository.java infrastructure/repository/MonthlyPeriodRuleRepository.java
git mv api/MonthlyPeriodPatternController.java api/MonthlyPeriodRuleController.java
```

**Step 2: Update class names and internal references in each file**

Replace all `MonthlyPeriodPattern` → `MonthlyPeriodRule` (class, constructor, event, aggregate type string at line 132).

**Step 3: Rename test files**

```bash
cd backend/src/test/kotlin/com/worklog
git mv domain/monthlyperiod/MonthlyPeriodPatternTest.kt domain/monthlyperiod/MonthlyPeriodRuleTest.kt
git mv api/MonthlyPeriodPatternControllerTest.kt api/MonthlyPeriodRuleControllerTest.kt
git mv api/MonthlyPeriodPatternControllerAuthTest.kt api/MonthlyPeriodRuleControllerAuthTest.kt
git mv fixtures/MonthlyPeriodPatternFixtures.kt fixtures/MonthlyPeriodRuleFixtures.kt
```

**Step 4: Update test file contents**

Replace all `MonthlyPeriodPattern` → `MonthlyPeriodRule` references.

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename MonthlyPeriodPattern to MonthlyPeriodRule"
```

---

### Task 7: Remaining Domain Class Renames

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/HolidayCalendarEntryRepository.java` → rename to `HolidayCalendarRuleRepository.java`
- Modify: `backend/src/main/java/com/worklog/domain/settings/SystemDefaultFiscalYearPattern.java` → rename to `SystemDefaultFiscalYearRule.java`
- Modify: `backend/src/main/java/com/worklog/domain/settings/SystemDefaultMonthlyPeriodPattern.java` → rename to `SystemDefaultMonthlyPeriodRule.java`
- Modify: `backend/src/main/java/com/worklog/domain/tenant/TenantDefaultPatternsAssigned.java` → rename to `TenantDefaultRulesAssigned.java`

**Step 1: Rename HolidayCalendarEntryRepository**

```bash
cd backend/src/main/java/com/worklog/infrastructure/repository
git mv HolidayCalendarEntryRepository.java HolidayCalendarRuleRepository.java
```

Update class name, inner record `HolidayCalendarEntryRow` → `HolidayCalendarRuleRow`, and all references inside the file.

**Step 2: Rename SystemDefault classes**

```bash
cd backend/src/main/java/com/worklog/domain/settings
git mv SystemDefaultFiscalYearPattern.java SystemDefaultFiscalYearRule.java
git mv SystemDefaultMonthlyPeriodPattern.java SystemDefaultMonthlyPeriodRule.java
```

Update class names and internal references.

**Step 3: Rename TenantDefaultPatternsAssigned**

```bash
cd backend/src/main/java/com/worklog/domain/tenant
git mv TenantDefaultPatternsAssigned.java TenantDefaultRulesAssigned.java
```

Update class name and event type string.

**Step 4: Rename test files**

```bash
cd backend/src/test
git mv java/com/worklog/infrastructure/repository/HolidayCalendarEntryRepositoryTest.java java/com/worklog/infrastructure/repository/HolidayCalendarRuleRepositoryTest.java
```

Update test class name and all `HolidayCalendarEntry` → `HolidayCalendarRule` references.

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename HolidayCalendarEntry, SystemDefault, TenantDefault classes"
```

---

### Task 8: Backend Reference Updates

All files that import or reference the renamed classes need updating.

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/AdminTenantController.java`
- Modify: `backend/src/main/java/com/worklog/api/OrganizationController.java`
- Modify: `backend/src/main/java/com/worklog/api/AdminMasterDataController.java`
- Modify: `backend/src/main/java/com/worklog/api/TenantSettingsController.java`
- Modify: `backend/src/main/java/com/worklog/application/service/DateInfoService.java`
- Modify: `backend/src/main/java/com/worklog/application/service/HolidayResolutionService.java`
- Modify: `backend/src/main/java/com/worklog/application/service/AdminOrganizationService.java`
- Modify: `backend/src/main/java/com/worklog/application/service/SystemSettingsService.java`
- Modify: `backend/src/main/java/com/worklog/domain/tenant/Tenant.java` (lines 33-34, 116, 171-173, 221, 225)
- Modify: `backend/src/main/java/com/worklog/domain/organization/Organization.java` (lines 41-42, 140-142)
- Modify: `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt`
- Modify: All remaining test files that reference old class names

**Step 1: Search and update all imports**

Search all `.java` and `.kt` files for imports containing the old class names:

```bash
cd backend
grep -rl "FiscalYearPattern\|MonthlyPeriodPattern\|HolidayCalendarEntry\|SystemDefaultFiscalYearPattern\|SystemDefaultMonthlyPeriodPattern\|TenantDefaultPatternsAssigned" src/
```

For each file found, update:
- Import statements: `FiscalYearPattern` → `FiscalYearRule`, etc.
- Type references: field types, method parameters, return types
- Variable names: `fiscalYearPattern` → `fiscalYearRule`, `pattern` → `rule` where contextually appropriate
- String literals: `"FiscalYearPatternCreated"` → `"FiscalYearRuleCreated"` in event type mappings

**Step 2: Update Tenant.java**

- Field names at lines 33-34: `defaultFiscalYearPatternId` / `defaultMonthlyPeriodPatternId` — these are UUID fields, keep names as-is for now (column names unchanged) OR rename to `defaultFiscalYearRuleId` / `defaultMonthlyPeriodRuleId`
- Method `assignDefaultPatterns()` → `assignDefaultRules()` at line 116
- Event handler for `TenantDefaultRulesAssigned` at lines 171-173
- Getter `getDefaultFiscalYearPatternId()` → `getDefaultFiscalYearRuleId()`

**NOTE**: Renaming Tenant's pattern fields and methods will cascade to TenantRepository projection SQL (column names `default_fiscal_year_pattern_id` stay unchanged in DB, but Java field names change). Verify the projection mapping handles this correctly.

**Step 3: Update Organization.java**

- Field names at lines 41-42: `fiscalYearPatternId` / `monthlyPeriodPatternId` → `fiscalYearRuleId` / `monthlyPeriodRuleId`
- Method `assignPatterns()` → `assignRules()` at line 140

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: update all backend references for renamed classes"
```

---

### Task 9: API Endpoint Path Changes

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/FiscalYearRuleController.java` (line 25 — already renamed in Task 5)
- Modify: `backend/src/main/java/com/worklog/api/MonthlyPeriodRuleController.java` (line 25)
- Modify: `backend/src/main/java/com/worklog/api/AdminMasterDataController.java` (lines 34-223)
- Modify: `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt` (lines 113-115)
- Modify: `backend/src/main/resources/static/api-docs/openapi.yaml` (if pattern endpoints are documented)

**Step 1: Update FiscalYearRuleController path**

```java
// Line 25: Change @RequestMapping
@RequestMapping("/api/v1/tenants/{tenantId}/fiscal-year-rules")
```

**Step 2: Update MonthlyPeriodRuleController path**

```java
@RequestMapping("/api/v1/tenants/{tenantId}/monthly-period-rules")
```

**Step 3: Update AdminMasterDataController paths**

Replace all endpoint sub-paths:
- `/fiscal-year-patterns` → `/fiscal-year-rules` (lines 34, 46, 55, 64, 72)
- `/monthly-period-patterns` → `/monthly-period-rules` (lines 83, 95, 104, 112, 120)
- `/holiday-calendars/{id}/entries` → `/holiday-calendars/{id}/rules` (lines 181, 187, 205, 223)

**Step 4: Update SecurityConfig.kt paths**

```kotlin
// Lines 113-115
.requestMatchers("/api/v1/tenants/*/fiscal-year-rules/**").authenticated()
.requestMatchers("/api/v1/tenants/*/monthly-period-rules/**").authenticated()
```

**Step 5: Update openapi.yaml (if applicable)**

Check and update any pattern-related endpoint definitions.

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: update API endpoint paths pattern→rule, entry→rule"
```

---

### Task 10: Seed Data Updates (data-dev.sql)

**Files:**
- Modify: `backend/src/main/resources/data-dev.sql`

**Step 1: Update table names in INSERT statements**

Replace all table name references in data-dev.sql:
- `INSERT INTO tenant (` → `INSERT INTO tenants (` (lines 33-46, 1666-1675)
- `INSERT INTO fiscal_year_pattern (` → `INSERT INTO fiscal_year_rules (` (lines 66-80, 1697+)
- `INSERT INTO monthly_period_pattern (` → `INSERT INTO monthly_period_rules (` (lines 86-98, 1710+)
- `INSERT INTO organization (` → `INSERT INTO organizations (` (lines 104-127, 1726+)
- Remove any `INSERT INTO audit_log` statements (deprecated table dropped)

**Step 2: Update event_store event types**

Search for event type strings and update:
- `FiscalYearPatternCreated` → `FiscalYearRuleCreated`
- `MonthlyPeriodPatternCreated` → `MonthlyPeriodRuleCreated`
- `TenantDefaultPatternsAssigned` → `TenantDefaultRulesAssigned`

Also update JSON payload field names if they reference pattern class names.

**Step 3: Update R__test_infrastructure.sql (if exists)**

Search for this file and update any table name references:

```bash
find backend/src -name "R__*" -o -name "*test_infrastructure*"
```

**Step 4: Commit**

```bash
git add backend/src/main/resources/data-dev.sql
git commit -m "refactor: update seed data for renamed tables and event types"
```

---

### Task 11: Backend Build + Test Verification

**Step 1: Run Spotless format**

```bash
cd backend && ./gradlew spotlessApply
```

**Step 2: Run full build**

```bash
cd backend && ./gradlew build
```

Expected: BUILD SUCCESSFUL — all compilation and tests pass.

**Step 3: Fix any failures**

If compilation fails, check for missed references:

```bash
cd backend
grep -r "FiscalYearPattern\|MonthlyPeriodPattern\|HolidayCalendarEntry\b" src/ --include="*.java" --include="*.kt" | grep -v "Rule"
grep -r "\"fiscal_year_pattern\"\|\"monthly_period_pattern\"\|\"holiday_calendar_entry\"\|\"daily_rejection_log\"\|\"audit_log\"" src/ --include="*.java" --include="*.kt" | grep -v "audit_logs\|_id"
```

**Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve remaining backend references after table rename"
```

---

### Task 12: Frontend API Client + Types

**Files:**
- Modify: `frontend/app/services/api.ts` (lines 76-89, 1136-1149, 1173-1282, 1386-1402)
- Modify: `frontend/app/types/masterData.ts` (if type names need changing)

**Step 1: Update api.ts path strings**

Replace all API path strings:
- `fiscal-year-patterns` → `fiscal-year-rules` (lines 1175, 1178, 1197, 1201, 1203, 1205, 1207)
- `monthly-period-patterns` → `monthly-period-rules` (lines 1176, 1180, 1219, 1223, 1225, 1227, 1229)
- `/entries` → `/rules` in holiday calendar sub-resource paths (lines 1253, 1265, 1278, 1280)
- `effective-patterns` → `effective-rules` (line 1148, if applicable)
- `settings/patterns` → `settings/rules` (line 1052, if applicable)

**Step 2: Update api.ts method names**

- `listFiscalYearPatterns` → `listFiscalYearRules`
- `createFiscalYearPattern` → `createFiscalYearRule`
- `listMonthlyPeriodPatterns` → `listMonthlyPeriodRules`
- `createMonthlyPeriodPattern` → `createMonthlyPeriodRule`
- `getEffectivePatterns` → `getEffectiveRules`
- `getDefaultPatterns` → `getDefaultRules`
- `updateDefaultPatterns` → `updateDefaultRules`
- `updatePatterns` → `updateRules`
- Namespace `patterns` → `rules`

**Step 3: Update api.ts type interfaces**

- `FiscalYearPatternOption` → `FiscalYearRuleOption` (lines 76-82)
- `MonthlyPeriodPatternOption` → `MonthlyPeriodRuleOption` (lines 84-89)
- `SystemDefaultPatterns` → `SystemDefaultRules` (line 1386)
- `EffectivePatterns` → `EffectiveRules` (line 1397)
- `HolidayEntryRow` → `HolidayRuleRow` (in masterData.ts line 32-42)

**Step 4: Update masterData.ts types (if needed)**

Check if `HolidayEntryRow` needs renaming to `HolidayRuleRow`.

**Step 5: Commit**

```bash
cd frontend
git add app/services/api.ts app/types/masterData.ts
git commit -m "refactor: update frontend API client paths and types for rule rename"
```

---

### Task 13: Frontend Component Renames

**Files (git mv):**
- `frontend/app/components/admin/FiscalYearPatternForm.tsx` → `FiscalYearRuleForm.tsx`
- `frontend/app/components/admin/MonthlyPeriodPatternForm.tsx` → `MonthlyPeriodRuleForm.tsx`
- `frontend/tests/unit/components/admin/FiscalYearPatternForm.test.tsx` → `FiscalYearRuleForm.test.tsx`
- `frontend/tests/unit/components/admin/MonthlyPeriodPatternForm.test.tsx` → `MonthlyPeriodRuleForm.test.tsx`

**Step 1: Rename component files**

```bash
cd frontend
git mv app/components/admin/FiscalYearPatternForm.tsx app/components/admin/FiscalYearRuleForm.tsx
git mv app/components/admin/MonthlyPeriodPatternForm.tsx app/components/admin/MonthlyPeriodRuleForm.tsx
git mv tests/unit/components/admin/FiscalYearPatternForm.test.tsx tests/unit/components/admin/FiscalYearRuleForm.test.tsx
git mv tests/unit/components/admin/MonthlyPeriodPatternForm.test.tsx tests/unit/components/admin/MonthlyPeriodRuleForm.test.tsx
```

**Step 2: Update component file contents**

In each file, replace:
- `FiscalYearPatternForm` → `FiscalYearRuleForm` (export name, props interface)
- `FiscalYearPatternFormProps` → `FiscalYearRuleFormProps`
- `MonthlyPeriodPatternForm` → `MonthlyPeriodRuleForm`
- `MonthlyPeriodPatternFormProps` → `MonthlyPeriodRuleFormProps`
- API method calls: `createFiscalYearPattern` → `createFiscalYearRule`, etc.
- Update `api.admin.patterns.` → `api.admin.rules.` namespace references

**Step 3: Update HolidayEntryForm (if renaming)**

If `HolidayEntryForm.tsx` should become `HolidayRuleForm.tsx`:
```bash
git mv app/components/admin/HolidayEntryForm.tsx app/components/admin/HolidayRuleForm.tsx
```

Update all internal references.

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename frontend pattern/entry components to rule"
```

---

### Task 14: Frontend Page + Component Reference Updates

**Files:**
- Modify: `frontend/app/components/admin/MasterDataTabs.tsx` (lines 12-16)
- Modify: `frontend/app/components/admin/TenantSettingsSection.tsx` (lines 38-40, 69-72)
- Modify: `frontend/app/admin/organizations/page.tsx` (lines 5-7, 116-191)
- Modify: `frontend/app/admin/master-data/page.tsx` (lines 5-13)
- Modify: `frontend/app/admin/tenants/page.tsx` (if pattern references exist)

**Step 1: Update MasterDataTabs**

Update i18n keys if changing from `tabs.fiscalYearPatterns` → `tabs.fiscalYearRules`, etc.

**Step 2: Update TenantSettingsSection**

- `api.admin.patterns.listFiscalYearPatterns(tenantId)` → `api.admin.rules.listFiscalYearRules(tenantId)`
- `api.admin.patterns.listMonthlyPeriodPatterns(tenantId)` → `api.admin.rules.listMonthlyPeriodRules(tenantId)`
- `api.admin.tenantSettings.getDefaultPatterns()` → `api.admin.tenantSettings.getDefaultRules()`

**Step 3: Update organizations page**

- Import: `FiscalYearPatternForm` → `FiscalYearRuleForm`
- Import: `MonthlyPeriodPatternForm` → `MonthlyPeriodRuleForm`
- API calls: `api.admin.patterns.*` → `api.admin.rules.*`
- `api.admin.organizations.getEffectivePatterns` → `getEffectiveRules`
- `api.admin.organizations.assignPatterns` → `assignRules`

**Step 4: Update master-data page**

- Import paths for renamed components

**Step 5: Commit**

```bash
cd frontend
git add app/components/admin/ app/admin/
git commit -m "refactor: update frontend page and component references for rule rename"
```

---

### Task 15: i18n Key Updates

**Files:**
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`

**Step 1: Update en.json pattern-related keys and values**

Replace display text values (not key names, unless the key itself contains "pattern"):
- `"fiscalYearPattern"` section (line 1034-1046): Keep key structure, update values if "Pattern" → "Rule" in user-facing text
- `"monthlyPeriodPattern"` section (line 1047-1059): Same
- `"patternSettings"` (line 742) → `"ruleSettings"` : "Rule Settings"
- `"fiscalYearPattern"` (line 746) → value "Fiscal Year Rule"
- `"monthlyPeriodPattern"` (line 747) → value "Monthly Period Rule"
- Tabs: `"fiscalYearPatterns"` (line 1083) → value "Fiscal Year Rules"
- Tabs: `"monthlyPeriodPatterns"` (line 1084) → value "Monthly Period Rules"

**Decision point**: Determine whether i18n key NAMES should change (e.g. `admin.fiscalYearPattern` → `admin.fiscalYearRule`) or just values. Changing key names requires updating all `useTranslations()` call sites. For minimum blast radius, update values only and leave key names as-is.

**Step 2: Update ja.json with matching changes**

- `"年度パターン"` → `"年度ルール"` (or keep as domain-appropriate Japanese term)
- `"会計年度パターン"` → `"会計年度ルール"`
- `"締め日パターン"` → `"締め日ルール"`
- `"月次期間パターン"` → `"月次期間ルール"`

**IMPORTANT**: Both en.json and ja.json must be updated together — key-parity test enforces matching keys.

**Step 3: Run Biome format**

```bash
cd frontend && npx biome check --write messages/en.json messages/ja.json
```

**Step 4: Commit**

```bash
cd frontend
git add messages/en.json messages/ja.json
git commit -m "refactor: update i18n display text from pattern to rule"
```

---

### Task 16: E2E Test Updates

**Files:**
- Modify: `frontend/tests/e2e/scenarios/master-data.spec.ts`
- Modify: `frontend/tests/e2e/fixtures/auth.ts` (if mock helpers reference pattern paths)
- Modify: Any other E2E spec files referencing pattern/entry API paths or UI text

**Step 1: Search for all E2E files referencing patterns**

```bash
cd frontend
grep -rl "pattern\|Pattern\|fiscal-year-pattern\|monthly-period-pattern\|entry" tests/e2e/ --include="*.ts"
```

**Step 2: Update master-data.spec.ts**

- UI text selectors: `"Create Fiscal Year"` → verify against updated en.json
- Tab selectors: update if tab labels changed
- API mock routes: `fiscal-year-patterns` → `fiscal-year-rules`

**Step 3: Update auth.ts fixtures (if needed)**

- Check `mockProjectsApi`, `mockCalendarApi` etc. for pattern-related mock paths

**Step 4: Update inline page.route() mocks**

Search spec files for `page.route()` calls containing old API paths.

**Step 5: Commit**

```bash
cd frontend
git add tests/e2e/
git commit -m "refactor: update E2E tests for rule rename"
```

---

### Task 17: Frontend Build + Test Verification

**Step 1: Run TypeScript type check**

```bash
cd frontend && npx tsc --noEmit
```

**Step 2: Run Biome lint**

```bash
cd frontend && npx biome ci
```

**Step 3: Run unit tests**

```bash
cd frontend && npm test -- --run
```

**Step 4: Run build**

```bash
cd frontend && npm run build
```

**Step 5: Fix any failures**

Search for missed references:

```bash
cd frontend
grep -r "FiscalYearPattern\|MonthlyPeriodPattern\|HolidayEntry\b" app/ tests/ --include="*.ts" --include="*.tsx" | grep -v "Rule"
grep -r "fiscal-year-patterns\|monthly-period-patterns\|/entries" app/ tests/ --include="*.ts" --include="*.tsx"
```

**Step 6: Commit fixes**

```bash
git add -A
git commit -m "fix: resolve remaining frontend references after rule rename"
```

---

### Task 18: Full Verification + Final Commit

**Step 1: Backend full test**

```bash
cd backend && ./gradlew spotlessApply && ./gradlew build
```

**Step 2: Frontend full test**

```bash
cd frontend && npx biome check --write . && npm test -- --run && npm run build
```

**Step 3: E2E tests (if devcontainer is running)**

```bash
cd frontend && npx playwright test --project=chromium
```

**Step 4: Squash or organize commits (optional)**

If commits are clean, leave as-is. If there were fix-up commits, consider interactive rebase.

**Step 5: Push and create PR**

```bash
git push -u origin worker/mio
```

PR title: `refactor: テーブル名の複数形統一 + シソーラス見直し (pattern→rule, entry→rule)`
PR body should reference `Closes #126`.
