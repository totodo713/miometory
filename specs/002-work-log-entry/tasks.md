# Tasks: Work-Log Entry System

**Feature Branch**: `002-work-log-entry`  
**Created**: 2026-01-02  
**Status**: Planning  
**Total Tasks**: 130  
**Estimated Duration**: 22-27 business days

---

## Task Summary

| Phase | Tasks | Days | Status |
|-------|-------|------|--------|
| Phase 1: Backend - Domain Model | T001-T013 | 3-4 | ⏳ Pending |
| Phase 2: Backend - API | T014-T040 | 4-5 | ⏳ Pending |
| Phase 3: Frontend - Foundation | T041-T065 | 5-6 | ⏳ Pending |
| Phase 4: Frontend - Features | T066-T090 | 4-5 | ⏳ Pending |
| Phase 5: Testing & QA | T091-T110 | 3-4 | ⏳ Pending |
| Phase 6: Documentation & Deployment | T111-T130 | 3 | ⏳ Pending |

**Legend**: ⏳ Pending | 🔄 In Progress | ✅ Done | ❌ Blocked

---

## Phase 1: Backend - Domain Model (3-4 days)

### T001: Project エンティティ実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: None
- **Description**: 
  - Project Aggregate Root 実装
  - ProjectId, OrganizationId, code, name, isActive
  - Validation: code unique per organization
- **Acceptance**:
  - [ ] Project.kt 実装完了
  - [ ] バリデーション実装
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T002: Project イベント実装
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T001
- **Description**:
  - ProjectCreated イベント
  - ProjectUpdated イベント
  - ProjectDeactivated イベント
- **Acceptance**:
  - [ ] 3イベント実装完了
  - [ ] JSON serialization テスト
- **Status**: ⏳ Pending

---

### T003: Member エンティティ実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: None
- **Description**:
  - Member Aggregate Root 実装
  - MemberId, organizationId, employeeNumber, name, email, managerId, roles
  - Validation: employeeNumber unique, email unique
- **Acceptance**:
  - [ ] Member.kt 実装完了
  - [ ] Self-reference (managerId) 対応
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T004: Member イベント実装
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T003
- **Description**:
  - MemberCreated イベント
  - MemberRolesUpdated イベント
  - MemberManagerAssigned イベント
  - MemberDeactivated イベント
- **Acceptance**:
  - [ ] 4イベント実装完了
  - [ ] JSON serialization テスト
- **Status**: ⏳ Pending

---

### T005: WorkLog エンティティ実装
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T001, T003
- **Description**:
  - WorkLog Aggregate Root 実装
  - WorkLogId, memberId, projectId, workDate, hours, comment, inputBy, status
  - Validation: hours >= 0.25, daily total <= 24h
  - Status: DRAFT/SUBMITTED/APPROVED/REJECTED
- **Acceptance**:
  - [ ] WorkLog.kt 実装完了
  - [ ] 時間バリデーション実装
  - [ ] 単体テスト 8件以上
- **Status**: ⏳ Pending

---

### T006: WorkLog イベント実装
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T005
- **Description**:
  - WorkLogCreated イベント
  - WorkLogUpdated イベント
  - WorkLogSubmitted イベント
  - WorkLogApproved イベント
  - WorkLogRejected イベント
- **Acceptance**:
  - [ ] 5イベント実装完了
  - [ ] JSON serialization テスト
- **Status**: ⏳ Pending

---

### T007: Absence エンティティ実装
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T003
- **Description**:
  - Absence Aggregate Root 実装
  - AbsenceId, memberId, absenceDate, absenceType, hours, comment
  - AbsenceType enum: PAID_LEAVE/SICK_LEAVE/SPECIAL_LEAVE/OTHER
- **Acceptance**:
  - [ ] Absence.kt 実装完了
  - [ ] AbsenceType enum 実装
  - [ ] 単体テスト 4件以上
- **Status**: ⏳ Pending

---

