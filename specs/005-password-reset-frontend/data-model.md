# Data Model: Password Reset Frontend

**Feature**: Password Reset Frontend  
**Date**: 2026-02-15  
**Status**: Phase 1 Design

このドキュメントは、パスワードリセット機能のフロントエンドで使用するデータモデルを定義します。すべてのエンティティはクライアント側（TypeScript/React）で使用されます。

---

## Entities

### 1. PasswordResetRequestForm

パスワードリセット要求ページ（`/password-reset/request`）で使用するフォームデータ

**Fields:**
```typescript
interface PasswordResetRequestForm {
  email: string;
  isLoading: boolean;
  isSubmitted: boolean;
  validationErrors: ValidationError[];
}
```

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `email` | `string` | ユーザーが入力したメールアドレス | 必須、RFC 5322形式 |
| `isLoading` | `boolean` | API リクエスト中かどうか | - |
| `isSubmitted` | `boolean` | フォーム送信済みかどうか | - |
| `validationErrors` | `ValidationError[]` | 現在のバリデーションエラー | - |

**State Transitions:**
```
idle (初期状態)
  → validating (ユーザー入力時)
    → valid/invalid (バリデーション結果)
      → submitting (送信ボタンクリック)
        → success (API成功) → フォームリセット
        → error (API失敗) → エラー表示、idle状態に戻る
```

**Validation Rules (from spec FR-002):**
- メールアドレスは必須
- メールアドレスは RFC 5322形式（regex: `/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/`）
- 空白のメールアドレスは許可しない

---

### 2. PasswordResetConfirmForm

パスワードリセット確認ページ（`/password-reset/confirm`）で使用するフォームデータ

**Fields:**
```typescript
interface PasswordResetConfirmForm {
  token: string; // URLから抽出、sessionStorageにバックアップ
  newPassword: string;
  confirmPassword: string;
  passwordStrength: PasswordStrengthResult | null;
  isLoading: boolean;
  isSubmitted: boolean;
  validationErrors: ValidationError[];
}
```

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `token` | `string` | URLクエリパラメータから抽出したリセットトークン | 必須（バックエンドで検証） |
| `newPassword` | `string` | ユーザーが入力した新しいパスワード | 必須、8文字以上、大文字・小文字・数字を含む |
| `confirmPassword` | `string` | パスワード確認フィールド | 必須、`newPassword`と一致 |
| `passwordStrength` | `PasswordStrengthResult \| null` | リアルタイムパスワード強度計算結果 | - |
| `isLoading` | `boolean` | API リクエスト中かどうか | - |
| `isSubmitted` | `boolean` | フォーム送信済みかどうか | - |
| `validationErrors` | `ValidationError[]` | 現在のバリデーションエラー | - |

**State Transitions:**
```
idle (初期状態、トークン抽出中)
  → token_loaded (トークン抽出成功)
    → validating (ユーザー入力時)
      → valid/invalid (バリデーション結果)
        → submitting (送信ボタンクリック)
          → success (API成功) → ログインページにリダイレクト
          → error (API失敗) → エラー表示、idle状態に戻る
  → token_error (トークン抽出失敗) → エラーページ表示
```

**Validation Rules (from spec FR-005, FR-006):**
- 新しいパスワードは必須
- 新しいパスワードは8文字以上
- 新しいパスワードは少なくとも1つの大文字を含む
- 新しいパスワードは少なくとも1つの小文字を含む
- 新しいパスワードは少なくとも1つの数字を含む
- パスワード確認は必須
- パスワード確認は新しいパスワードと一致

---

### 3. ValidationError

フォームバリデーションエラーを表現するエンティティ

**Fields:**
```typescript
interface ValidationError {
  field: string; // エラーが発生したフィールド名
  message: string; // ユーザーに表示するエラーメッセージ
  type: 'required' | 'format' | 'mismatch' | 'length';
}
```

| Field | Type | Description |
|-------|------|-------------|
| `field` | `string` | エラーが発生したフィールド名（例: "email", "newPassword"） |
| `message` | `string` | i18nローカライズされたエラーメッセージ |
| `type` | `'required' \| 'format' \| 'mismatch' \| 'length'` | エラーの種類 |

