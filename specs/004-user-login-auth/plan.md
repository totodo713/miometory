# Implementation Plan: ユーザーログイン認証・認可システム

**Branch**: `004-user-login-auth` | **Date**: 2026-02-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-user-login-auth/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Miometryシステムにユーザー認証・認可機能を実装します。ユーザーはメールアドレスとパスワードでアカウントを作成・ログインし、ロールベースのアクセス制御により機能単位の細かい権限管理を行います。セッション管理、パスワードリセット、メール確認、監査ログなどのセキュリティ機能も含まれます。既存のMiometry Entry Systemと統合し、バックエンドはSpring Boot/Kotlin、フロントエンドはNext.js/TypeScriptで実装します。

## Technical Context

**Language/Version**: 
- Backend: Kotlin 2.3.0, Java 21
- Frontend: TypeScript 5.x, React 19.x

**Primary Dependencies**:
- Backend: Spring Boot 3.5.9, Spring Security, Spring Data JDBC, Flyway
- Frontend: Next.js 16.1.1, React 19.x

**Storage**: PostgreSQL (既存のMiometry Entry Systemデータベース)

**Testing**:
- Backend: JUnit 5
- Frontend: Vitest (既存プロジェクトで使用中)

**Target Platform**: 
- Backend: Linux server (Docker)
- Frontend: Web (modern browsers)

**Project Type**: Web application (frontend + backend)

**Performance Goals**: 
- ログイン処理: 1秒以内（SC-002）
- 同時ログインユーザー: 100人以上（SC-003）

**Constraints**: 
- セッションタイムアウト: 30分±1分（SC-008）
- 可用性: 99.5%以上（SC-010）
- 監査ログ保存期間: 90日間

**Scale/Scope**: 
- ユーザー数: 100人以上の同時接続をサポート
- 機能単位の細かい権限管理（user.create, user.edit, user.delete, report.view など）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Code Quality ✅
- **Status**: PASS (予定)
- **Plan**: 
  - Backend: Kotlin coding conventions適用、Gradle Kotlinterプラグインでlint
  - Frontend: Biome lint/formatで既存コード品質標準を維持
  - Pull requestでpeer review必須

### II. Testing Discipline & Standards ✅
- **Status**: PASS (予定)
- **Plan**:
  - Backend: JUnit 5でunit/integration tests作成
  - Frontend: Vitest unit tests作成（既存テスト構造に従う）
  - Test Pyramid: 多数のunit tests、適度なintegration tests
  - 各機能要件（FR-001～FR-020）に対応するテストを作成

### III. Consistent User Experience (UX) ✅
- **Status**: PASS (予定)
- **Plan**:
  - 既存のMiometry Entry SystemのUI/UXパターンに従う
  - WCAG 2.1 AA準拠（既存プロジェクトの標準）
  - エラーメッセージ、ローディング状態を明確に表示
  - 未確認アカウント用のバナー表示（FR-017）

### IV. Performance Requirements ✅
- **Status**: PASS (予定)
- **Plan**:
  - Success Criteria（SC-001～SC-010）を満たす実装
  - パフォーマンスベンチマークテスト追加
  - データベースインデックス最適化

**Overall**: ✅ All gates expected to PASS. Constitution-compliant design planned.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-login-auth/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── auth-api.yaml    # OpenAPI spec for authentication endpoints
│   └── user-api.yaml    # OpenAPI spec for user management endpoints
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── kotlin/com/worklog/
│   │   │   ├── api/            # REST controllers
│   │   │   │   ├── auth/       # Authentication endpoints
│   │   │   │   └── user/       # User management endpoints
│   │   │   ├── application/    # Application services (CQRS)
│   │   │   │   ├── auth/       # Auth-related commands/queries
│   │   │   │   └── user/       # User-related commands/queries
│   │   │   ├── domain/         # Domain entities and value objects
│   │   │   │   ├── user/       # User aggregate
│   │   │   │   ├── role/       # Role aggregate
│   │   │   │   ├── session/    # Session entity
│   │   │   │   └── audit/      # AuditLog entity
│   │   │   └── infrastructure/ # Infrastructure implementations
│   │   │       ├── config/     # Security, CORS, Session config
│   │   │       ├── persistence/# Repository implementations
│   │   │       └── email/      # Email service implementation
│   │   └── resources/
│   │       └── db/migration/   # Flyway migration scripts
│   │           └── V003__user_auth.sql
│   └── test/
│       ├── kotlin/com/worklog/
│       │   ├── api/            # Controller tests
│       │   ├── application/    # Application service tests
│       │   └── domain/         # Domain logic tests
│       └── resources/          # Test data