### T008: Absence イベント実装
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T007
- **Description**:
  - AbsenceCreated イベント
  - AbsenceUpdated イベント
  - AbsenceDeleted イベント
- **Acceptance**:
  - [ ] 3イベント実装完了
  - [ ] JSON serialization テスト
- **Status**: ⏳ Pending

---

### T009: Holiday エンティティ実装
- **Priority**: P1
- **Estimate**: 2h
- **Dependencies**: None
- **Description**:
  - Holiday Entity 実装（Event Sourcing不要）
  - HolidayId, date, name, year
- **Acceptance**:
  - [ ] Holiday.kt 実装完了
  - [ ] 単体テスト 2件以上
- **Status**: ⏳ Pending

---

### T010: Project Repository 実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T001, T002
- **Description**:
  - ProjectRepository (EventStore + Projection)
  - save(), findById(), findByOrganizationId()
- **Acceptance**:
  - [ ] Repository実装完了
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T011: Member Repository 実装
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T003, T004
- **Description**:
  - MemberRepository (EventStore + Projection)
  - save(), findById(), findByManagerId()
  - findSubordinates() - 配下メンバー取得
- **Acceptance**:
  - [ ] Repository実装完了
  - [ ] 階層検索テスト
  - [ ] 統合テスト 5件以上
- **Status**: ⏳ Pending

---

### T012: WorkLog Repository 実装
- **Priority**: P0
- **Estimate**: 6h
- **Dependencies**: T005, T006
- **Description**:
  - WorkLogRepository (EventStore + Projection)
  - save(), findById(), findByMemberIdAndDateRange()
  - findByMemberIdAndMonth() - 月度単位取得
- **Acceptance**:
  - [ ] Repository実装完了
  - [ ] 複合検索テスト
  - [ ] 統合テスト 6件以上
- **Status**: ⏳ Pending

---

### T013: Flyway Migration (V4)
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: None
- **Description**:
  - V4__work_log_entry_tables.sql 作成
  - 5テーブル作成: project, member, work_log, absence, holiday
  - インデックス作成
- **Acceptance**:
  - [ ] マイグレーション成功
  - [ ] 全制約・インデックス適用確認
  - [ ] Rollback テスト
- **Status**: ⏳ Pending

---

## Phase 2: Backend - API (4-5 days)

### T014: GET /api/worklogs - 月度単位取得API
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T012
- **Description**:
  - 月度単位の稼働時間一覧取得
  - Query params: memberId, fiscalYear, monthlyPeriod
  - Response: worklogs + absences + holidays + summary
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 3件以上
  - [ ] パフォーマンステスト < 100ms
- **Status**: ⏳ Pending

---

### T015: POST /api/worklogs - 稼働時間登録API
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T012
- **Description**:
  - 日単位の稼働時間登録（複数案件対応）
  - Validation: total hours <= 24h, hours in 0.25 increments
- **Acceptance**:
  - [ ] API実装完了
  - [ ] バリデーションテスト 5件以上
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T016: PUT /api/worklogs/{id} - 稼働時間更新API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T012
- **Description**:
  - 稼働時間更新
  - DRAFT/SUBMITTED のみ更新可能
- **Acceptance**:
  - [ ] API実装完了
  - [ ] ステータスチェックテスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T017: DELETE /api/worklogs/{id} - 稼働時間削除API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T012
- **Description**:
  - 稼働時間削除
  - DRAFT のみ削除可能
- **Acceptance**:
  - [ ] API実装完了
  - [ ] ステータスチェックテスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T018: POST /api/worklogs/bulk-import - CSV一括インポートAPI
- **Priority**: P1
- **Estimate**: 6h
- **Dependencies**: T012
- **Description**:
  - CSV一括インポート
  - バリデーション: 行単位エラー返却
  - バッチ処理: 1,000件ずつ
- **Acceptance**:
  - [ ] API実装完了
  - [ ] CSV パースエラーハンドリング
  - [ ] 統合テスト 5件以上
  - [ ] パフォーマンステスト < 1s/100件
