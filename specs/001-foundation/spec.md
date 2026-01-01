# Feature Specification: Foundation Infrastructure

**Feature Branch**: `001-foundation`  
**Created**: 2026-01-01  
**Status**: Draft  
**Input**: User description: "Engineer Management System - Phase 1: 基盤構築（Tenant, Organization, FiscalYearPattern, MonthlyPeriodPattern, EventStore, テスト基盤）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - テナント・組織の作成と管理 (Priority: P1)

システム管理者として、テナントと組織構造を作成・管理できる。
これにより、マルチテナント環境でエンジニア管理システムを運用できる。

**Why this priority**: 他のすべてのドメイン（Member, Project, WorkLog, Assignment）がテナント・組織に依存するため、最優先で実装が必要。

**Independent Test**: テナント作成 API と組織作成 API を呼び出し、階層構造を持つ組織ツリーが構築できることを確認。

**Acceptance Scenarios**:

1. **Given** システムが起動している, **When** テナント作成 API を呼び出す, **Then** 新しいテナントが作成され UUID が返される
2. **Given** テナントが存在する, **When** 組織作成 API を親組織なしで呼び出す, **Then** ルート組織（level=1）が作成される
3. **Given** ルート組織が存在する, **When** 組織作成 API を親組織を指定して呼び出す, **Then** 子組織（level=2）が作成される
4. **Given** level 6 の組織が存在する, **When** その組織を親として組織作成を試みる, **Then** エラー（最大階層超過）が返される
5. **Given** 組織が存在する, **When** 組織を非アクティブ化する, **Then** is_active が false になりイベントが記録される

---

### User Story 2 - 年度・月度パターンの設定 (Priority: P1)

システム管理者として、組織ごとに年度パターンと月度パターンを設定できる。
これにより、異なる会計年度・締め日を持つ組織を統一システムで管理できる。

**Why this priority**: WorkLog の年度・月度計算に必須。Member, Assignment の管理開始前に必要。

**Independent Test**: 年度パターン（4月開始）と月度パターン（21日締め）を設定し、任意の日付から正しい年度・月度が計算されることを確認。

**Acceptance Scenarios**:

1. **Given** テナントが存在する, **When** 年度パターン（4月1日開始）を作成する, **Then** パターンが保存される
2. **Given** テナントが存在する, **When** 年度パターン（11月1日開始）を作成する, **Then** パターンが保存される
3. **Given** テナントが存在する, **When** 月度パターン（21日開始）を作成する, **Then** パターンが保存される
4. **Given** テナントが存在する, **When** 月度パターン（1日開始＝月末締め）を作成する, **Then** パターンが保存される
5. **Given** 組織に年度・月度パターンが設定されている, **When** 日付情報 API を呼び出す, **Then** 正しい年度・月度・期間開始日・期間終了日が返される

---

### User Story 3 - イベントソーシング基盤 (Priority: P1)

開発者として、すべての状態変更がイベントとして記録される基盤を使用できる。
これにより、監査ログ・状態再構築・Projection更新が可能になる。

**Why this priority**: DDD + イベントソーシングアーキテクチャの中核。すべてのドメインロジック実装の前提条件。

**Independent Test**: Aggregate にイベントを適用し、EventStore に保存、ロード後に状態が復元されることを確認。

**Acceptance Scenarios**:

1. **Given** EventStore が空, **When** イベントを append する, **Then** イベントが保存され version=1 になる
2. **Given** version=1 のイベントが存在する, **When** 新しいイベントを append する, **Then** version=2 で保存される
3. **Given** 複数のイベントが存在する, **When** load(aggregateId) を呼ぶ, **Then** すべてのイベントが順番に返される
4. **Given** version=2 のイベントが存在する, **When** expectedVersion=1 で append を試みる, **Then** 楽観的ロックエラーが発生する
5. **Given** Aggregate にイベントを適用する, **When** 保存してから再ロードする, **Then** 同じ状態が復元される

---

### User Story 4 - テスト基盤の構築 (Priority: P1)

開発者として、本番と同等の PostgreSQL 環境でテストを実行できる。
これにより、信頼性の高い統合テストが可能になる。

**Why this priority**: TDD/BDD での開発を進めるための前提条件。最初に構築が必要。

**Independent Test**: Testcontainers で PostgreSQL が起動し、Flyway マイグレーションが適用され、Database Rider でデータセットが投入できることを確認。

**Acceptance Scenarios**:

1. **Given** テストクラスを実行する, **When** Testcontainers が起動する, **Then** PostgreSQL コンテナが利用可能になる
2. **Given** PostgreSQL コンテナが起動している, **When** Spring コンテキストがロードされる, **Then** Flyway マイグレーションが自動適用される
3. **Given** Database Rider が設定されている, **When** @DataSet でデータセットを指定する, **Then** テスト前にデータが投入される
4. **Given** Instancio が設定されている, **When** テストコードでエンティティを生成する, **Then** ランダムデータが自動生成される
5. **Given** テストが完了する, **When** 次のテストが開始する, **Then** データベースがクリーンな状態にリセットされる

---

### Edge Cases