frontend/
├── app/
│   ├── (auth)/            # Authentication route group
│   │   ├── login/         # Login page
│   │   ├── signup/        # Signup page
│   │   └── password-reset/# Password reset flow
│   ├── components/
│   │   ├── auth/          # Auth-specific components
│   │   │   ├── LoginForm.tsx
│   │   │   ├── SignupForm.tsx
│   │   │   ├── PasswordResetForm.tsx
│   │   │   └── UnverifiedBanner.tsx
│   │   └── shared/        # Existing shared components
│   ├── hooks/
│   │   ├── useAuth.ts     # Authentication hook
│   │   └── usePermission.ts # Permission check hook
│   └── lib/
│       ├── api/
│       │   └── auth.ts    # Auth API client
│       └── types/
│           └── auth.ts    # Auth-related types
└── tests/
    └── auth/              # Auth-related tests
```

**Structure Decision**: Web application structure (Option 2) を選択。既存のMiometryプロジェクトはbackend/frontend分離構造を採用しており、認証機能もこの構造に従います。バックエンドはDDD+CQRS、フロントエンドはNext.js App Routerのベストプラクティスに従います。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

該当なし - すべてのConstitution Checkに準拠予定。

## Phase 0: Research & Technical Decisions

以下の技術選択肢について調査が必要:

1. **パスワードハッシュ化アルゴリズム** (FR-004)
   - 候補: bcrypt, Argon2, PBKDF2
   - 調査ポイント: Spring Securityのデフォルト、性能、セキュリティ強度

2. **セッション管理方式** (FR-013, FR-014)
   - 候補: Spring Session + Redis, Spring Security Session Management
   - 調査ポイント: 複数デバイス対応、30日間永続化、パフォーマンス

3. **メール送信サービス** (FR-011, FR-016)
   - 候補: Spring Mail + SMTP, 外部メールAPI (SendGrid, AWS SES)
   - 調査ポイント: 信頼性、エラーハンドリング、コスト

4. **権限管理パターン** (FR-008, FR-009, FR-019)
   - 候補: Spring Security Method Security, カスタムPermissionEvaluator
   - 調査ポイント: 機能単位の細かい権限制御、パフォーマンス

5. **フロントエンドセッション管理**
   - 候補: HTTP-only Cookie, NextAuth.js, カスタム実装
   - 調査ポイント: CSRF対策、セキュリティ、Next.js 16との互換性

6. **監査ログ削除戦略** (FR-020)
   - 候補: PostgreSQL TTL, スケジュールジョブ (Spring Scheduler), 手動アーカイブ
   - 調査ポイント: パフォーマンス影響、データ整合性

## Phase 1: Design Artifacts

Phase 1では以下を生成予定:

1. **data-model.md**: User, Role, Permission, Session, PasswordResetToken, AuditLog エンティティ定義
2. **contracts/auth-api.yaml**: 認証関連エンドポイント (POST /auth/signup, POST /auth/login, POST /auth/logout, POST /auth/password-reset/request, POST /auth/password-reset/confirm)
3. **contracts/user-api.yaml**: ユーザー管理エンドポイント (GET /users/me, PUT /users/me, GET /users/:id - 権限チェック付き)
4. **quickstart.md**: ローカル開発環境セットアップ、初期管理者作成手順

## Phase 2: Task Breakdown

Phase 2 (`/speckit.tasks` コマンド) では、Phase 0/1の成果物を基に詳細なタスク分解を実施予定。想定されるタスクカテゴリ:

- Database migration (V003__user_auth.sql)
- Backend domain entities & value objects
- Backend repository implementations
- Backend application services (commands/queries)
- Backend REST controllers
- Backend security configuration (Spring Security)
- Backend tests (unit/integration)
- Frontend auth components & pages
- Frontend API client
- Frontend hooks (useAuth, usePermission)
- Frontend tests
- Integration testing (E2E)
- Documentation updates

---

**Next Steps**: Phase 0研究を実施し、`research.md`を生成します。