- **Status**: ⏳ Pending

---

### T019: GET /api/worklogs/export - CSV一括エクスポートAPI
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T012
- **Description**:
  - CSV一括エクスポート
  - Content-Type: text/csv
  - Content-Disposition: attachment
- **Acceptance**:
  - [ ] API実装完了
  - [ ] CSV形式正常
  - [ ] 統合テスト 2件以上
  - [ ] パフォーマンステスト < 500ms (1ヶ月分)
- **Status**: ⏳ Pending

---

### T020: POST /api/worklogs/copy-from-previous-month - 前月コピーAPI
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T012
- **Description**:
  - 前月案件リストコピー（時間はコピーしない）
  - Response: コピーされた案件一覧
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T021: POST /api/worklogs/submit - 承認申請API
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T012
- **Description**:
  - 月度単位で承認申請
  - Status: DRAFT → SUBMITTED
- **Acceptance**:
  - [ ] API実装完了
  - [ ] ステータス遷移テスト
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T022: POST /api/worklogs/approve - 承認・差し戻しAPI
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T012, T011
- **Description**:
  - 承認・差し戻し処理
  - 権限チェック: 配下メンバーのみ
  - Actions: APPROVE/REJECT
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 権限チェックテスト
  - [ ] 統合テスト 5件以上
- **Status**: ⏳ Pending

---

### T023: POST /api/absences - 休暇登録API
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T007, T008
- **Description**:
  - 休暇登録
  - Validation: hours in 0.25 increments
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T024: PUT /api/absences/{id} - 休暇更新API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T007, T008
- **Description**:
  - 休暇更新
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T025: DELETE /api/absences/{id} - 休暇削除API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T007, T008
- **Description**:
  - 休暇削除
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T026: GET /api/holidays - 祝日一覧取得API
- **Priority**: P1
- **Estimate**: 2h
- **Dependencies**: T009
- **Description**:
  - 年度単位の祝日一覧取得
  - Query param: year
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T027: POST /api/holidays - 祝日登録API (管理者のみ)
- **Priority**: P2
- **Estimate**: 2h
- **Dependencies**: T009
- **Description**:
  - 祝日登録（管理者のみ）
  - 権限チェック: ADMIN role
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 権限チェックテスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T028: GET /api/projects - 案件一覧取得API
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T010
- **Description**:
  - 組織単位の案件一覧取得
  - Query params: organizationId, isActive
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T029: POST /api/projects - 案件登録API
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T010
- **Description**:
  - 案件登録
  - Validation: code unique per organization
- **Acceptance**:
  - [ ] API実装完了
  - [ ] バリデーションテスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T030: PUT /api/projects/{id} - 案件更新API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T010
- **Description**:
  - 案件更新
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T031: DELETE /api/projects/{id} - 案件削除API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T010
- **Description**:
  - 案件削除（非アクティブ化）
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T032: GET /api/members - メンバー一覧取得API
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T011
- **Description**:
  - メンバー一覧取得
  - Query params: organizationId
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T033: GET /api/members/{id}/subordinates - 配下メンバー取得API
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T011
- **Description**:
  - 配下メンバー一覧取得（代理入力用）
  - 再帰的に配下を取得
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 階層構造テスト
  - [ ] 統合テスト 3件以上
- **Status**: ⏳ Pending

---

### T034: POST /api/members - メンバー登録API
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T011
- **Description**:
  - メンバー登録
  - Validation: employeeNumber unique, email unique
- **Acceptance**:
  - [ ] API実装完了
  - [ ] バリデーションテスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T035: PUT /api/members/{id} - メンバー更新API
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T011
- **Description**:
  - メンバー更新（name, email, managerId, roles）
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T036: GlobalExceptionHandler 拡張
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: None
- **Description**:
  - WorkLog関連の例外ハンドリング追加
  - ValidationException, UnauthorizedException