**Error Types:**
- `required`: 必須フィールドが空
- `format`: フォーマットが不正（メールアドレス形式エラーなど）
- `mismatch`: フィールド間の不一致（パスワード確認が一致しないなど）
- `length`: 長さ制約違反（8文字未満など）

**Example:**
```typescript
const errors: ValidationError[] = [
  {
    field: 'email',
    message: '有効なメールアドレスを入力してください',
    type: 'format'
  },
  {
    field: 'confirmPassword',
    message: 'パスワードが一致しません',
    type: 'mismatch'
  }
];
```

---

### 4. RateLimitState

クライアント側レート制限の状態を管理するエンティティ

**Fields:**
```typescript
interface RateLimitState {
  attempts: RateLimitAttempt[];
  allowed: boolean; // 現在リクエスト可能かどうか
  remainingAttempts: number; // 残り試行回数
  resetTime: number | undefined; // リセット時刻（UNIX timestamp ms）
}

interface RateLimitAttempt {
  timestamp: number; // UNIX timestamp in milliseconds
}
```

| Field | Type | Description |
|-------|------|-------------|
| `attempts` | `RateLimitAttempt[]` | 直近5分間の試行履歴（最大3件） |
| `allowed` | `boolean` | 現在リクエストが許可されているかどうか |
| `remainingAttempts` | `number` | 残り試行回数（0〜3） |
| `resetTime` | `number \| undefined` | 次にリクエスト可能になる時刻（ミリ秒単位UNIX timestamp） |

**Storage Location:** `localStorage` (key: `password_reset_rate_limit`)

**State Transitions:**
```
available (remainingAttempts > 0)
  → recordAttempt()
    → available (remainingAttempts > 0)
    → warning (remainingAttempts <= 2 && remainingAttempts > 0)
    → blocked (remainingAttempts === 0)
      → (5分経過後、最も古い試行が削除)
      → available (remainingAttempts > 0)
```

**Business Rules (from research.md):**
- 最大試行回数: 3回
- ウィンドウ期間: 5分（300,000ms）
- アルゴリズム: Sliding Window（直近5分間を常に監視）
- クロスタブ同期: localStorage + Storage Event API
- エッジケース対策: 時計巻き戻し検出、SSR環境チェック

---

### 5. PasswordStrengthResult

パスワード強度のリアルタイム計算結果を表現するエンティティ

**Fields:**
```typescript
interface PasswordStrengthResult {
  strength: PasswordStrength; // 3段階評価
  score: number; // zxcvbnスコア (0-4)
  feedback: string[]; // ユーザーへの改善提案
  crackTimeDisplay?: string; // 推定クラック時間（表示用）
}

type PasswordStrength = 'weak' | 'medium' | 'strong';
```

| Field | Type | Description |
|-------|------|-------------|
| `strength` | `'weak' \| 'medium' \| 'strong'` | 3段階のパスワード強度評価 |
| `score` | `number` | zxcvbnアルゴリズムによる0〜4のスコア |
| `feedback` | `string[]` | パスワード改善のための提案リスト |
| `crackTimeDisplay` | `string \| undefined` | 推定クラック時間（例: "数秒"、"数時間"） |

**Strength Mapping (from research.md):**
| zxcvbn Score | Strength | Color | Icon | Label |
|--------------|----------|-------|------|-------|
| 0-1 | `weak` | 赤 (red-500) | ⚠️ | 弱い |
| 2-3 | `medium` | 黄 (yellow-500) | 🔒 | 普通 |
| 4 | `strong` | 緑 (green-500) | ✓ | 強い |

**Calculation:** 
- アルゴリズム: zxcvbn-ts（エントロピー + パターン認識）
- デバウンス: 300ms
- パフォーマンス: < 10ms per calculation

**Accessibility:**
- 色だけに依存しない（アイコン + テキストラベル併用）
- `role="status"` + `aria-live="polite"` で動的更新を通知
- ビジュアルインジケーターは `aria-hidden="true"`（テキストで情報提供）