- 同一テナント内で重複する組織コードを作成しようとした場合 → エラー
- 組織の親を自分自身に設定しようとした場合 → エラー
- 循環参照（A→B→C→A）を作成しようとした場合 → エラー
- 年度パターンで不正な月（0, 13）を指定した場合 → バリデーションエラー
- 月度パターンで不正な日（0, 32）を指定した場合 → バリデーションエラー
- 年度をまたぐ日付計算（例：11月開始で翌年10月終了）→ 正しく計算
- EventStore への同時書き込み → 楽観的ロックで片方が失敗

## Clarifications

### Session 2026-01-01

- Q: Phase 1 の API 認証方式は？ → A: すべての API に Basic 認証を要求（現状の設定を維持）
- Q: 組織の物理削除は許可するか？ → A: 物理削除は不可（deactivate のみ）
- Q: 年度・月度パターン未設定時の解決方法は？ → A: 親組織のパターンを継承（ルート組織は必須）
- Q: テナントの物理削除は許可するか？ → A: 物理削除は不可（deactivate のみ、組織と同様）
- Q: EventStore のイベント保持期間は？ → A: 無期限保持（アーカイブは後続 Phase で検討）
- Q: SC-001 のレスポンスタイム < 100ms はどのパーセンタイルか？ → A: p95（95パーセンタイル）

## Requirements *(mandatory)*

### Functional Requirements

#### テナント・組織

- **FR-001**: System MUST allow creating a tenant with unique code and name
- **FR-001a**: System MUST allow deactivating/activating tenants (physical deletion is NOT permitted)
- **FR-002**: System MUST allow creating organizations with hierarchical structure (max 6 levels)
- **FR-003**: System MUST validate that organization code is unique within a tenant
- **FR-004**: System MUST allow deactivating/activating organizations (physical deletion is NOT permitted)
- **FR-005**: System MUST support multiple organizations per tenant with parent-child relationships
- **FR-006**: System MUST enforce maximum hierarchy depth of 6 levels at application level

#### 年度・月度パターン

- **FR-007**: System MUST allow creating fiscal year patterns with configurable start month and day
- **FR-008**: System MUST allow creating monthly period patterns with configurable start day (1-28)
- **FR-009**: System MUST calculate fiscal year from a given date based on organization's pattern (inherits from parent if not set)
- **FR-010**: System MUST calculate monthly period (start/end dates) from a given date based on organization's pattern (inherits from parent if not set)
- **FR-011**: System MUST support fiscal years that span calendar years (e.g., November to October)
- **FR-012**: System MUST combine fiscal year pattern and monthly period pattern for date calculations
- **FR-012a**: System MUST require root organizations to have fiscal year and monthly period patterns set

#### イベントソーシング

- **FR-013**: System MUST record all state changes as immutable events in EventStore
- **FR-013a**: System MUST retain all events indefinitely (archiving strategy to be defined in future Phase)
- **FR-014**: System MUST support optimistic locking via version field
- **FR-015**: System MUST serialize events as JSONB for flexible schema evolution
- **FR-016**: System MUST support loading all events for an aggregate by ID
- **FR-017**: System MUST support snapshot storage for performance optimization (future use)
- **FR-018**: System MUST record audit log entries for all significant operations

#### テスト基盤

- **FR-019**: System MUST use Testcontainers for isolated PostgreSQL instances in tests
- **FR-020**: System MUST apply Flyway migrations automatically in test environment
- **FR-021**: System MUST support Database Rider for declarative test data setup
- **FR-022**: System MUST support Instancio for programmatic test data generation

#### セキュリティ

- **FR-023**: System MUST require Basic authentication for all API endpoints except health check
- **FR-024**: System MUST return 401 Unauthorized for unauthenticated requests

### Key Entities

- **Tenant**: マルチテナントの最上位単位。code（一意）、name を持つ
- **Organization**: 組織。tenant_id、parent_id（階層）、code、name、level(1-6)、fiscal_year_pattern_id、monthly_period_pattern_id を持つ
- **FiscalYearPattern**: 年度パターン。tenant_id、name、start_month(1-12)、start_day(1-31) を持つ
- **MonthlyPeriodPattern**: 月度パターン。tenant_id、name、start_day(1-28) を持つ
- **EventStore**: イベント永続化。aggregate_type、aggregate_id、event_type、payload(JSONB)、version、created_at
- **Snapshot**: スナップショット。aggregate_type、aggregate_id、version、state(JSONB)
- **AuditLog**: 監査ログ。tenant_id、user_id、action、resource_type、resource_id、details(JSONB)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: テナント・組織の CRUD API が正常に動作し、レスポンスタイム < 100ms (p95)
- **SC-002**: 年度・月度計算 API が任意の日付に対して正しい結果を返す（テストケース 20 件以上で検証）
- **SC-003**: EventStore への append/load が正常に動作し、楽観的ロックが機能する
- **SC-004**: すべての統合テストが Testcontainers 上で独立して実行可能
- **SC-005**: テストスイート全体の実行時間 < 60秒（コンテナ再利用時）
- **SC-006**: 組織階層 6 レベルまでの作成・取得が正常に動作する
- **SC-007**: コードカバレッジ 80% 以上（ドメイン層）
