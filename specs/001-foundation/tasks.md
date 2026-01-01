# Tasks: Foundation Infrastructure

**Input**: Design documents from `/specs/001-foundation/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: テストは TDD で実装（テスト先行）

**Organization**: タスクはユーザーストーリー単位でグループ化

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 並列実行可能（異なるファイル、依存なし）
- **[Story]**: 対応するユーザーストーリー（US1, US2, US3, US4）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: プロジェクト初期化と基本構造

- [X] T001 [P] Gradle 依存関係の追加（Testcontainers, Database Rider, Instancio, Kotlin Test）
  - `backend/build.gradle.kts`

- [X] T002 [P] テスト用ディレクトリ構成の作成
  - `backend/src/test/kotlin/com/worklog/`

- [X] T003 [P] src/main パッケージ構成の作成（domain/shared, domain/tenant, eventsourcing, etc.）
  - `backend/src/main/kotlin/com/worklog/`
  - `backend/src/main/java/com/worklog/`

---

## Phase 2: Foundational (Blocking Prerequisites) - US4 テスト基盤

**Purpose**: すべてのユーザーストーリー実装の前提となる基盤

**CRITICAL**: この Phase が完了するまで他のストーリーは開始不可

### Implementation for US4

- [X] T004 [US4] IntegrationTestBase.kt の作成（Testcontainers + Spring Boot）
  - `backend/src/test/kotlin/com/worklog/IntegrationTestBase.kt`
  - PostgreSQL 16 コンテナ設定
  - `@DynamicPropertySource` で接続情報設定
  - コンテナ再利用設定（`.withReuse(true)`）

- [X] T005 [US4] Database Rider 設定
  - `backend/src/test/resources/dbunit.yml` 設定ファイル
  - `@DBRider` アノテーション対応

- [X] T006 [US4] Instancio 基本設定の確認
  - 簡単なテストで動作確認

- [X] T007 [US4] テスト実行確認
  - `./gradlew test` でコンテナ起動・マイグレーション適用を確認

**Checkpoint**: テスト基盤が動作し、Flyway マイグレーションが自動適用される

---

## Phase 3: User Story 3 - イベントソーシング基盤 (Priority: P1)

**Goal**: すべての状態変更をイベントとして記録する基盤を構築

**Independent Test**: EventStore への append/load が正常動作し、楽観的ロックが機能する

### Tests (TDD: テスト先行)

- [X] T008 [US3] JdbcEventStoreTest の作成
  - `backend/src/test/kotlin/com/worklog/eventsourcing/JdbcEventStoreTest.kt`
  - append/load テスト
  - 楽観的ロックテスト
  - Database Rider でデータセット使用

- [X] T009 [P] [US3] SnapshotStoreTest の作成
  - `backend/src/test/kotlin/com/worklog/eventsourcing/SnapshotStoreTest.kt`
  - save/load テスト

- [X] T010 [P] [US3] AuditLoggerTest の作成
  - `backend/src/test/kotlin/com/worklog/eventsourcing/AuditLoggerTest.kt`
  - 監査ログ記録テスト

### Domain Layer

- [X] T011 [P] [US3] DomainEvent インターフェースの作成
  - `backend/src/main/java/com/worklog/domain/shared/DomainEvent.java`

- [X] T012 [P] [US3] AggregateRoot 基底クラスの作成
  - `backend/src/main/java/com/worklog/domain/shared/AggregateRoot.java`
  - イベント収集機能
  - バージョン管理

- [X] T013 [P] [US3] EntityId 基底クラスの作成
  - `backend/src/main/java/com/worklog/domain/shared/EntityId.java`
  - UUID ラッパー

- [X] T014 [P] [US3] DomainException の作成
  - `backend/src/main/java/com/worklog/domain/shared/DomainException.java`

- [X] T015 [P] [US3] OptimisticLockException の作成
  - `backend/src/main/java/com/worklog/domain/shared/OptimisticLockException.java`

### Event Sourcing Infrastructure

- [X] T016 [US3] StoredEvent クラスの作成
  - `backend/src/main/java/com/worklog/eventsourcing/StoredEvent.java`
  - id, aggregateType, aggregateId, eventType, payload, version, createdAt

- [X] T017 [US3] EventStore インターフェースの作成
  - `backend/src/main/java/com/worklog/eventsourcing/EventStore.java`
  - `append(aggregateId, aggregateType, events, expectedVersion)`
  - `load(aggregateId)`

- [X] T018 [US3] JdbcEventStore 実装
  - `backend/src/main/java/com/worklog/eventsourcing/JdbcEventStore.java`
  - Spring Data JDBC 使用
  - 楽観的ロック実装

- [X] T019 [US3] SnapshotStore インターフェースの作成
  - `backend/src/main/java/com/worklog/eventsourcing/SnapshotStore.java`
  - `save(aggregateId, version, state)`
  - `load(aggregateId)`

- [X] T020 [US3] JdbcSnapshotStore 実装
  - `backend/src/main/java/com/worklog/eventsourcing/JdbcSnapshotStore.java`

- [X] T021 [US3] AuditLogger インターフェースの作成
  - `backend/src/main/java/com/worklog/eventsourcing/AuditLogger.java`
  - `log(tenantId, userId, action, resourceType, resourceId, details)`

- [X] T022 [US3] JdbcAuditLogger 実装
  - `backend/src/main/java/com/worklog/eventsourcing/JdbcAuditLogger.java`

- [X] T023 [P] [US3] JacksonConfig の作成
  - `backend/src/main/kotlin/com/worklog/infrastructure/config/JacksonConfig.kt`
  - JSONB シリアライズ設定

**Checkpoint**: EventStore, SnapshotStore, AuditLogger が正常動作

---

## Phase 4: User Story 1 - テナント・組織の作成と管理 (Priority: P1)

**Goal**: テナントと組織構造を作成・管理できる

**Independent Test**: テナント作成 API と組織作成 API が正常動作

### DB Migration

- [X] T024 [US1] V2__foundation.sql の作成
  - `backend/src/main/resources/db/migration/V2__foundation.sql`
  - tenant, organization, fiscal_year_pattern, monthly_period_pattern テーブル
  - es_aggregate, es_event, es_snapshot, audit_log テーブル
  - インデックス

### Tests (TDD: テスト先行)

- [X] T025 [P] [US1] TenantFixtures の作成
  - `backend/src/test/kotlin/com/worklog/fixtures/TenantFixtures.kt`

- [X] T026 [P] [US1] OrganizationFixtures の作成
  - `backend/src/test/kotlin/com/worklog/fixtures/OrganizationFixtures.kt`

- [X] T027 [US1] TenantTest（ドメイン単体テスト）の作成
  - `backend/src/test/kotlin/com/worklog/domain/tenant/TenantTest.kt`
  - create, update, deactivate, activate テスト

- [X] T028 [US1] OrganizationTest（ドメイン単体テスト）の作成
  - `backend/src/test/kotlin/com/worklog/domain/organization/OrganizationTest.kt`
  - 階層レベル検証テスト（max 6）
  - 循環参照チェックテスト
  - 自己参照チェックテスト

- [ ] T029 [US1] TenantControllerTest（統合テスト）の作成
  - `backend/src/test/kotlin/com/worklog/api/TenantControllerTest.kt`
  - POST /api/v1/tenants
  - GET /api/v1/tenants/{id}
  - POST /api/v1/tenants/{id}/deactivate
  - POST /api/v1/tenants/{id}/activate

- [ ] T030 [US1] OrganizationControllerTest（統合テスト）の作成
  - `backend/src/test/kotlin/com/worklog/api/OrganizationControllerTest.kt`
  - POST /api/v1/tenants/{tenantId}/organizations
  - GET /api/v1/tenants/{tenantId}/organizations/{id}
  - POST .../deactivate, .../activate

### Domain Layer - Tenant

- [X] T031 [P] [US1] TenantId ValueObject の作成
  - `backend/src/main/java/com/worklog/domain/tenant/TenantId.java`

- [X] T032 [P] [US1] Code ValueObject の作成
  - `backend/src/main/java/com/worklog/domain/shared/Code.java`

- [X] T033 [US1] TenantCreated イベントの作成
  - `backend/src/main/java/com/worklog/domain/tenant/TenantCreated.java`

- [X] T034 [US1] TenantUpdated イベントの作成
  - `backend/src/main/java/com/worklog/domain/tenant/TenantUpdated.java`

- [X] T035 [US1] TenantDeactivated イベントの作成
  - `backend/src/main/java/com/worklog/domain/tenant/TenantDeactivated.java`

- [X] T036 [US1] TenantActivated イベントの作成
  - `backend/src/main/java/com/worklog/domain/tenant/TenantActivated.java`

- [X] T037 [US1] Tenant Aggregate の作成
  - `backend/src/main/java/com/worklog/domain/tenant/Tenant.java`
  - ファクトリメソッド `create(code, name)`
  - `update(name)`, `deactivate()`, `activate()` メソッド
  - イベント発行

### Domain Layer - Organization

- [X] T038 [P] [US1] OrganizationId ValueObject の作成
  - `backend/src/main/java/com/worklog/domain/organization/OrganizationId.java`

- [X] T039 [US1] OrganizationCreated イベントの作成
  - `backend/src/main/java/com/worklog/domain/organization/OrganizationCreated.java`

- [X] T040 [US1] OrganizationUpdated イベントの作成
  - `backend/src/main/java/com/worklog/domain/organization/OrganizationUpdated.java`

- [X] T041 [US1] OrganizationDeactivated イベントの作成
  - `backend/src/main/java/com/worklog/domain/organization/OrganizationDeactivated.java`

- [X] T042 [US1] OrganizationActivated イベントの作成
  - `backend/src/main/java/com/worklog/domain/organization/OrganizationActivated.java`

- [X] T043 [US1] Organization Aggregate の作成
  - `backend/src/main/java/com/worklog/domain/organization/Organization.java`
  - ファクトリメソッド `create(...)`
  - 階層レベル検証（max 6）
  - 循環参照チェック
  - 自己参照チェック
  - deactivate/activate メソッド

### Infrastructure Layer

- [X] T044 [P] [US1] TenantRepository の作成
  - `backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java`

- [X] T045 [P] [US1] OrganizationRepository の作成
  - `backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java`

### Application Layer

- [X] T046 [P] [US1] CreateTenantCommand の作成
  - `backend/src/main/java/com/worklog/application/command/CreateTenantCommand.java`

- [X] T047 [P] [US1] CreateOrganizationCommand の作成
  - `backend/src/main/java/com/worklog/application/command/CreateOrganizationCommand.java`

- [X] T048 [US1] TenantService の作成
  - `backend/src/main/java/com/worklog/application/service/TenantService.java`
  - create, update, deactivate, activate, findById

- [X] T049 [US1] OrganizationService の作成
  - `backend/src/main/java/com/worklog/application/service/OrganizationService.java`
  - create, update, deactivate, activate, findById
  - 循環参照バリデーション
  - 自己参照バリデーション

### API Layer

- [ ] T050 [US1] TenantController の作成
  - `backend/src/main/java/com/worklog/api/TenantController.java`
  - POST /api/v1/tenants
  - GET /api/v1/tenants
  - GET /api/v1/tenants/{id}
  - PATCH /api/v1/tenants/{id}
  - POST /api/v1/tenants/{id}/deactivate
  - POST /api/v1/tenants/{id}/activate

- [ ] T051 [US1] OrganizationController の作成
  - `backend/src/main/java/com/worklog/api/OrganizationController.java`
  - POST /api/v1/tenants/{tenantId}/organizations
  - GET /api/v1/tenants/{tenantId}/organizations
  - GET /api/v1/tenants/{tenantId}/organizations/{id}
  - PATCH /api/v1/tenants/{tenantId}/organizations/{id}
  - POST .../deactivate
  - POST .../activate

- [ ] T052 [US1] SecurityConfig の更新（新エンドポイント許可）
  - `backend/src/main/kotlin/com/worklog/infrastructure/config/SecurityConfig.kt`

**Checkpoint**: テナント・組織の CRUD API が正常動作（deactivate/activate 含む）

---

## Phase 5: User Story 2 - 年度・月度パターンの設定 (Priority: P1)

**Goal**: 組織ごとに年度パターンと月度パターンを設定し、日付計算ができる

**Independent Test**: 年度・月度パターン設定後、任意の日付から正しい年度・月度が計算される

### Tests (TDD: テスト先行)

- [ ] T053 [P] [US2] FiscalYearPatternFixtures の作成
  - `backend/src/test/kotlin/com/worklog/fixtures/FiscalYearPatternFixtures.kt`

- [ ] T054 [P] [US2] MonthlyPeriodPatternFixtures の作成
  - `backend/src/test/kotlin/com/worklog/fixtures/MonthlyPeriodPatternFixtures.kt`

- [ ] T055 [US2] FiscalYearPatternTest（ドメイン単体テスト）の作成
  - `backend/src/test/kotlin/com/worklog/domain/fiscalyear/FiscalYearPatternTest.kt`
  - startMonth, startDay バリデーションテスト
  - getFiscalYear() 計算テスト

- [ ] T056 [US2] MonthlyPeriodPatternTest（ドメイン単体テスト）の作成
  - `backend/src/test/kotlin/com/worklog/domain/monthlyperiod/MonthlyPeriodPatternTest.kt`
  - startDay バリデーションテスト（1-28）
  - getMonthlyPeriod() 計算テスト

- [ ] T057 [US2] DateInfoServiceTest の作成（20+ テストケース: SC-002）
  - `backend/src/test/kotlin/com/worklog/application/service/DateInfoServiceTest.kt`
  - 4月開始パターン（日本標準）
  - 11月開始パターン（年度またぎ）
  - 21日締めパターン
  - 1日締めパターン（月末締め）
  - 年度またぎ境界テスト
  - パターン継承テスト（親組織から継承）
  - ルート組織パターン必須テスト（FR-012a）

- [ ] T058 [US2] FiscalYearPatternControllerTest の作成
  - `backend/src/test/kotlin/com/worklog/api/FiscalYearPatternControllerTest.kt`
  - POST /api/v1/tenants/{tenantId}/fiscal-year-patterns
  - GET /api/v1/tenants/{tenantId}/fiscal-year-patterns
  - GET /api/v1/tenants/{tenantId}/fiscal-year-patterns/{id}

- [ ] T059 [US2] MonthlyPeriodPatternControllerTest の作成
  - `backend/src/test/kotlin/com/worklog/api/MonthlyPeriodPatternControllerTest.kt`
  - POST /api/v1/tenants/{tenantId}/monthly-period-patterns
  - GET /api/v1/tenants/{tenantId}/monthly-period-patterns
  - GET /api/v1/tenants/{tenantId}/monthly-period-patterns/{id}

- [ ] T060 [US2] DateInfoEndpointTest の作成
  - `backend/src/test/kotlin/com/worklog/api/DateInfoEndpointTest.kt`
  - POST /api/v1/tenants/{tenantId}/organizations/{id}/date-info

### Domain Layer - FiscalYearPattern

- [ ] T061 [P] [US2] FiscalYearPatternId ValueObject の作成
  - `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPatternId.java`

- [ ] T062 [US2] FiscalYearPatternCreated イベントの作成
  - `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPatternCreated.java`

- [ ] T063 [US2] FiscalYearPattern エンティティの作成
  - `backend/src/main/java/com/worklog/domain/fiscalyear/FiscalYearPattern.java`
  - startMonth (1-12), startDay (1-31) のバリデーション
  - `getFiscalYear(date)` 計算ロジック
  - `getFiscalYearRange(fiscalYear)` 計算ロジック

### Domain Layer - MonthlyPeriodPattern

- [ ] T064 [P] [US2] MonthlyPeriodPatternId ValueObject の作成
  - `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPatternId.java`

- [ ] T065 [US2] MonthlyPeriodPatternCreated イベントの作成
  - `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPatternCreated.java`

- [ ] T066 [US2] MonthlyPeriodPattern エンティティの作成
  - `backend/src/main/java/com/worklog/domain/monthlyperiod/MonthlyPeriodPattern.java`
  - startDay のバリデーション（1-28）
  - `getMonthlyPeriod(date)` 計算ロジック

### Application Layer

- [ ] T067 [US2] DateInfo ValueObject の作成
  - `backend/src/main/java/com/worklog/application/service/DateInfo.java`
  - fiscalYear, fiscalYearStart, fiscalYearEnd
  - monthlyPeriodStart, monthlyPeriodEnd, displayMonth, displayYear

- [ ] T068 [US2] DateInfoService の作成
  - `backend/src/main/java/com/worklog/application/service/DateInfoService.java`
  - 年度計算ロジック
  - 月度計算ロジック
  - 年度またぎ対応
  - パターン継承解決（親組織から継承）
  - ルート組織パターン必須検証（FR-012a）

### Infrastructure Layer

- [ ] T069 [P] [US2] FiscalYearPatternRepository の作成
  - `backend/src/main/java/com/worklog/infrastructure/persistence/FiscalYearPatternRepository.java`

- [ ] T070 [P] [US2] MonthlyPeriodPatternRepository の作成
  - `backend/src/main/java/com/worklog/infrastructure/persistence/MonthlyPeriodPatternRepository.java`

### API Layer

- [ ] T071 [US2] FiscalYearPatternController の作成
  - `backend/src/main/java/com/worklog/api/FiscalYearPatternController.java`
  - POST /api/v1/tenants/{tenantId}/fiscal-year-patterns
  - GET /api/v1/tenants/{tenantId}/fiscal-year-patterns
  - GET /api/v1/tenants/{tenantId}/fiscal-year-patterns/{id}

- [ ] T072 [US2] MonthlyPeriodPatternController の作成
  - `backend/src/main/java/com/worklog/api/MonthlyPeriodPatternController.java`
  - POST /api/v1/tenants/{tenantId}/monthly-period-patterns
  - GET /api/v1/tenants/{tenantId}/monthly-period-patterns
  - GET /api/v1/tenants/{tenantId}/monthly-period-patterns/{id}

- [ ] T073 [US2] OrganizationController に date-info エンドポイント追加
  - POST /api/v1/tenants/{tenantId}/organizations/{id}/date-info
  - リクエスト: `{ "date": "YYYY-MM-DD" }`
  - レスポンス: DateInfo

**Checkpoint**: 年度・月度パターンの設定と日付計算 API が正常動作

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 複数のユーザーストーリーに影響する改善

- [ ] T074 [P] GlobalExceptionHandler の作成
  - `backend/src/main/java/com/worklog/api/GlobalExceptionHandler.java`
  - DomainException → 400 Bad Request
  - OptimisticLockException → 409 Conflict
  - EntityNotFoundException → 404 Not Found

- [ ] T075 [P] ErrorResponse DTO の作成
  - `backend/src/main/java/com/worklog/api/ErrorResponse.java`
  - error, message, timestamp, details フィールド

- [ ] T076 レスポンスタイム検証テスト（SC-001: < 100ms p95）
  - `backend/src/test/kotlin/com/worklog/api/PerformanceTest.kt`

- [ ] T077 コードカバレッジ確認（SC-007: 80%+ ドメイン層）
  - JaCoCo レポート確認

- [ ] T078 コードレビュー・リファクタリング

- [ ] T079 README 更新（Phase 1 完了）

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (US4: テスト基盤) ← すべてのテストの前提
    ↓
Phase 3 (US3: EventStore, SnapshotStore, AuditLogger) ← ドメインイベント記録の前提
    ↓
Phase 4 (US1: Tenant/Organization) ← deactivate/activate 含む
    ↓
Phase 5 (US2: FiscalYear/MonthlyPeriod) ← パターン継承含む
    ↓
Phase 6 (Polish)
```

