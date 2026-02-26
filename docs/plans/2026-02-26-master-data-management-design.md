# System Admin Master Data Management — Design Document

Date: 2026-02-26
Status: Approved
Branch: worker/mio

## Overview

Add system-wide master data management for SYSTEM_ADMIN. Three preset types — fiscal year patterns, monthly period (closing date) patterns, and holiday calendars — are managed as system-level templates. When a new tenant is bootstrapped, active presets are copied as initial tenant data. Tenants can then edit their copies independently.

## Data Model: "Preset" Pattern

System presets live in dedicated `*_preset` tables with NO `tenant_id`. Existing tenant-scoped tables (`fiscal_year_pattern`, `monthly_period_pattern`) remain unchanged. A new tenant-scoped `holiday_calendar` + `holiday_calendar_entry` table pair is also created.

## Database Schema

### Preset Tables

```sql
-- Fiscal year pattern presets (system-wide)
fiscal_year_pattern_preset (
  id          UUID PK DEFAULT gen_random_uuid(),
  name        VARCHAR(128) NOT NULL UNIQUE,
  description VARCHAR(512),
  start_month INT NOT NULL CHECK (1..12),
  start_day   INT NOT NULL DEFAULT 1 CHECK (1..31),
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

-- Monthly period pattern presets (system-wide)
monthly_period_pattern_preset (
  id          UUID PK DEFAULT gen_random_uuid(),
  name        VARCHAR(128) NOT NULL UNIQUE,
  description VARCHAR(512),
  start_day   INT NOT NULL DEFAULT 1 CHECK (1..28),
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

-- Holiday calendar presets (system-wide, header)
holiday_calendar_preset (
  id          UUID PK DEFAULT gen_random_uuid(),
  name        VARCHAR(128) NOT NULL UNIQUE,
  description VARCHAR(512),
  country     VARCHAR(2),  -- ISO 3166-1 alpha-2
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

-- Holiday entries within a calendar preset
holiday_calendar_entry_preset (
  id                  UUID PK DEFAULT gen_random_uuid(),
  holiday_calendar_id UUID NOT NULL FK → holiday_calendar_preset ON DELETE CASCADE,
  name                VARCHAR(128) NOT NULL,
  entry_type          VARCHAR(16) NOT NULL,  -- 'FIXED' or 'NTH_WEEKDAY'
  month               INT NOT NULL CHECK (1..12),
  day                 INT CHECK (1..31),          -- required when FIXED
  nth_occurrence      INT CHECK (1..5),           -- required when NTH_WEEKDAY
  day_of_week         INT CHECK (1..7),           -- required when NTH_WEEKDAY (ISO 8601: 1=Mon..7=Sun)
  specific_year       INT,                        -- NULL = recurring every year

  CHECK (entry_type = 'FIXED' => day IS NOT NULL AND nth_occurrence IS NULL AND day_of_week IS NULL),
  CHECK (entry_type = 'NTH_WEEKDAY' => nth_occurrence IS NOT NULL AND day_of_week IS NOT NULL AND day IS NULL)
)
INDEX idx_hce_preset_calendar_id ON (holiday_calendar_id)
```

### Tenant-Level Holiday Calendar (copy target)

```sql
holiday_calendar (
  id          UUID PK,
  tenant_id   UUID NOT NULL FK → tenant,
  name        VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  country     VARCHAR(2),
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(tenant_id, name)
)

holiday_calendar_entry (
  -- same columns as holiday_calendar_entry_preset
  holiday_calendar_id FK → holiday_calendar ON DELETE CASCADE
)
```

### Permissions

| ID (bb000000-...) | Permission | Description |
|---|---|---|
| ...028 | `master_data.view` | View system master data presets |
| ...029 | `master_data.create` | Create master data presets |
| ...030 | `master_data.update` | Update master data presets |
| ...031 | `master_data.deactivate` | Deactivate/activate master data presets |

All granted to SYSTEM_ADMIN role.

### Seed Data

- Fiscal year: Japanese FY (Apr 1), Calendar year (Jan 1)
- Monthly period: 1st, 16th, 21st, 26th
- Holiday calendar: Japan Public Holidays with 16 entries
  - FIXED: New Year's Day (1/1), National Foundation Day (2/11), Emperor's Birthday (2/23), etc.
  - NTH_WEEKDAY: Coming of Age Day (Jan 2nd Mon), Marine Day (Jul 3rd Mon), Respect for the Aged Day (Sep 3rd Mon), Sports Day (Oct 2nd Mon)

## Backend Architecture

### Files

- **Migration**: `V23__master_data_presets.sql`
- **Service**: `AdminMasterDataService.java` — JdbcTemplate-based CRUD (no event sourcing), follows `AdminProjectService` pattern
- **Controller**: `AdminMasterDataController.java` — REST controller, follows `AdminProjectController` pattern

### API Endpoints

Base path: `/api/v1/admin/master-data`

**Fiscal Year Pattern Presets:**

