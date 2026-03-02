# Plan: テーブル名の複数形統一 + シソーラス見直し

## Context

DB テーブル名が単数形（V1〜V4 初期）と複数形（V11 以降）で混在している。全テーブルを複数形に統一し、あわせて `pattern` → `rule`、`holiday_calendar_entry` → `holiday_calendar_rule` のシソーラス変更を行う。また、V2 の deprecated `audit_log` テーブルを DROP し `JdbcAuditLogger` を `audit_logs` に移行する。

## 決定事項

| 項目 | 決定 |
|------|------|
| `event_store` / `snapshot_store` | そのまま維持（ES インフラ慣例） |
| `pattern` → `rule` | 変更する |
| `entry` → `rule` (holiday_calendar) | 変更する |
| `preset` | そのまま |
| `daily_rejection_log` | 単純に複数形化 (`daily_rejection_logs`) |
| `audit_log` (V2) | DROP。`JdbcAuditLogger` を `audit_logs` に移行 |
| Java ドメインクラス名 | テーブル名に合わせて変更 |

## リネームマッピング

| # | 旧テーブル名 | 新テーブル名 | 変更種別 |
|---|---|---|---|
| 1 | `tenant` | `tenants` | 複数形化 |
| 2 | `organization` | `organizations` | 複数形化 |
| 3 | `fiscal_year_pattern` | `fiscal_year_rules` | 複数形化 + シソーラス |
| 4 | `monthly_period_pattern` | `monthly_period_rules` | 複数形化 + シソーラス |
| 5 | `fiscal_year_pattern_preset` | `fiscal_year_rule_presets` | 複数形化 + シソーラス |
| 6 | `monthly_period_pattern_preset` | `monthly_period_rule_presets` | 複数形化 + シソーラス |
| 7 | `holiday_calendar` | `holiday_calendars` | 複数形化 |
| 8 | `holiday_calendar_entry` | `holiday_calendar_rules` | 複数形化 + シソーラス |
| 9 | `holiday_calendar_preset` | `holiday_calendar_presets` | 複数形化 |
| 10 | `holiday_calendar_entry_preset` | `holiday_calendar_rule_presets` | 複数形化 + シソーラス |
| 11 | `daily_rejection_log` | `daily_rejection_logs` | 複数形化 |
| 12 | `audit_log` | DROP TABLE | 削除 |

変更なし: `event_store`, `snapshot_store`, `system_default_settings`（既に複数形）

## ドメインクラス/ファイルリネーム

### Pattern → Rule

| 旧クラス名 | 新クラス名 | ファイルパス |
|---|---|---|
| `FiscalYearPattern` | `FiscalYearRule` | `backend/src/main/java/com/worklog/domain/fiscalyear/` |
| `FiscalYearPatternCreated` | `FiscalYearRuleCreated` | 同上 |
| `FiscalYearPatternId` | `FiscalYearRuleId` | 同上 |
| `FiscalYearPatternRepository` | `FiscalYearRuleRepository` | `backend/src/main/java/com/worklog/infrastructure/repository/` |
| `FiscalYearPatternController` | `FiscalYearRuleController` | `backend/src/main/java/com/worklog/api/` |
| `MonthlyPeriodPattern` | `MonthlyPeriodRule` | `backend/src/main/java/com/worklog/domain/monthlyperiod/` |
| `MonthlyPeriodPatternCreated` | `MonthlyPeriodRuleCreated` | 同上 |
| `MonthlyPeriodPatternId` | `MonthlyPeriodRuleId` | 同上 |
| `MonthlyPeriodPatternRepository` | `MonthlyPeriodRuleRepository` | `backend/src/main/java/com/worklog/infrastructure/repository/` |
| `MonthlyPeriodPatternController` | `MonthlyPeriodRuleController` | `backend/src/main/java/com/worklog/api/` |
| `SystemDefaultFiscalYearPattern` | `SystemDefaultFiscalYearRule` | `backend/src/main/java/com/worklog/domain/settings/` |
| `SystemDefaultMonthlyPeriodPattern` | `SystemDefaultMonthlyPeriodRule` | `backend/src/main/java/com/worklog/domain/settings/` |
| `TenantDefaultPatternsAssigned` | `TenantDefaultRulesAssigned` | `backend/src/main/java/com/worklog/domain/tenant/` |

### HolidayCalendarEntry → HolidayCalendarRule

| 旧クラス名 | 新クラス名 | ファイルパス |
|---|---|---|
| `HolidayCalendarEntryRepository` | `HolidayCalendarRuleRepository` | `backend/src/main/java/com/worklog/infrastructure/repository/` |

