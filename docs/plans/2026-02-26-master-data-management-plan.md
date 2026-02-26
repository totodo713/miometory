# Master Data Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add system-wide master data preset management (fiscal year patterns, monthly period patterns, holiday calendars) for SYSTEM_ADMIN.

**Architecture:** JdbcTemplate-based CRUD service + REST controller for 3 preset types, stored in dedicated `*_preset` tables. Frontend uses a single tabbed page following the existing admin CRUD pattern (ProjectList/ProjectForm). Holiday calendar entries support FIXED and NTH_WEEKDAY rule types.

**Tech Stack:** Spring Boot (Java), JdbcTemplate, Flyway, Next.js 14, Tailwind CSS, next-intl

**Design Doc:** `docs/plans/2026-02-26-master-data-management-design.md`

---

## Task 1: Flyway Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__master_data_presets.sql`

**Step 1: Write the migration SQL**

```sql
-- ============================================================================
-- Master Data Preset Tables (System-Wide, No Tenant)
-- ============================================================================

-- Fiscal Year Pattern Presets
CREATE TABLE fiscal_year_pattern_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    start_month INT          NOT NULL,
    start_day   INT          NOT NULL DEFAULT 1,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fyp_preset_start_month CHECK (start_month >= 1 AND start_month <= 12),
    CONSTRAINT chk_fyp_preset_start_day CHECK (start_day >= 1 AND start_day <= 31),
    CONSTRAINT uq_fyp_preset_name UNIQUE (name)
);

-- Monthly Period Pattern Presets
CREATE TABLE monthly_period_pattern_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    start_day   INT          NOT NULL DEFAULT 1,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mpp_preset_start_day CHECK (start_day >= 1 AND start_day <= 28),
    CONSTRAINT uq_mpp_preset_name UNIQUE (name)
);

-- Holiday Calendar Presets (header)
CREATE TABLE holiday_calendar_preset (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    country     VARCHAR(2),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hc_preset_name UNIQUE (name)
);

-- Holiday Calendar Entry Presets (detail)
CREATE TABLE holiday_calendar_entry_preset (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_calendar_id     UUID         NOT NULL REFERENCES holiday_calendar_preset(id) ON DELETE CASCADE,
    name                    VARCHAR(128) NOT NULL,
    entry_type              VARCHAR(16)  NOT NULL,
    month                   INT          NOT NULL,
    day                     INT,
    nth_occurrence          INT,
    day_of_week             INT,
    specific_year           INT,
    CONSTRAINT chk_hcep_entry_type CHECK (entry_type IN ('FIXED', 'NTH_WEEKDAY')),
    CONSTRAINT chk_hcep_month CHECK (month >= 1 AND month <= 12),
    CONSTRAINT chk_hcep_day CHECK (day IS NULL OR (day >= 1 AND day <= 31)),
    CONSTRAINT chk_hcep_nth CHECK (nth_occurrence IS NULL OR (nth_occurrence >= 1 AND nth_occurrence <= 5)),
    CONSTRAINT chk_hcep_dow CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT chk_hcep_fixed CHECK (
        entry_type != 'FIXED' OR (day IS NOT NULL AND nth_occurrence IS NULL AND day_of_week IS NULL)
    ),
    CONSTRAINT chk_hcep_nth_weekday CHECK (
        entry_type != 'NTH_WEEKDAY' OR (nth_occurrence IS NOT NULL AND day_of_week IS NOT NULL AND day IS NULL)
    )
);

CREATE INDEX idx_hcep_calendar_id ON holiday_calendar_entry_preset(holiday_calendar_id);

-- ============================================================================
-- Tenant-Level Holiday Calendar (copy target for presets)
-- ============================================================================

CREATE TABLE holiday_calendar (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    country     VARCHAR(2),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hc_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_hc_tenant_id ON holiday_calendar(tenant_id);

CREATE TABLE holiday_calendar_entry (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_calendar_id     UUID         NOT NULL REFERENCES holiday_calendar(id) ON DELETE CASCADE,
    name                    VARCHAR(128) NOT NULL,
    entry_type              VARCHAR(16)  NOT NULL,
    month                   INT          NOT NULL,
    day                     INT,
    nth_occurrence          INT,
    day_of_week             INT,
    specific_year           INT,
    CONSTRAINT chk_hce_entry_type CHECK (entry_type IN ('FIXED', 'NTH_WEEKDAY')),
    CONSTRAINT chk_hce_month CHECK (month >= 1 AND month <= 12),
    CONSTRAINT chk_hce_day CHECK (day IS NULL OR (day >= 1 AND day <= 31)),
    CONSTRAINT chk_hce_nth CHECK (nth_occurrence IS NULL OR (nth_occurrence >= 1 AND nth_occurrence <= 5)),
    CONSTRAINT chk_hce_dow CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT chk_hce_fixed CHECK (
        entry_type != 'FIXED' OR (day IS NOT NULL AND nth_occurrence IS NULL AND day_of_week IS NULL)
    ),
    CONSTRAINT chk_hce_nth_weekday CHECK (
        entry_type != 'NTH_WEEKDAY' OR (nth_occurrence IS NOT NULL AND day_of_week IS NOT NULL AND day IS NULL)
    )
);

CREATE INDEX idx_hce_calendar_id ON holiday_calendar_entry(holiday_calendar_id);

-- ============================================================================
-- Permissions
-- ============================================================================
INSERT INTO permissions (id, name, description, created_at) VALUES
    ('bb000000-0000-0000-0000-000000000028', 'master_data.view', 'View system master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000029', 'master_data.create', 'Create master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000030', 'master_data.update', 'Update master data presets', NOW()),
    ('bb000000-0000-0000-0000-000000000031', 'master_data.deactivate', 'Deactivate/activate master data presets', NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.name IN ('master_data.view', 'master_data.create', 'master_data.update', 'master_data.deactivate')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- Seed: Fiscal Year Pattern Presets
-- ============================================================================
INSERT INTO fiscal_year_pattern_preset (id, name, description, start_month, start_day) VALUES
    ('00000000-0000-0000-0000-fp0000000001', 'Japanese Fiscal Year (April Start)', 'Standard Japanese fiscal year starting April 1', 4, 1),
    ('00000000-0000-0000-0000-fp0000000002', 'Calendar Year (January Start)', 'Standard calendar year starting January 1', 1, 1)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- Seed: Monthly Period Pattern Presets
-- ============================================================================
INSERT INTO monthly_period_pattern_preset (id, name, description, start_day) VALUES
    ('00000000-0000-0000-0000-mp0000000001', 'Standard (1st of Month)', 'Monthly period starting on the 1st', 1),
    ('00000000-0000-0000-0000-mp0000000002', '16th Cutoff', 'Monthly period starting on the 16th', 16),
    ('00000000-0000-0000-0000-mp0000000003', '21st Cutoff', 'Monthly period starting on the 21st', 21),
    ('00000000-0000-0000-0000-mp0000000004', '26th Cutoff', 'Monthly period starting on the 26th', 26)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- Seed: Holiday Calendar Preset (Japan)
-- ============================================================================
INSERT INTO holiday_calendar_preset (id, name, description, country) VALUES
    ('00000000-0000-0000-0000-hc0000000001', 'Japan Public Holidays', 'Standard Japanese public holidays', 'JP')
ON CONFLICT (name) DO NOTHING;

INSERT INTO holiday_calendar_entry_preset (holiday_calendar_id, name, entry_type, month, day, nth_occurrence, day_of_week) VALUES
    -- FIXED holidays
    ('00000000-0000-0000-0000-hc0000000001', 'New Year''s Day',             'FIXED', 1,  1,    NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'National Foundation Day',     'FIXED', 2,  11,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Emperor''s Birthday',         'FIXED', 2,  23,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Vernal Equinox Day',          'FIXED', 3,  20,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Showa Day',                   'FIXED', 4,  29,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Constitution Memorial Day',   'FIXED', 5,  3,    NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Greenery Day',                'FIXED', 5,  4,    NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Children''s Day',             'FIXED', 5,  5,    NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Mountain Day',                'FIXED', 8,  11,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Autumnal Equinox Day',        'FIXED', 9,  23,   NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Culture Day',                 'FIXED', 11, 3,    NULL, NULL),
    ('00000000-0000-0000-0000-hc0000000001', 'Labor Thanksgiving Day',      'FIXED', 11, 23,   NULL, NULL),
    -- NTH_WEEKDAY holidays (Happy Monday)
    ('00000000-0000-0000-0000-hc0000000001', 'Coming of Age Day',           'NTH_WEEKDAY', 1,  NULL, 2, 1),
    ('00000000-0000-0000-0000-hc0000000001', 'Marine Day',                  'NTH_WEEKDAY', 7,  NULL, 3, 1),
    ('00000000-0000-0000-0000-hc0000000001', 'Respect for the Aged Day',    'NTH_WEEKDAY', 9,  NULL, 3, 1),
    ('00000000-0000-0000-0000-hc0000000001', 'Sports Day',                  'NTH_WEEKDAY', 10, NULL, 2, 1);
```