- **Acceptance**:
  - [ ] 例外ハンドリング実装
  - [ ] 統合テスト 5件以上
- **Status**: ⏳ Pending

---

### T037: Security Config 拡張
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: None
- **Description**:
  - Role-based access control 実装
  - MEMBER/MANAGER/APPROVER roles
  - 配下メンバーチェック
- **Acceptance**:
  - [ ] SecurityConfig実装
  - [ ] 権限チェックテスト 10件以上
- **Status**: ⏳ Pending

---

### T038-T040: Backend Integration Tests
- **Priority**: P0
- **Estimate**: 8h
- **Description**:
  - 全API統合テスト
  - E2E シナリオテスト
  - パフォーマンステスト
- **Acceptance**:
  - [ ] 統合テスト 50件以上
  - [ ] カバレッジ 85%以上
- **Status**: ⏳ Pending

---

## Phase 3: Frontend - Foundation (5-6 days)

### T041: ライブラリセットアップ
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: None
- **Description**:
  - Zustand, react-day-picker, TanStack Table, date-fns, papaparse, zod
  - package.json 更新
- **Acceptance**:
  - [ ] 全ライブラリインストール完了
  - [ ] TypeScript型定義確認
- **Status**: ⏳ Pending

---

### T042: Zustand Store 設計
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T041
- **Description**:
  - worklogStore 作成
  - State: selectedYear, selectedMonth, worklogs, absences, holidays, projects
  - Actions: fetchWorklogs, addWorklog, updateWorklog, deleteWorklog
- **Acceptance**:
  - [ ] Store実装完了
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T042A-1: 自動保存用API拡張
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T042
- **Description**:
  - PATCH /api/worklogs/draft エンドポイント追加（FR-029対応）
  - 楽観的ロック対応（versionフィールド）
  - 競合検出時のレスポンス設計
- **Acceptance**:
  - [ ] API実装完了
  - [ ] 競合検出テスト
  - [ ] 統合テスト 2件以上
- **Status**: ⏳ Pending

---

### T042A-2: 自動保存Service実装（フロントエンド）
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T042A-1, T042
- **Description**:
  - TanStack Query mutation設定
  - 60秒間隔のタイマー実装
  - 変更検出ロジック（フォームダーティチェック）
- **Acceptance**:
  - [ ] 60秒ごとに自動保存実行
  - [ ] 変更なし時はスキップ
  - [ ] 単体テスト 4件以上
- **Status**: ⏳ Pending

---

### T042A-3: localStorage バックアップ機能
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T042A-2
- **Description**:
  - オフライン時のlocalStorage保存
  - オンライン復帰時の同期処理
  - 古いデータのクリーンアップ（7日以上前）
- **Acceptance**:
  - [ ] オフライン時保存確認
  - [ ] 同期処理テスト
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T042A-4: 競合解決UI
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T042A-2, T042A-1
- **Description**:
  - 競合検出時のダイアログ表示
  - 「自分の変更を保持」「サーバーの変更を取得」選択UI
  - 差分プレビュー表示
- **Acceptance**:
  - [ ] ダイアログ表示確認
  - [ ] 両選択肢の動作テスト
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T042B-1: アイドル検出ロジック
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T042
- **Description**:
  - マウス/キーボード/タッチイベントリスナー（FR-030対応）
  - APIコール追跡
  - 最終活動時刻の管理（Zustand state）
- **Acceptance**:
  - [ ] 各種イベント検出確認
  - [ ] 最終活動時刻更新テスト
  - [ ] 単体テスト 4件以上
- **Status**: ⏳ Pending

---

### T042B-2: タイムアウト警告ダイアログ
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T042B-1
- **Description**:
  - 28分時点での警告モーダル表示
  - カウントダウンタイマー（残り2分）
  - 「セッション延長」ボタン