| Method | Path | Permission |
|--------|------|------------|
| GET | `/fiscal-year-patterns` | `master_data.view` |
| POST | `/fiscal-year-patterns` | `master_data.create` |
| PUT | `/fiscal-year-patterns/{id}` | `master_data.update` |
| PATCH | `/fiscal-year-patterns/{id}/deactivate` | `master_data.deactivate` |
| PATCH | `/fiscal-year-patterns/{id}/activate` | `master_data.deactivate` |

**Monthly Period Pattern Presets:** Same pattern at `/monthly-period-patterns`

**Holiday Calendar Presets:**

| Method | Path | Permission |
|--------|------|------------|
| GET | `/holiday-calendars` | `master_data.view` |
| POST | `/holiday-calendars` | `master_data.create` |
| PUT | `/holiday-calendars/{id}` | `master_data.update` |
| PATCH | `/holiday-calendars/{id}/deactivate` | `master_data.deactivate` |
| PATCH | `/holiday-calendars/{id}/activate` | `master_data.deactivate` |
| GET | `/holiday-calendars/{id}/entries` | `master_data.view` |
| POST | `/holiday-calendars/{id}/entries` | `master_data.create` |
| PUT | `/holiday-calendars/{id}/entries/{entryId}` | `master_data.update` |
| DELETE | `/holiday-calendars/{id}/entries/{entryId}` | `master_data.update` |

### Tenant Integration

In `AdminTenantService.bootstrapTenant()` (line ~138), before organization creation:
1. Call `AdminMasterDataService.copyPresetsToTenant(tenantId)`
2. Fiscal year / monthly period: use domain model `create()` factories + repositories (event sourcing consistency)
3. Holiday calendar: direct JDBC insert into tenant-scoped tables
4. Return created pattern IDs for organization assignment

## Frontend Architecture

### Page Structure

Single page `/admin/master-data` with 3 tabs. Follows existing `admin/projects/page.tsx` CRUD pattern.

### New Components (9 files)

| File | Purpose |
|------|---------|
| `admin/master-data/page.tsx` | Main page with tab state, form modals, ConfirmDialog |
| `components/admin/MasterDataTabs.tsx` | Tab bar (FY / MP / Holiday) |
| `components/admin/FiscalYearPresetList.tsx` | FY preset list with search/pagination |
| `components/admin/FiscalYearPresetForm.tsx` | FY preset modal form |
| `components/admin/MonthlyPeriodPresetList.tsx` | MP preset list |
| `components/admin/MonthlyPeriodPresetForm.tsx` | MP preset modal form |
| `components/admin/HolidayCalendarPresetList.tsx` | Calendar list with expandable entry rows |
| `components/admin/HolidayCalendarPresetForm.tsx` | Calendar header modal form |
| `components/admin/HolidayEntryForm.tsx` | Entry modal form (FIXED/NTH_WEEKDAY toggle) |

### Existing File Changes

| File | Change |
|------|--------|
| `AdminNav.tsx` | Add "Master Data" nav item with `master_data.view` permission |
| `admin/page.tsx` | Add Master Data dashboard card |
| `services/api.ts` | Add `api.admin.masterData` section |
| `messages/en.json` | Add `admin.masterData.*` i18n keys |
| `messages/ja.json` | Add Japanese translations |

### UI Patterns (all follow existing conventions)

- Lists: pagination, 300ms debounced search, show-inactive toggle, Skeleton.Table loading
- Forms: modal overlay, per-field useState, manual validation, useToast feedback
- Responsive: desktop table + mobile cards
- Errors: ForbiddenError detection, AccessDenied fallback
- Holiday entries: inline expandable sub-table within calendar rows

## Implementation Order

1. V23 migration (tables + permissions + seed data)
2. AdminMasterDataService (FY → MP → Holiday CRUD)
3. AdminMasterDataController (REST endpoints)
4. Backend tests (AdminMasterDataControllerTest.kt)
5. Frontend API client (api.ts extension)
6. i18n keys (en.json + ja.json)
7. FY preset UI (List + Form)
8. MP preset UI (List + Form)
9. Holiday calendar UI (List + Form + EntryForm)
10. Master data page (tab integration)
11. Navigation + dashboard updates
12. Tenant bootstrap integration (AdminTenantService change)

## Testing Strategy

### Backend
- `AdminMasterDataControllerTest.kt`: CRUD for all 3 types, permission enforcement (SYSTEM_ADMIN OK, others 403), unique name constraints (409), validation, deactivate/activate lifecycle, holiday entry sub-resource CRUD

### Frontend
- Component tests for List and Form components
- Tab switching behavior
- Permission-based visibility

### Manual Verification
- SYSTEM_ADMIN login → Master Data page → CRUD each tab
- TENANT_ADMIN login → Master Data page inaccessible
- Bootstrap new tenant → verify copied presets exist

## Key Reference Files

- `AdminProjectService.java` — Service pattern (JdbcTemplate CRUD)
- `AdminProjectController.java` — Controller pattern (@PreAuthorize)
- `AdminTenantService.java:138` — Bootstrap integration point
- `V18__admin_permissions_seed.sql` — Permission seeding pattern
- `frontend/app/admin/projects/page.tsx` — Frontend CRUD page pattern
- `frontend/app/components/admin/ProjectList.tsx` — List component pattern
- `frontend/app/components/admin/ProjectForm.tsx` — Form component pattern
