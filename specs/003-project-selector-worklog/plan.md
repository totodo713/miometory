# Implementation Plan: 工数入力画面のプロジェクト選択機能

**Branch**: `003-project-selector-worklog` | **Date**: 2025-01-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-project-selector-worklog/spec.md`

## Summary

工数入力画面において、プロジェクトIDの手動入力をプロジェクト選択ドロップダウンに置き換える。ユーザーにアサインされているプロジェクト一覧を取得し、プロジェクト名とコードで表示して選択可能にする。これにより入力ミスを防止し、ユーザー体験を向上させる。

## Technical Context

**Language/Version**: Kotlin 2.3.0 (Backend), TypeScript 5.x (Frontend)  
**Primary Dependencies**: Spring Boot 3.5.9, Next.js (React), Spring Data JDBC  
**Storage**: PostgreSQL (Flyway migrations)  
**Testing**: JUnit 5 + MockK (Backend), Vitest + Playwright (Frontend)  
**Target Platform**: Web application (Linux server + Browser)  
**Project Type**: Web application (backend + frontend)  
**Performance Goals**: プロジェクト一覧は2秒以内に表示 (SC-004)  
**Constraints**: 既存の工数エントリのプロジェクト変更は不可  
**Scale/Scope**: 1ユーザーあたり20-50プロジェクト程度を想定

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | ✅ Pass | 既存のコードスタイル（ktlint, biome）に従う。ドキュメント・テスト必須 |
| II. Testing Discipline | ✅ Pass | 新規APIエンドポイント・コンポーネントにユニット・統合テストを追加 |
| III. Consistent UX | ✅ Pass | 既存のフォームコンポーネントパターン（DailyEntryForm）に準拠 |
| IV. Performance Requirements | ✅ Pass | 2秒以内のレスポンス目標を設定、キャッシュ戦略を検討 |

## Project Structure

### Documentation (this feature)

```text
specs/003-project-selector-worklog/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/worklog/
│   ├── domain/
│   │   └── project/
│   │       └── ProjectAssignment.java      # NEW: メンバー・プロジェクト関連エンティティ
│   ├── api/
│   │   └── ProjectController.java          # UPDATE: アサインされたプロジェクト取得API追加
│   ├── application/service/
│   │   └── ProjectService.java             # NEW: プロジェクトサービス
│   └── infrastructure/repository/
│       └── JdbcProjectRepository.java      # NEW: プロジェクトリポジトリ
├── src/main/resources/db/migration/
│   └── V10__member_project_assignments.sql # NEW: アサインメントテーブル
└── src/test/kotlin/com/worklog/
    └── api/
        └── ProjectControllerTest.kt        # UPDATE: 新規エンドポイントのテスト

frontend/
├── app/
│   ├── components/worklog/
│   │   ├── DailyEntryForm.tsx             # UPDATE: プロジェクト選択ドロップダウン
│   │   └── ProjectSelector.tsx            # NEW: プロジェクト選択コンポーネント
│   ├── services/
│   │   └── api.ts                         # UPDATE: プロジェクト一覧取得API追加
│   └── types/
│       └── worklog.ts                     # UPDATE: Project型の確認
└── tests/
    ├── unit/components/
    │   └── ProjectSelector.test.tsx       # NEW: コンポーネントテスト
    └── e2e/
        └── project-selector.spec.ts       # NEW: E2Eテスト
```

**Structure Decision**: 既存のWeb application構造（backend/ + frontend/）を維持。新規ドメインエンティティ（ProjectAssignment）とコンポーネント（ProjectSelector）を追加。

## Constitution Check (Post-Design)

*Re-evaluation after Phase 1 design completion.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | ✅ Pass | 新規ファイルはktlint/biomeに準拠。全APIにJavaDoc/JSDoc追加予定。PRレビュー必須 |
| II. Testing Discipline | ✅ Pass | data-model.mdの検証ルールをテストケースにマッピング。単体/統合/E2Eテスト計画済み |
| III. Consistent UX | ✅ Pass | 既存DailyEntryFormのデザインパターンを踏襲。エラー/空状態のメッセージ定義済み |
| IV. Performance Requirements | ✅ Pass | 2秒目標に対しRedisキャッシュ + クライアントキャッシュで対応。research.mdに記載 |

## Complexity Tracking

> **No violations identified** - 設計は既存パターンに従い、新規の複雑な依存関係を追加しない。

## Generated Artifacts

| Artifact | Path | Description |
|----------|------|-------------|
| Research | [research.md](./research.md) | 技術決定と代替案の検討 |
| Data Model | [data-model.md](./data-model.md) | エンティティ定義とマイグレーション |
| API Contract | [contracts/member-projects-api.yaml](./contracts/member-projects-api.yaml) | OpenAPI仕様 |
| Quickstart | [quickstart.md](./quickstart.md) | 実装ガイド |

## Next Steps

Phase 2 (タスク分解) は `/speckit.tasks` コマンドで実行してください。