- **Acceptance**:
  - [ ] 28分時点で表示確認
  - [ ] カウントダウン動作確認
  - [ ] 延長ボタン動作テスト
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T042B-3: タイムアウト時の処理
- **Priority**: P0
- **Estimate**: 1.5h
- **Dependencies**: T042B-2, T042A-2
- **Description**:
  - 30分経過時の自動ログアウト
  - ログアウト前の自動保存トリガー
  - ログイン画面へのリダイレクト
- **Acceptance**:
  - [ ] 30分時点でログアウト確認
  - [ ] 自動保存実行確認
  - [ ] リダイレクト動作テスト
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T065A-1: インジケーターコンポーネント基本実装
- **Priority**: P0
- **Estimate**: 1.5h
- **Dependencies**: T042A-2, T044
- **Description**:
  - ステータス表示コンポーネント（FR-031対応）
  - 「保存中...」「保存完了」「保存失敗」の状態管理
  - アイコン + テキスト表示
- **Acceptance**:
  - [ ] 3状態表示確認
  - [ ] 状態遷移テスト
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T065A-2: タイムスタンプ表示とツールチップ
- **Priority**: P0
- **Estimate**: 1h
- **Dependencies**: T065A-1
- **Description**:
  - 最終保存時刻の表示（「HH:MMに保存」）
  - ホバー時の詳細ツールチップ
  - 相対時間表示（「3分前に保存」）
- **Acceptance**:
  - [ ] タイムスタンプ表示確認
  - [ ] ツールチップ動作確認
  - [ ] 相対時間更新テスト
  - [ ] 単体テスト 2件以上
- **Status**: ⏳ Pending

---

### T065A-3: アクセシビリティ対応
- **Priority**: P0
- **Estimate**: 1h
- **Dependencies**: T065A-1
- **Description**:
  - ARIA live region設定
  - スクリーンリーダー用の通知
  - キーボード操作対応
- **Acceptance**:
  - [ ] ARIA属性設定確認
  - [ ] スクリーンリーダーテスト
  - [ ] キーボード操作テスト
- **Status**: ⏳ Pending

---

### T043: API Client 実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T041
- **Description**:
  - Fetch API wrapper
  - エラーハンドリング
  - TypeScript型定義
- **Acceptance**:
  - [ ] API Client実装完了
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T044: shadcn/ui セットアップ
- **Priority**: P0
- **Estimate**: 2h
- **Dependencies**: T041
- **Description**:
  - shadcn/ui 初期化
  - Button, Select, Modal, Input components
- **Acceptance**:
  - [ ] shadcn/ui セットアップ完了
  - [ ] 基本コンポーネント動作確認
- **Status**: ⏳ Pending

---

### T045: Tailwind CSS カスタマイズ
- **Priority**: P1
- **Estimate**: 2h
- **Dependencies**: T044
- **Description**:
  - カスタムカラー設定（土曜=薄青、日曜=薄ピンク、祝日=オレンジ）
  - レスポンシブブレークポイント
- **Acceptance**:
  - [ ] Tailwind設定完了
  - [ ] カスタムカラー動作確認
- **Status**: ⏳ Pending

---

### T046: ダッシュボードレイアウト実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T044
- **Description**:
  - /app/worklogs/page.tsx 作成
  - ヘッダー、メインコンテンツ、フッター
  - レスポンシブレイアウト
- **Acceptance**:
  - [ ] レイアウト実装完了
  - [ ] レスポンシブ動作確認
- **Status**: ⏳ Pending

---

### T047: 年度・月度セレクタ実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T042, T046
- **Description**:
  - 年度セレクタ（FiscalYearPattern連携）
  - 月度セレクタ（MonthlyPeriodPattern連携）
  - 選択時にStore更新
- **Acceptance**:
  - [ ] セレクタ実装完了
  - [ ] Store連携動作確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T048: カレンダーコンポーネント実装 (Part 1)
- **Priority**: P0
- **Estimate**: 6h
- **Dependencies**: T041, T042
- **Description**:
  - react-day-picker 統合
  - 月度期間に合わせた表示（21日締め対応）
  - 土日祝日の色分け