### パッケージディレクトリリネーム

| 旧パッケージ | 新パッケージ |
|---|---|
| `com.worklog.domain.fiscalyear` | `com.worklog.domain.fiscalyearrule` **（※要検討: そのまま `fiscalyear` でクラス名のみ変更の方がシンプル）** |
| `com.worklog.domain.monthlyperiod` | 同上、パッケージは変更せずクラス名のみ変更 |

→ **パッケージ名はそのまま維持**（パッケージ名はドメイン概念を表し、クラス名がその中の実体を表す構造でOK）

### API エンドポイントパス

| 旧パス | 新パス |
|---|---|
| `/api/fiscal-year-patterns` | `/api/fiscal-year-rules` |
| `/api/monthly-period-patterns` | `/api/monthly-period-rules` |
| `/api/admin/master-data/fiscal-year-pattern-presets` | `/api/admin/master-data/fiscal-year-rule-presets` |
| `/api/admin/master-data/monthly-period-pattern-presets` | `/api/admin/master-data/monthly-period-rule-presets` |
| `/api/admin/master-data/holiday-calendar-entry-presets` | `/api/admin/master-data/holiday-calendar-rule-presets` |

### フロントエンドファイルリネーム

| 旧ファイル名 | 新ファイル名 |
|---|---|
| `FiscalYearPatternForm.tsx` | `FiscalYearRuleForm.tsx` |
| `FiscalYearPatternForm.test.tsx` | `FiscalYearRuleForm.test.tsx` |
| `MonthlyPeriodPatternForm.tsx` | `MonthlyPeriodRuleForm.tsx` |
| `MonthlyPeriodPatternForm.test.tsx` | `MonthlyPeriodRuleForm.test.tsx` |

### テストファイルリネーム

| 旧ファイル名 | 新ファイル名 | パス |
|---|---|---|
| `FiscalYearPatternControllerTest.kt` | `FiscalYearRuleControllerTest.kt` | `backend/src/test/kotlin/com/worklog/api/` |
| `FiscalYearPatternControllerAuthTest.kt` | `FiscalYearRuleControllerAuthTest.kt` | 同上 |
| `MonthlyPeriodPatternControllerTest.kt` | `MonthlyPeriodRuleControllerTest.kt` | 同上 |
| `MonthlyPeriodPatternControllerAuthTest.kt` | `MonthlyPeriodRuleControllerAuthTest.kt` | 同上 |
| `FiscalYearPatternTest.kt` | `FiscalYearRuleTest.kt` | `backend/src/test/kotlin/com/worklog/domain/fiscalyear/` |
| `MonthlyPeriodPatternTest.kt` | `MonthlyPeriodRuleTest.kt` | `backend/src/test/kotlin/com/worklog/domain/monthlyperiod/` |
| `FiscalYearPatternFixtures.kt` | `FiscalYearRuleFixtures.kt` | `backend/src/test/kotlin/com/worklog/fixtures/` |
| `MonthlyPeriodPatternFixtures.kt` | `MonthlyPeriodRuleFixtures.kt` | 同上 |
| `HolidayCalendarEntryRepositoryTest.java` | `HolidayCalendarRuleRepositoryTest.java` | `backend/src/test/java/.../repository/` |

## 実装ステップ

### Step 1: Flyway マイグレーション作成
`V31__rename_tables_to_plural_and_thesaurus.sql` を作成。

内容:
1. `DROP TABLE IF EXISTS audit_log` — deprecated テーブル削除
2. `ALTER TABLE ... RENAME TO` — 11 テーブルを順番にリネーム（FK 依存順）
3. `ALTER INDEX ... RENAME TO` — インデックス名を新テーブル名に合わせて更新
4. `ALTER TABLE ... RENAME CONSTRAINT` — 制約名を更新

ファイル: `backend/src/main/resources/db/migration/V31__rename_tables_to_plural_and_thesaurus.sql`

### Step 2: JdbcAuditLogger の audit_logs 移行
`JdbcAuditLogger.java` の INSERT 先を `audit_log` → `audit_logs` に変更。スキーマ差分の対応:
- `audit_log`: `(tenant_id, user_id, action, resource_type, resource_id, details)`
- `audit_logs`: `(user_id, event_type, ip_address, timestamp, details, retention_days)`
- → `action` を `event_type` にマッピング、`tenant_id`/`resource_type`/`resource_id` は `details` JSONB に含める

ファイル: `backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java`

### Step 3: バックエンド SQL 文字列の更新
テーブル名を参照する全 SQL 文字列を更新。