**Step 2: Verify migration applies**

Run: `cd backend && ./gradlew flywayMigrate`
Expected: `Successfully applied 1 migration to schema "public", now at version v23`

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V23__master_data_presets.sql
git commit -m "feat: add V23 migration for master data preset tables and permissions"
```

---

## Task 2: Backend Service — Fiscal Year & Monthly Period CRUD

**Files:**
- Create: `backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java`

**Reference:** Follow `AdminProjectService.java` pattern — JdbcTemplate CRUD, inner records for DTOs, `escapeLike` helper.

**Step 1: Write the failing test (fiscal year list + create)**

Create: `backend/src/test/kotlin/com/worklog/api/AdminMasterDataControllerTest.kt`

```kotlin
package com.worklog.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

class AdminMasterDataControllerTest : AdminIntegrationTestBase() {

    private lateinit var adminEmail: String
    private lateinit var tenantAdminEmail: String
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        adminEmail = "sysadmin-md-$suffix@test.com"
        tenantAdminEmail = "tenantadmin-md-$suffix@test.com"
        createUser(adminEmail, SYSTEM_ADMIN_ROLE_ID, "System Admin")
        createUser(tenantAdminEmail, TENANT_ADMIN_ROLE_ID, "Tenant Admin")
        createMemberForUser(tenantAdminEmail)
    }

    private val basePath = "/api/v1/admin/master-data"

    @Nested
    inner class FiscalYearPresets {

        @Test
        fun `list returns 200 with seeded data for system admin`() {
            mockMvc.perform(
                get("$basePath/fiscal-year-patterns").with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(
                get("$basePath/fiscal-year-patterns").with(user(tenantAdminEmail)),
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `create returns 201`() {
            val name = "FY-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Test","startMonth":7,"startDay":1}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
        }

        @Test
        fun `create duplicate name returns 409`() {
            val name = "DupFY-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"First","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated)

            mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Second","startMonth":1,"startDay":1}"""),
            ).andExpect(status().isConflict)
        }

        @Test
        fun `update returns 200`() {
            val name = "UpdFY-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            mockMvc.perform(
                put("$basePath/fiscal-year-patterns/$id")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name-updated","description":"Updated","startMonth":10,"startDay":1}"""),
            ).andExpect(status().isOk)
        }

        @Test
        fun `deactivate and activate returns 200`() {
            val name = "DaFY-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            mockMvc.perform(patch("$basePath/fiscal-year-patterns/$id/deactivate").with(user(adminEmail)))
                .andExpect(status().isOk)

            mockMvc.perform(patch("$basePath/fiscal-year-patterns/$id/activate").with(user(adminEmail)))
                .andExpect(status().isOk)
        }

        @Test
        fun `search filters by name`() {
            val unique = UUID.randomUUID().toString().take(8)
            val name = "SearchFY-$unique"
            mockMvc.perform(
                post("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","startMonth":4,"startDay":1}"""),
            ).andExpect(status().isCreated)

            mockMvc.perform(
                get("$basePath/fiscal-year-patterns")
                    .with(user(adminEmail))
                    .param("search", unique),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(name))
        }
    }

    @Nested
    inner class MonthlyPeriodPresets {

        @Test
        fun `list returns 200 with seeded data`() {
            mockMvc.perform(
                get("$basePath/monthly-period-patterns").with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
        }

        @Test
        fun `create returns 201`() {
            val name = "MP-${UUID.randomUUID().toString().take(8)}"
            mockMvc.perform(
                post("$basePath/monthly-period-patterns")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","description":"Test","startDay":11}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(
                get("$basePath/monthly-period-patterns").with(user(tenantAdminEmail)),
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class HolidayCalendars {

        @Test
        fun `list returns 200 with seeded data`() {
            mockMvc.perform(
                get("$basePath/holiday-calendars").with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        }

        @Test
        fun `create calendar and add entry returns 201`() {
            val calName = "HC-${UUID.randomUUID().toString().take(8)}"
            val result = mockMvc.perform(
                post("$basePath/holiday-calendars")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$calName","description":"Test","country":"US"}"""),
            ).andExpect(status().isCreated).andReturn()

            val calId = objectMapper.readTree(result.response.contentAsString).get("id").asText()

            // Add FIXED entry
            mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Independence Day","entryType":"FIXED","month":7,"day":4}"""),
            ).andExpect(status().isCreated)

            // Add NTH_WEEKDAY entry
            mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Thanksgiving","entryType":"NTH_WEEKDAY","month":11,"nthOccurrence":4,"dayOfWeek":4}"""),
            ).andExpect(status().isCreated)

            // Verify entries
            mockMvc.perform(
                get("$basePath/holiday-calendars/$calId/entries").with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        @Test
        fun `list seeded Japan entries`() {
            mockMvc.perform(
                get("$basePath/holiday-calendars/00000000-0000-0000-0000-hc0000000001/entries")
                    .with(user(adminEmail)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(16))
        }

        @Test
        fun `delete entry returns 200`() {
            val calName = "HC-Del-${UUID.randomUUID().toString().take(8)}"
            val calResult = mockMvc.perform(
                post("$basePath/holiday-calendars")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$calName","country":"US"}"""),
            ).andExpect(status().isCreated).andReturn()

            val calId = objectMapper.readTree(calResult.response.contentAsString).get("id").asText()

            val entryResult = mockMvc.perform(
                post("$basePath/holiday-calendars/$calId/entries")
                    .with(user(adminEmail))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test Holiday","entryType":"FIXED","month":1,"day":1}"""),
            ).andExpect(status().isCreated).andReturn()

            val entryId = objectMapper.readTree(entryResult.response.contentAsString).get("id").asText()

            mockMvc.perform(
                delete("$basePath/holiday-calendars/$calId/entries/$entryId")
                    .with(user(adminEmail)),
            ).andExpect(status().isOk)
        }

        @Test
        fun `list returns 403 for tenant admin`() {
            mockMvc.perform(
                get("$basePath/holiday-calendars").with(user(tenantAdminEmail)),
            )
                .andExpect(status().isForbidden)
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AdminMasterDataControllerTest"`
Expected: FAIL — `AdminMasterDataControllerTest` class compiles but all tests get 404 (no controller yet)

**Step 3: Write AdminMasterDataService**

Create: `backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java`

Follow the `AdminProjectService.java` pattern (JdbcTemplate, inner record DTOs, `escapeLike`). The service needs:

- **Fiscal year methods:** `listFiscalYearPresets(search, isActive, page, size)`, `createFiscalYearPreset(name, description, startMonth, startDay)`, `updateFiscalYearPreset(id, name, description, startMonth, startDay)`, `deactivateFiscalYearPreset(id)`, `activateFiscalYearPreset(id)`
- **Monthly period methods:** Same CRUD pattern with `startDay` only
- **Holiday calendar methods:** Same CRUD for calendars + `listHolidayEntries(calendarId)`, `addHolidayEntry(calendarId, ...)`, `updateHolidayEntry(calendarId, entryId, ...)`, `removeHolidayEntry(calendarId, entryId)`
- No `tenantId` parameter anywhere (system-wide)
- Throw `DomainException("DUPLICATE_NAME", ...)` for unique constraint violations
- Throw `DomainException("NOT_FOUND", ...)` for zero-row updates

Inner records:
```java
public record PresetPage<T>(List<T> content, long totalElements, int totalPages, int number) {}
public record FiscalYearPresetRow(String id, String name, String description, int startMonth, int startDay, boolean isActive) {}
public record MonthlyPeriodPresetRow(String id, String name, String description, int startDay, boolean isActive) {}
public record HolidayCalendarPresetRow(String id, String name, String description, String country, boolean isActive, int entryCount) {}
public record HolidayEntryRow(String id, String name, String entryType, int month, Integer day, Integer nthOccurrence, Integer dayOfWeek, Integer specificYear) {}
```

**Step 4: Write AdminMasterDataController**

Create: `backend/src/main/java/com/worklog/api/AdminMasterDataController.java`

Follow `AdminProjectController.java` pattern but WITHOUT `UserContextService` (no tenant scoping). Key differences:
- `@RequestMapping("/api/v1/admin/master-data")`
- All `@PreAuthorize` use `master_data.*` permissions
- No `Authentication` parameter needed for tenant resolution
- Request records with Jakarta Validation:

```java
record CreateFiscalYearPresetRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description,
    @Min(1) @Max(12) int startMonth,
    @Min(1) @Max(31) int startDay) {}

record CreateMonthlyPeriodPresetRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description,
    @Min(1) @Max(28) int startDay) {}

record CreateHolidayCalendarRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description,
    @Size(max = 2) String country) {}

record CreateHolidayEntryRequest(
    @NotBlank @Size(max = 128) String name,
    @NotBlank String entryType,
    @Min(1) @Max(12) int month,
    Integer day,
    Integer nthOccurrence,
    Integer dayOfWeek,
    Integer specificYear) {}

record CreateResponse(String id) {}
```

**Step 5: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AdminMasterDataControllerTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java \
        backend/src/main/java/com/worklog/api/AdminMasterDataController.java \
        backend/src/test/kotlin/com/worklog/api/AdminMasterDataControllerTest.kt
git commit -m "feat: add AdminMasterDataService and Controller with tests

CRUD for fiscal year, monthly period, and holiday calendar presets.
All endpoints gated by master_data.* permissions (SYSTEM_ADMIN only)."
```

---

## Task 3: Frontend API Client & i18n

**Files:**
- Modify: `frontend/app/services/api.ts` (add `masterData` section to `api.admin`)
- Modify: `frontend/messages/en.json` (add `admin.masterData.*` keys)
- Modify: `frontend/messages/ja.json` (add Japanese translations)
- Create: `frontend/app/types/masterData.ts` (TypeScript interfaces)

**Step 1: Add TypeScript types**

Create `frontend/app/types/masterData.ts`:
```typescript
export interface FiscalYearPresetRow {
  id: string;
  name: string;
  description: string | null;
  startMonth: number;
  startDay: number;
  isActive: boolean;
}

export interface MonthlyPeriodPresetRow {
  id: string;
  name: string;
  description: string | null;
  startDay: number;
  isActive: boolean;
}

export interface HolidayCalendarPresetRow {
  id: string;
  name: string;
  description: string | null;
  country: string | null;
  isActive: boolean;
  entryCount: number;
}

export interface HolidayEntryRow {
  id: string;
  name: string;
  entryType: "FIXED" | "NTH_WEEKDAY";
  month: number;
  day: number | null;
  nthOccurrence: number | null;
  dayOfWeek: number | null;
  specificYear: number | null;
}
```

**Step 2: Add API client methods**

In `frontend/app/services/api.ts`, add `masterData` to the `admin` object. Follow the `admin.projects` pattern (GET with URLSearchParams, POST/PUT/PATCH/DELETE). Add all 19 endpoints from the design doc.

**Step 3: Add i18n keys to en.json**

Add under `admin`:
- `nav.masterData`: "Master Data"
- `dashboard.cards.masterDataDescription`: "Manage system-wide preset patterns"
- `masterData.title`, `masterData.tabs.*`, `masterData.fiscalYear.*`, `masterData.monthlyPeriod.*`, `masterData.holidayCalendar.*`
- Shared: `masterData.searchPlaceholder`, `masterData.showInactive`, `masterData.notFound`, etc.

**Step 4: Add i18n keys to ja.json**

Same structure with Japanese translations:
- `masterData.title`: "マスタデータ管理"
- `masterData.tabs.fiscalYearPatterns`: "会計年度パターン"
- `masterData.tabs.monthlyPeriodPatterns`: "締め日パターン"
- `masterData.tabs.holidayCalendars`: "祝日カレンダー"
- etc.

**Step 5: Commit**

```bash
git add frontend/app/types/masterData.ts frontend/app/services/api.ts \
        frontend/messages/en.json frontend/messages/ja.json
git commit -m "feat: add master data API client, types, and i18n keys"
```

---

## Task 4: Fiscal Year Preset UI Components

**Files:**
- Create: `frontend/app/components/admin/FiscalYearPresetList.tsx`
- Create: `frontend/app/components/admin/FiscalYearPresetForm.tsx`

**Reference:** Copy `ProjectList.tsx` and `ProjectForm.tsx` patterns exactly.

**Step 1: Create FiscalYearPresetList**

Follow `ProjectList.tsx` structure:
- Interface `FiscalYearPresetRow` (import from types)
- Props: `onEdit`, `onDeactivate`, `onActivate`, `onForbidden`, `refreshKey`
- State: items, totalPages, page, search, debouncedSearch, showInactive, isLoading, loadError
- API call: `api.admin.masterData.fiscalYearPresets.list({...})`
- Table columns: Name, Description, Start Month, Start Day, Status, Actions
- Mobile card layout
- Month display: convert number to month name

**Step 2: Create FiscalYearPresetForm**

Follow `ProjectForm.tsx` structure:
- Props: `preset` (null for create, `FiscalYearPresetRow` for edit), `onClose`, `onSaved`
- Fields: name (text), description (text), startMonth (select 1-12), startDay (number 1-31)
- Validation: name required
- API: create or update based on `preset` prop
- Modal overlay pattern

**Step 3: Commit**

```bash
git add frontend/app/components/admin/FiscalYearPresetList.tsx \
        frontend/app/components/admin/FiscalYearPresetForm.tsx
git commit -m "feat: add FiscalYearPreset list and form components"
```

---

## Task 5: Monthly Period Preset UI Components

**Files:**
- Create: `frontend/app/components/admin/MonthlyPeriodPresetList.tsx`
- Create: `frontend/app/components/admin/MonthlyPeriodPresetForm.tsx`

**Step 1: Create MonthlyPeriodPresetList**

Same pattern as FiscalYearPresetList but simpler:
- Table columns: Name, Description, Start Day, Status, Actions
- API: `api.admin.masterData.monthlyPeriodPresets.list({...})`

**Step 2: Create MonthlyPeriodPresetForm**

- Fields: name (text), description (text), startDay (number 1-28)
- Validation: name required, startDay in range

**Step 3: Commit**

```bash
git add frontend/app/components/admin/MonthlyPeriodPresetList.tsx \
        frontend/app/components/admin/MonthlyPeriodPresetForm.tsx
git commit -m "feat: add MonthlyPeriodPreset list and form components"
```

---

## Task 6: Holiday Calendar Preset UI Components

**Files:**
- Create: `frontend/app/components/admin/HolidayCalendarPresetList.tsx`
- Create: `frontend/app/components/admin/HolidayCalendarPresetForm.tsx`
- Create: `frontend/app/components/admin/HolidayEntryForm.tsx`

**Step 1: Create HolidayCalendarPresetList**

More complex than other lists — includes expandable rows:
- Calendar list with Name, Description, Country, Entry Count, Status, Actions + expand toggle
- When expanded, show inline sub-table of holiday entries
- Each entry row has: Name, Type badge (FIXED/NTH_WEEKDAY), Date description, Edit/Delete buttons
- "Add Holiday" button in expanded view
- State: `expandedCalendarId` to track which calendar is expanded
- Fetch entries on expand: `api.admin.masterData.holidayCalendars.listEntries(calendarId)`
- Date display helper: FIXED → "Month/Day", NTH_WEEKDAY → "Month Nth Weekday"

**Step 2: Create HolidayCalendarPresetForm**

Simple form for calendar header:
- Fields: name (text), description (text), country (text, 2 chars max)
- Validation: name required

**Step 3: Create HolidayEntryForm**

Form with entry type toggle:
- Radio/select for entryType: FIXED or NTH_WEEKDAY
- FIXED fields: month (select 1-12), day (number 1-31)
- NTH_WEEKDAY fields: month (select 1-12), nthOccurrence (select 1-5), dayOfWeek (select Mon-Sun)
- Optional: specificYear (number input, empty = recurring)
- Conditional rendering based on entryType

**Step 4: Commit**

```bash
git add frontend/app/components/admin/HolidayCalendarPresetList.tsx \
        frontend/app/components/admin/HolidayCalendarPresetForm.tsx \
        frontend/app/components/admin/HolidayEntryForm.tsx
git commit -m "feat: add HolidayCalendarPreset list, form, and entry form components"
```

---

## Task 7: Master Data Page + Navigation Integration

**Files:**
- Create: `frontend/app/components/admin/MasterDataTabs.tsx`
- Create: `frontend/app/admin/master-data/page.tsx`
- Modify: `frontend/app/components/admin/AdminNav.tsx` (line 17-25, add nav item)
- Modify: `frontend/app/admin/page.tsx` (line 14-52, add dashboard card)

**Step 1: Create MasterDataTabs**

Simple tab bar component:
```typescript
interface MasterDataTabsProps {
  activeTab: "fiscal-year" | "monthly-period" | "holiday-calendar";
  onTabChange: (tab: ...) => void;
}
```
Render 3 buttons with active/inactive styling. Use i18n keys for tab labels.

**Step 2: Create master-data page**

Follow `admin/projects/page.tsx` pattern:
- State: `activeTab`, `editingItem` (union type), `showForm`, `refreshKey`, `confirmTarget`, `isForbidden`
- Breadcrumbs: Admin > Master Data
- Tab bar switches between 3 list components
- Create button label changes per active tab
- Form modal renders different component per active tab
- ConfirmDialog for deactivate/activate

**Step 3: Add nav item to AdminNav**

In `AdminNav.tsx` line 17-25, add to `NAV_ITEMS` array:
```typescript
{ href: "/admin/master-data", labelKey: "masterData", shortLabel: "MD", permission: "master_data.view" },
```

**Step 4: Add dashboard card**

In `admin/page.tsx` line 14-52, add to `CARDS` array:
```typescript
{
  permission: "master_data.view",
  titleKey: "masterData",
  descriptionKey: "cards.masterDataDescription",
  href: "/admin/master-data",
},
```

**Step 5: Run frontend build to verify no errors**

Run: `cd frontend && npx next build`
Expected: Build succeeds

**Step 6: Commit**

```bash
git add frontend/app/components/admin/MasterDataTabs.tsx \
        frontend/app/admin/master-data/page.tsx \
        frontend/app/components/admin/AdminNav.tsx \
        frontend/app/admin/page.tsx
git commit -m "feat: add Master Data admin page with tabs and navigation"
```

---

## Task 8: Tenant Bootstrap Integration

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java` (add `copyPresetsToTenant`)
- Modify: `backend/src/main/java/com/worklog/application/service/AdminTenantService.java` (line ~157, before org creation)

**Step 1: Add copyPresetsToTenant to AdminMasterDataService**

```java
public record CopiedPresets(
    List<CopiedPattern> fiscalYearPatterns,
    List<CopiedPattern> monthlyPeriodPatterns,
    List<CopiedCalendar> holidayCalendars
) {}
public record CopiedPattern(UUID presetId, UUID tenantPatternId, String name) {}
public record CopiedCalendar(UUID presetId, UUID tenantCalendarId, String name) {}

@Transactional
public CopiedPresets copyPresetsToTenant(UUID tenantId) {
    // 1. Copy active fiscal year presets → fiscal_year_pattern (with event store)
    // 2. Copy active monthly period presets → monthly_period_pattern (with event store)
    // 3. Copy active holiday calendars → holiday_calendar + holiday_calendar_entry (direct JDBC)
    // Return mapping for org assignment
}
```

Important: For fiscal_year_pattern and monthly_period_pattern, use the domain model factories + repositories to maintain event store consistency. Inject `FiscalYearPatternRepository` and `MonthlyPeriodPatternRepository`.

**Step 2: Call from bootstrapTenant**

In `AdminTenantService.java`, after tenant validation (line ~147) and before "Create organizations" (line ~157):
```java
adminMasterDataService.copyPresetsToTenant(tenantId);
```

**Step 3: Write integration test**

Add a test to `AdminTenantBootstrapTest.kt` or `AdminMasterDataControllerTest.kt`:
- Create a tenant, activate it, bootstrap it
- Verify that `fiscal_year_pattern` has records for the new tenant
- Verify that `monthly_period_pattern` has records for the new tenant
- Verify that `holiday_calendar` + entries exist for the new tenant

**Step 4: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/application/service/AdminMasterDataService.java \
        backend/src/main/java/com/worklog/application/service/AdminTenantService.java \
        backend/src/test/kotlin/com/worklog/api/AdminMasterDataControllerTest.kt
git commit -m "feat: copy presets to tenant during bootstrap"
```

---

## Task 9: Final Verification

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: ALL PASS

**Step 2: Run backend lint/format checks**

Run: `cd backend && ./gradlew checkFormat && ./gradlew detekt`
Expected: PASS

**Step 3: Run frontend lint**

Run: `cd frontend && npm ci && npx biome ci`
Expected: PASS

**Step 4: Run frontend tests**

Run: `cd frontend && npm test -- --run`
Expected: PASS

**Step 5: Verify test coverage**

Run: `cd backend && ./gradlew test jacocoTestReport`
Check: 80%+ LINE coverage for `com.worklog.application.service.AdminMasterDataService` and `com.worklog.api.AdminMasterDataController`

**Step 6: Final commit if any fixes needed**

```bash
git add -A && git commit -m "fix: address lint and test issues"
```