- **Acceptance**:
  - [ ] カレンダー表示完了
  - [ ] 月度期間正常表示
  - [ ] 色分け動作確認
- **Status**: ⏳ Pending

---

### T049: カレンダーコンポーネント実装 (Part 2)
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T048
- **Description**:
  - 日付クリックイベント
  - 各日の合計時間表示
  - ステータス表示（承認済み/未承認）
- **Acceptance**:
  - [ ] クリックイベント実装
  - [ ] 合計時間表示
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T050: カレンダー - モバイル最適化
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T049
- **Description**:
  - タッチ操作最適化
  - スワイプで月移動
  - 小画面対応
- **Acceptance**:
  - [ ] モバイル動作確認
  - [ ] タッチ操作テスト
- **Status**: ⏳ Pending

---

### T051: 案件サマリコンポーネント実装
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T042, T041
- **Description**:
  - TanStack Table 統合
  - 案件別合計時間・割合表示
  - ソート機能
- **Acceptance**:
  - [ ] テーブル表示完了
  - [ ] ソート動作確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T052: サマリ - 月度統計表示
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T051
- **Description**:
  - 合計稼働時間
  - 予定時間（営業日×8h）
  - 達成率
- **Acceptance**:
  - [ ] 統計表示完了
  - [ ] 計算ロジック実装
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T053: 祝日マスタAPI連携
- **Priority**: P1
- **Estimate**: 3h
- **Dependencies**: T042, T043
- **Description**:
  - GET /api/holidays 連携
  - Zustand Store に祝日データ保存
  - カレンダー表示反映
- **Acceptance**:
  - [ ] API連携完了
  - [ ] カレンダー反映確認
- **Status**: ⏳ Pending

---

### T054-T055: Frontend Foundation Tests
- **Priority**: P1
- **Estimate**: 4h
- **Description**:
  - カレンダー単体テスト
  - サマリ単体テスト
  - Store単体テスト
- **Acceptance**:
  - [ ] テスト 20件以上
- **Status**: ⏳ Pending

---

### T056-T065: [Reserved for Foundation refinements]
- **Status**: ⏳ Pending

---

## Phase 4: Frontend - Features (4-5 days)

### T066: 詳細入力モーダル - 基本構造
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T044, T049
- **Description**:
  - shadcn/ui Dialog 使用
  - 日付表示
  - 案件リスト表示
  - 休暇入力フォーム
- **Acceptance**:
  - [ ] モーダル実装完了
  - [ ] 開閉動作確認
- **Status**: ⏳ Pending

---

### T067: 詳細入力モーダル - 案件入力
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T066
- **Description**:
  - 案件セレクタ（検索機能付き）
  - 時間入力（0.25刻み）
  - 複数案件追加・削除
  - コメント入力
- **Acceptance**:
  - [ ] 案件入力実装完了
  - [ ] 動的行追加・削除動作確認
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T068: 詳細入力モーダル - バリデーション
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T067
- **Description**:
  - zod schema 定義
  - 合計時間 <= 24h チェック
  - 時間 0.25刻みチェック
  - エラーメッセージ表示
- **Acceptance**:
  - [ ] バリデーション実装完了
  - [ ] エラー表示確認
  - [ ] 単体テスト 8件以上
- **Status**: ⏳ Pending

---

### T069: 詳細入力モーダル - API連携
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T068, T043
- **Description**:
  - POST /api/worklogs 連携
  - PUT /api/worklogs/{id} 連携
  - DELETE /api/worklogs/{id} 連携
  - 楽観的ロック対応
- **Acceptance**:
  - [ ] API連携完了
  - [ ] Store更新確認
  - [ ] エラーハンドリング実装
- **Status**: ⏳ Pending

---