主要ファイル:
- `TenantRepository.java` — `tenant` → `tenants`
- `OrganizationRepository.java` — `organization` → `organizations`
- `FiscalYearPatternRepository.java` — `fiscal_year_pattern` → `fiscal_year_rules`
- `MonthlyPeriodPatternRepository.java` — `monthly_period_pattern` → `monthly_period_rules`
- `HolidayCalendarEntryRepository.java` — `holiday_calendar_entry` → `holiday_calendar_rules`, `holiday_calendar` → `holiday_calendars`
- `JdbcDailyRejectionLogRepository.java` — `daily_rejection_log` → `daily_rejection_logs`
- `AdminTenantService.java` — `tenant`, `organization` 参照
- `AdminOrganizationService.java` — `organization`, `fiscal_year_pattern`, `monthly_period_pattern` 参照
- `AdminMasterDataService.java` — 全 preset テーブル + holiday_calendar + holiday_calendar_entry 参照
- `SystemSettingsService.java` — pattern 関連参照
- `data-dev.sql` — 全テーブルの INSERT 文
- `R__test_infrastructure.sql` — テストデータ

### Step 4: バックエンド ドメインクラス リネーム
ファイル名とクラス名を一括変更（git mv + 内容のリネーム）。

対象: 上記「ドメインクラス/ファイルリネーム」表の全エントリ（約13クラス + 約9テストファイル）

参照箇所の更新対象（import 文、型参照、変数名）:
- `AdminTenantController.java`, `OrganizationController.java`
- `AdminMasterDataController.java`
- `TenantSettingsController.java`
- `DateInfoService.java`, `HolidayResolutionService.java`
- `Tenant.java`, `Organization.java`
- `SecurityConfig.kt`
- 全テストファイル（約15ファイル）

### Step 5: API エンドポイントパス変更
コントローラの `@RequestMapping` パスを更新。

ファイル:
- `FiscalYearRuleController.java` (旧 FiscalYearPatternController) — `/api/fiscal-year-patterns` → `/api/fiscal-year-rules`
- `MonthlyPeriodRuleController.java` — `/api/monthly-period-patterns` → `/api/monthly-period-rules`
- `AdminMasterDataController.java` — preset エンドポイントパス更新
- `SecurityConfig.kt` — パスパターン更新

### Step 6: フロントエンド更新
- `api.ts` — API パスと型名の更新
- `FiscalYearPatternForm.tsx` → `FiscalYearRuleForm.tsx` (ファイル名 + 内容)
- `MonthlyPeriodPatternForm.tsx` → `MonthlyPeriodRuleForm.tsx`
- `MasterDataTabs.tsx` — コンポーネント参照更新
- `TenantSettingsSection.tsx` — pattern 参照更新
- 管理画面ページ: `organizations/page.tsx`, `tenants/page.tsx`
- E2E テスト: `tenant-onboarding.spec.ts`, `settings-inheritance.spec.ts`
- ユニットテスト: 約6ファイル

### Step 7: i18n キー確認
`frontend/messages/en.json` 内の "pattern" 関連キーを確認し、必要に応じて更新。

## audit_log → audit_logs 移行の詳細

スキーマ差分:
```
audit_log (V2):  tenant_id, user_id, action, resource_type, resource_id, details
audit_logs (V11): user_id, event_type, ip_address, timestamp, details, retention_days
```

マッピング方針:
- `action` → `event_type` (e.g. "TENANT_CREATED" → "TENANT_CREATED")
- `tenant_id`, `resource_type`, `resource_id` → `details` JSONB に含める
- `ip_address` → null (サーバーサイド処理のため取得不可）
- `retention_days` → デフォルト値 (365)

## 検証方法

1. **マイグレーション検証**: `./gradlew flywayMigrate` が成功すること
2. **バックエンドビルド**: `./gradlew build` がコンパイルエラーなく通ること
3. **バックエンドテスト**: `./gradlew test` が全て PASS すること
4. **フロントエンドビルド**: `npm run build` が通ること
5. **フロントエンドテスト**: `npm test` が全て PASS すること
6. **E2E テスト**: `npx playwright test --project=chromium` が PASS すること
7. **Spotless**: `./gradlew spotlessCheck` が通ること
8. **Biome**: `npx biome ci` が通ること

## リスク

- **影響範囲が広い**: バックエンド約30ファイル、フロントエンド約15ファイル。テスト全パスで漏れを検出
- **API 破壊的変更**: エンドポイントパスが変わる。現在は内部利用のみなのでフロントエンドと同時変更で対応可
- **PostgreSQL DDL**: `ALTER TABLE RENAME` はメタデータのみの操作で高速。FK 参照は自動更新される