---

### 6. ApiResponse

バックエンドAPIからのレスポンスを表現するエンティティ

**Fields:**
```typescript
interface ApiResponse {
  success: boolean;
  message: string;
  errorCode?: string; // エラー時のみ
  statusCode?: number; // HTTPステータスコード
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | APIリクエストが成功したかどうか |
| `message` | `string` | サーバーからのメッセージ（成功メッセージまたはエラーメッセージ） |
| `errorCode` | `string \| undefined` | エラー時のエラーコード（例: "INVALID_TOKEN"） |
| `statusCode` | `number \| undefined` | HTTPステータスコード |

**Example Responses:**

**Success (Password Reset Request):**
```json
{
  "success": true,
  "message": "If the email exists, a password reset link has been sent.",
  "statusCode": 200
}
```

**Success (Password Reset Confirm):**
```json
{
  "success": true,
  "message": "Password reset successfully. You may now log in with your new password.",
  "statusCode": 200
}
```

**Error (Invalid Token):**
```json
{
  "success": false,
  "message": "Invalid or expired password reset token",
  "errorCode": "INVALID_TOKEN",
  "statusCode": 404
}
```

**Error (Validation Error):**
```json
{
  "success": false,
  "message": "Password must be at least 8 characters",
  "errorCode": "validation_error",
  "statusCode": 400
}
```

---

### 7. ErrorState

フロントエンドでのエラー状態を管理するエンティティ

**Fields:**
```typescript
interface ErrorState {
  type: ErrorType;
  message: string; // ユーザーに表示するエラーメッセージ
  isRetryable: boolean; // リトライボタンを表示するかどうか
  statusCode?: number; // HTTPステータスコード
}

type ErrorType = 'network' | 'validation' | 'expired_token' | 'rate_limit' | 'server' | null;
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | `ErrorType` | エラーの種類 |
| `message` | `string` | i18nローカライズされたエラーメッセージ |
| `isRetryable` | `boolean` | ユーザーがリトライ可能なエラーかどうか |
| `statusCode` | `number \| undefined` | HTTPステータスコード |

**Error Type Classification (from research.md):**
| ErrorType | HTTP Status | Retryable | Description |
|-----------|-------------|-----------|-------------|
| `network` | - | ✅ Yes | fetch失敗、接続タイムアウト |
| `server` | 500-599 | ✅ Yes | サーバー内部エラー |
| `rate_limit` | 429 | ✅ Yes (with delay) | レート制限超過 |
| `validation` | 400, 422 | ❌ No | パスワード形式エラー |
| `expired_token` | 401, 403, 404 | ❌ No | トークン期限切れ・無効 |

**User Actions by Error Type:**
| ErrorType | Action Button | Destination |
|-----------|--------------|-------------|
| `network` | 「再試行」 | 同じリクエストをリトライ |
| `server` | 「再試行」 | 同じリクエストをリトライ |
| `rate_limit` | 「再試行」 | 同じリクエストをリトライ（カウントダウン後） |
| `validation` | 「修正する」 | フォーム入力フィールドに戻る |
| `expired_token` | 「新しいリンクをリクエスト」 | `/password-reset` ページへ遷移 |

---

## Relationships

```
PasswordResetRequestForm
  ├─ validationErrors: ValidationError[]
  └─ (submits to) → ApiResponse

PasswordResetConfirmForm
  ├─ token: string (from URL → sessionStorage)
  ├─ passwordStrength: PasswordStrengthResult
  ├─ validationErrors: ValidationError[]
  └─ (submits to) → ApiResponse

RateLimitState
  ├─ attempts: RateLimitAttempt[]
  └─ (affects) → PasswordResetRequestForm submission

ErrorState
  └─ (created from) → ApiResponse failures
```

---

## Data Flow Diagrams

### Password Reset Request Flow

```
User Input (email)
  ↓
ValidationError[] (client-side validation)
  ↓ (if valid)
RateLimitState check
  ↓ (if allowed)
API Request: POST /api/v1/auth/password-reset/request
  ↓
ApiResponse
  ↓ (if success)
Success Message Display
  ↓ (if error)
ErrorState → Error Message + Retry Button (if retryable)
```