### T070: 詳細入力モーダル - 休暇入力
- **Priority**: P0
- **Estimate**: 3h
- **Dependencies**: T066
- **Description**:
  - 休暇タイプセレクタ
  - 休暇時間入力（0.25刻み）
  - POST /api/absences 連携
- **Acceptance**:
  - [ ] 休暇入力実装完了
  - [ ] API連携確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T071: CSV一括入力 - アップロード機能
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T041, T043
- **Description**:
  - ファイルアップロードUI
  - papaparse で CSV パース
  - POST /api/worklogs/bulk-import 連携
- **Acceptance**:
  - [ ] アップロード実装完了
  - [ ] パース動作確認
  - [ ] API連携確認
- **Status**: ⏳ Pending

---

### T072: CSV一括入力 - エラーハンドリング
- **Priority**: P1
- **Estimate**: 3h
- **Dependencies**: T071
- **Description**:
  - 行単位エラー表示
  - バリデーションエラー詳細表示
  - リトライ機能
- **Acceptance**:
  - [ ] エラーハンドリング実装完了
  - [ ] エラー表示確認
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T073: CSV一括出力機能
- **Priority**: P1
- **Estimate**: 3h
- **Dependencies**: T041, T043
- **Description**:
  - GET /api/worklogs/export 連携
  - CSVダウンロード
  - ファイル名: worklogs_YYYY_MM.csv
- **Acceptance**:
  - [ ] エクスポート実装完了
  - [ ] ダウンロード動作確認
- **Status**: ⏳ Pending

---

### T074: 前月案件コピー機能
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T043
- **Description**:
  - POST /api/worklogs/copy-from-previous-month 連携
  - 確認ダイアログ
  - Store更新
- **Acceptance**:
  - [ ] コピー機能実装完了
  - [ ] API連携確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T075: 承認申請機能
- **Priority**: P0
- **Estimate**: 4h
- **Dependencies**: T043
- **Description**:
  - POST /api/worklogs/submit 連携
  - 確認ダイアログ
  - ステータス更新
- **Acceptance**:
  - [ ] 承認申請実装完了
  - [ ] API連携確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T076: 承認・差し戻し機能 (承認者用)
- **Priority**: P0
- **Estimate**: 5h
- **Dependencies**: T043
- **Description**:
  - 承認対象一覧表示
  - POST /api/worklogs/approve 連携
  - 差し戻しコメント入力
- **Acceptance**:
  - [ ] 承認機能実装完了
  - [ ] API連携確認
  - [ ] 単体テスト 5件以上
- **Status**: ⏳ Pending

---

### T077: 代理入力機能 - メンバー選択
- **Priority**: P1
- **Estimate**: 4h
- **Dependencies**: T043
- **Description**:
  - GET /api/members/{id}/subordinates 連携
  - 配下メンバー一覧表示
  - メンバー選択UI
- **Acceptance**:
  - [ ] メンバー選択実装完了
  - [ ] API連携確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T078: 代理入力機能 - 入力モード切替
- **Priority**: P1
- **Estimate**: 3h
- **Dependencies**: T077, T069
- **Description**:
  - 代理入力モード切替
  - inputBy パラメータ追加
  - 代理入力表示（アイコン等）
- **Acceptance**:
  - [ ] 代理入力実装完了
  - [ ] モード切替動作確認
  - [ ] 単体テスト 3件以上
- **Status**: ⏳ Pending

---

### T079: ローディング・エラー表示
- **Priority**: P1
- **Estimate**: 3h
- **Dependencies**: T042
- **Description**:
  - ローディングスピナー
  - エラートースト表示
  - リトライボタン
- **Acceptance**:
  - [ ] ローディング表示実装
  - [ ] エラー表示実装
  - [ ] UX確認
- **Status**: ⏳ Pending

---

### T080-T090: [Reserved for Feature refinements]
- **Status**: ⏳ Pending

---

## Phase 5: Testing & QA (3-4 days)

