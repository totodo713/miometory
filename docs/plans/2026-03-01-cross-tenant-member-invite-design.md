# Cross-Tenant Member Invite Design

## Problem

PR#54 で実装された CSV メンバーインポート機能では、`AdminMemberService.inviteMember()` が毎回新しい `User` レコードを作成する。`users.email` にはグローバル UNIQUE 制約があるため、他テナントに既に登録済みのユーザーは `DUPLICATE_EMAIL` エラーで拒否される。

DB 設計（`members` テーブルの `(tenant_id, email)` UNIQUE 制約）は複数テナントへの所属を許容しているが、アプリケーションロジックが対応していない。

## Solution: Approach A — 既存ユーザー分岐の追加

### 概要

`inviteMember()` 内で既存ユーザーの存在を確認し、存在する場合は User 作成をスキップして Member レコードのみ作成する。

### 変更対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `AdminMemberService.java` | `inviteMember()` に既存ユーザー分岐を追加 |
| `MemberCsvValidationService.java` | `existingUserEmails` チェックをエラーから除外 |
| `AdminMemberCsvService.java` | 結果 CSV に `status` 列を追加 |
| `InviteMemberResult.java` (新規 or 修正) | `isExistingUser` フラグ追加 |
| テスト各種 | 既存ユーザーシナリオのテスト追加・修正 |

### 詳細設計

#### 1. `AdminMemberService.inviteMember()` の変更

```
修正後のフロー:
  findByEmail? → 存在する → User 作成スキップ → Member 作成 (isExistingUser=true)
              → 存在しない → User 作成 → Member 作成 (isExistingUser=false)
```

- `existsByEmail()` → `findByEmail()` に変更
- 既存ユーザーの場合: パスワード変更なし、アカウント状態変更なし
- `InviteMemberResult` に `isExistingUser` フラグと nullable `temporaryPassword`

#### 2. `MemberCsvValidationService.validate()` の変更

- `existingUserEmails` によるエラー追加を削除
- `existingMemberEmails`（同一テナント内重複）は維持

#### 3. 結果 CSV フォーマット変更

```csv
email,displayName,status,temporaryPassword
alice@example.com,Alice,created,abc123xyz789
bob@example.com,Bob,existing,
```

- `status`: `created`（新規ユーザー） / `existing`（既存ユーザー）
- `temporaryPassword`: 既存ユーザーは空

### セキュリティ考慮事項

- 既存ユーザーのパスワード・アカウント状態は一切変更しない
- テナント越境チェックは維持（セッションの tenantId 検証）
- 同一テナント内の Member 重複チェックは維持

### テスト方針

- `AdminMemberServiceTest`: 既存ユーザー分岐のテスト追加
- `MemberCsvValidationServiceTest`: 既存ユーザーメールがエラーにならないことを検証
- `AdminMemberCsvServiceTest`: 結果 CSV の status 列テスト追加