### Within Each Phase (TDD)

1. **テスト作成** → 実行して失敗確認
2. **実装** → テストがパスするまで
3. ValueObject → Event → Aggregate → Repository → Service → Controller
4. [P] マーク付きタスクは並列実行可能

---

## Implementation Strategy

### 推奨順序

1. **Phase 1 + Phase 2**: テスト基盤を最優先で構築
2. **Phase 3**: EventStore, SnapshotStore, AuditLogger 実装
3. **Phase 4**: Tenant/Organization（deactivate/activate 含む）
4. **Phase 5**: FiscalYear/MonthlyPeriod（日付計算、パターン継承）
5. **Phase 6**: 仕上げ（例外ハンドリング、パフォーマンス検証）

### 各 Checkpoint でのバリデーション

- `./gradlew test` ですべてのテストがパス
- API エンドポイントが Postman/curl で動作確認可能
- イベントが es_event テーブルに記録されている
- 監査ログが audit_log テーブルに記録されている

---

## Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| Phase 1 | T001-T003 | Setup |
| Phase 2 | T004-T007 | US4: テスト基盤 |
| Phase 3 | T008-T023 | US3: EventStore, SnapshotStore, AuditLogger |
| Phase 4 | T024-T052 | US1: Tenant/Organization (deactivate/activate 含む) |
| Phase 5 | T053-T073 | US2: FiscalYear/MonthlyPeriod, DateInfo |
| Phase 6 | T074-T079 | Polish |
| **Total** | **79 tasks** | |

---

## Notes

- [P] タスク = 異なるファイル、依存なし → 並列実行可能
- [Story] ラベルでユーザーストーリーへの紐付けを明示
- 各タスク完了後にコミット推奨
- TDD: テスト先行、失敗確認後に実装
- すべてのファイルパスは `backend/` プレフィックス付き
- API パスは `/api/v1/tenants/{tenantId}/...` 形式で統一