### T091-T095: E2Eテスト - 基本シナリオ
- **Priority**: P0
- **Estimate**: 8h
- **Description**:
  - Playwright セットアップ
  - 稼働時間入力シナリオ
  - 承認フローシナリオ
  - CSV入出力シナリオ
- **Acceptance**:
  - [ ] E2Eテスト 10件以上
  - [ ] 全テストパス
- **Status**: ⏳ Pending

---

### T096-T100: パフォーマンステスト
- **Priority**: P1
- **Estimate**: 6h
- **Description**:
  - API応答時間測定
  - フロントエンド初期表示測定
  - CSV処理パフォーマンス測定
- **Acceptance**:
  - [ ] API < 100ms (p95)
  - [ ] 初期表示 < 1s
  - [ ] CSV < 1s/100件
- **Status**: ⏳ Pending

---

### T101-T105: アクセシビリティチェック
- **Priority**: P1
- **Estimate**: 4h
- **Description**:
  - ARIA属性チェック
  - キーボード操作確認
  - スクリーンリーダー確認
- **Acceptance**:
  - [ ] WCAG 2.1 AA準拠
- **Status**: ⏳ Pending

---

### T106-T110: ブラウザ互換性テスト
- **Priority**: P1
- **Estimate**: 4h
- **Description**:
  - Chrome/Safari/Edge 最新版
  - モバイルブラウザ（iOS Safari/Chrome）
  - レスポンシブデザイン確認
- **Acceptance**:
  - [ ] 全ブラウザ動作確認
- **Status**: ⏳ Pending

---

## Phase 6: Documentation & Deployment (3 days)

### T111-T115: ユーザーマニュアル作成
- **Priority**: P0
- **Estimate**: 6h
- **Description**:
  - 基本操作マニュアル
  - 承認者向けマニュアル
  - FAQ
  - スクリーンショット付き
- **Acceptance**:
  - [ ] マニュアル完成
  - [ ] レビュー完了
- **Status**: ⏳ Pending

---

### T116-T120: API仕様書完成 (OpenAPI)
- **Priority**: P0
- **Estimate**: 4h
- **Description**:
  - OpenAPI 3.0 形式
  - 全エンドポイント記載
  - Request/Response サンプル
- **Acceptance**:
  - [ ] API仕様書完成
  - [ ] Swagger UI 動作確認
- **Status**: ⏳ Pending

---

### T121-T125: Docker設定更新
- **Priority**: P0
- **Estimate**: 4h
- **Description**:
  - docker-compose.yml 更新
  - 環境変数設定
  - ヘルスチェック追加
- **Acceptance**:
  - [ ] Docker起動確認
  - [ ] 全サービス正常動作
- **Status**: ⏳ Pending

---

### T126-T130: リリースノート作成
- **Priority**: P0
- **Estimate**: 2h
- **Description**:
  - RELEASE_NOTES_v0.2.0.md 作成
  - 機能一覧
  - Breaking Changes
  - マイグレーション手順
- **Acceptance**:
  - [ ] リリースノート完成
  - [ ] レビュー完了
- **Status**: ⏳ Pending

---

## Summary

**Total Tasks**: 130  
**Total Estimate**: 22-27 business days  
**Priority Distribution**:
- P0 (必須): 80 tasks
- P1 (重要): 40 tasks
- P2 (任意): 10 tasks

**Critical Path**:
1. Phase 1 (Backend Domain Model) → Phase 2 (Backend API)
2. Phase 3 (Frontend Foundation) → Phase 4 (Frontend Features)
3. Phase 5 (Testing) → Phase 6 (Documentation)

**Risks**:
- CSV大容量処理のパフォーマンス
- モバイルUIの操作性
- 承認フローの複雑性

**Next Steps**:
1. Phase 1 開始: T001 Project エンティティ実装
2. 毎日の進捗確認・ステータス更新
3. 週次レビュー・計画調整

---

**作成日**: 2026-01-02  
**最終更新**: 2026-01-02  
**ステータス**: Planning → Ready to Start