### Password Reset Confirm Flow

```
URL Query Parameter (token)
  ↓
Extract token → sessionStorage backup → Clean URL
  ↓
User Input (newPassword, confirmPassword)
  ↓
PasswordStrengthResult (real-time calculation)
  ↓
ValidationError[] (client-side validation)
  ↓ (if valid)
API Request: POST /api/v1/auth/password-reset/confirm
  ↓
ApiResponse
  ↓ (if success)
Redirect to /login
  ↓ (if error)
ErrorState → Error Message + Action Button
```

---

## Validation Summary

### Client-Side Validation Rules

| Field | Rules | Error Messages (Japanese) |
|-------|-------|--------------------------|
| `email` | Required, RFC 5322 format | "メールアドレスは必須です"<br>"有効なメールアドレスを入力してください" |
| `newPassword` | Required, Min 8 chars, 1 uppercase, 1 lowercase, 1 digit | "パスワードは必須です"<br>"パスワードは8文字以上必要です"<br>"パスワードは大文字、小文字、数字を含む必要があります" |
| `confirmPassword` | Required, Matches `newPassword` | "パスワード確認は必須です"<br>"パスワードが一致しません" |

### Backend Validation (for reference)

バックエンド（Spring Boot/Kotlin）は以下のバリデーションを実施（フロントエンドではサーバーレスポンスのみ処理）:
- メールアドレスの存在確認（ただし、結果は常に成功メッセージを返す - anti-enumeration）
- トークンの有効性確認（有効期限、使用済みチェック）
- パスワードの追加バリデーション（バックエンド側でも8文字以上チェック）

---

## Storage Strategy

| Data | Storage Location | Persistence | Scope | Reason |
|------|-----------------|-------------|-------|--------|
| Rate Limit State | localStorage | 5分間 | クロスタブ | クロスタブ共有、永続性 |
| Password Reset Token | sessionStorage | タブセッション中 | タブ単位 | セキュリティ（タブクローズで削除）、リフレッシュ対応 |
| Form State | React state | メモリのみ | コンポーネント内 | 一時的、ページリロードで削除 |
| Error State | React state | メモリのみ | コンポーネント内 | 一時的、ページリロードで削除 |

---

## Type Definitions (TypeScript)

```typescript
// types/password-reset.ts

export interface PasswordResetRequestForm {
  email: string;
  isLoading: boolean;
  isSubmitted: boolean;
  validationErrors: ValidationError[];
}

export interface PasswordResetConfirmForm {
  token: string;
  newPassword: string;
  confirmPassword: string;
  passwordStrength: PasswordStrengthResult | null;
  isLoading: boolean;
  isSubmitted: boolean;
  validationErrors: ValidationError[];
}

export interface ValidationError {
  field: string;
  message: string;
  type: 'required' | 'format' | 'mismatch' | 'length';
}

export interface RateLimitState {
  attempts: RateLimitAttempt[];
  allowed: boolean;
  remainingAttempts: number;
  resetTime: number | undefined;
}

export interface RateLimitAttempt {
  timestamp: number;
}

export type PasswordStrength = 'weak' | 'medium' | 'strong';

export interface PasswordStrengthResult {
  strength: PasswordStrength;
  score: number;
  feedback: string[];
  crackTimeDisplay?: string;
}

export interface ApiResponse {
  success: boolean;
  message: string;
  errorCode?: string;
  statusCode?: number;
}

export type ErrorType = 'network' | 'validation' | 'expired_token' | 'rate_limit' | 'server' | null;

export interface ErrorState {
  type: ErrorType;
  message: string;
  isRetryable: boolean;
  statusCode?: number;
}
```

---

## Next Steps

Phase 1の次のステップ:
1. API契約を`contracts/api-contracts.yaml`に定義
2. 開発者向けクイックスタートガイドを`quickstart.md`に作成
3. エージェントコンテキストを更新（`update-agent-context.sh`）

