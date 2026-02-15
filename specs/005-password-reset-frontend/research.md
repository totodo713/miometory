# Research: Password Reset Frontend

**Feature**: Password Reset Frontend  
**Date**: 2026-02-15  
**Status**: Complete

このドキュメントは、Phase 0で実施した技術調査の結果をまとめたものです。Phase 1の設計とPhase 2の実装において、ここで決定した技術選択を適用します。

---

## 1. Password Strength Calculation Algorithm

### Decision
**zxcvbn-ts**ライブラリを使用したハイブリッド方式（エントロピー + パターン認識）を採用

### Rationale
- **OWASP推奨**: 認証セキュリティチートシートで明示的に推奨されているアルゴリズム
- **高精度**: 単純なルールベースではなく、辞書攻撃、一般的なパターン、キーボード配列を考慮
- **高速**: < 10msの高速計算でパフォーマンス目標（< 100ms）を満たす
- **TypeScript完全対応**: zxcvbn-tsは完全なTypeScript実装

### Alternatives Considered
| アプローチ | 精度 | パフォーマンス | 実装難易度 | 選択理由 |
|----------|------|--------------|----------|---------|
| ルールベース | 低 | 高速 (< 1ms) | 簡単 | ❌ セキュリティリスク（単純なパターンを見逃す） |
| エントロピーのみ | 中 | 高速 (< 5ms) | 簡単 | ❌ 一般的なパスワードパターンを検出できない |
| **zxcvbn (ハイブリッド)** | 高 | 高速 (< 10ms) | 簡単 | ✅ バランスが最適、OWASP推奨 |

### Implementation Details

**Dependencies:**
```json
{
  "dependencies": {
    "@zxcvbn-ts/core": "^3.0.4",
    "@zxcvbn-ts/language-common": "^3.0.4",
    "@zxcvbn-ts/language-en": "^3.0.2"
  }
}
```

**Performance Profile:**
| 操作 | 目標 | 実測値 |
|------|------|-------|
| 初回ロード (辞書読込) | < 500ms | ~200ms (code splitting使用) |
| パスワード計算 (8文字) | < 100ms | ~5ms |
| パスワード計算 (20文字) | < 100ms | ~10ms |
| デバウンス遅延 | 推奨 | 300ms |

**Accessibility Compliance (WCAG 2.1 AA):**
- 色だけに依存しない表示（アイコン + テキストラベル併用）
- `role="status"` + `aria-live="polite"` で動的更新を通知
- `aria-describedby` でパスワードフィールドとインジケーターを関連付け
- コントラスト比 ≥ 4.5:1 確保

**Code Example:**
```typescript
// hooks/usePasswordStrength.ts
import { zxcvbnAsync, zxcvbnOptions } from '@zxcvbn-ts/core';
import { useState, useEffect, useCallback } from 'react';

export type PasswordStrength = 'weak' | 'medium' | 'strong';

export interface PasswordStrengthResult {
  strength: PasswordStrength;
  score: number; // 0-4 (zxcvbn score)
  feedback: string[];
}

export function usePasswordStrength(password: string) {
  const [result, setResult] = useState<PasswordStrengthResult | null>(null);
  const [isCalculating, setIsCalculating] = useState(false);

  const calculateStrength = useCallback(
    debounce(async (pwd: string) => {
      if (!pwd) {
        setResult(null);
        return;
      }

      setIsCalculating(true);
      const analysis = await zxcvbnAsync(pwd);
      
      // zxcvbnスコア (0-4) を3段階にマッピング
      let strength: PasswordStrength;
      if (analysis.score <= 1) strength = 'weak';
      else if (analysis.score <= 3) strength = 'medium';
      else strength = 'strong';

      setResult({
        strength,
        score: analysis.score,
        feedback: analysis.feedback.suggestions || [],
      });
      setIsCalculating(false);
    }, 300),
    []
  );

  useEffect(() => {
    calculateStrength(password);
    return () => calculateStrength.cancel();
  }, [password, calculateStrength]);

  return { result, isCalculating };
}
```

---

## 2. Client-Side Rate Limiting Implementation

### Decision
**localStorage + Sliding Window**方式を採用

### Rationale
- **クロスタブ同期**: localStorageは同一オリジンのすべてのタブ間で共有されるため、ユーザーが複数タブで操作しても制限が適用される
- **永続性**: ブラウザを閉じても5分間のウィンドウ期間中はデータが保持される
- **公平性**: Sliding Window方式により、固定ウィンドウの境界でのバースト攻撃を防ぐ
- **正確性**: 常に直近5分間の試行回数をカウント

### Alternatives Considered
| アプローチ | クロスタブ | 永続性 | 精度 | 選択理由 |
|----------|----------|--------|------|---------|
| **localStorage + Sliding Window** | ✅ | ✅ | 高 | ✅ 最もセキュアで公平 |
| sessionStorage + Sliding Window | ❌ | ❌ | 高 | ❌ タブごとに独立、制限回避可能 |
| localStorage + Fixed Window | ✅ | ✅ | 中 | ❌ 境界でのバースト攻撃が可能 |
| メモリのみ（state） | ❌ | ❌ | 高 | ❌ リフレッシュで制限リセット |

### Implementation Details

**Rate Limit Parameters:**
- 最大試行回数: 3回
- ウィンドウ期間: 5分（300,000ms）
- ストレージキー: `password_reset_rate_limit`

**Edge Case Handling:**
| エッジケース | 対策 |
|------------|------|
| ストレージがクリアされた | 新規ユーザーとして扱う（バックエンドでも制限） |
| 時計の手動巻き戻し | 最終試行時刻との比較で検出、試行履歴をクリア |
| 時計の進め操作 | 未来のタイムスタンプを削除（1分の余裕を許容） |
| タイムゾーン変更 | Date.now()（UTCミリ秒）使用により影響なし |
| SSR環境 | `typeof window === 'undefined'` チェックで回避 |

**User Feedback Patterns:**
```typescript
// 残り試行回数表示（警告）
if (remainingAttempts <= 2 && remainingAttempts > 0) {
  showWarning(`残り${remainingAttempts}回の試行が可能です`);
}

// レート制限超過（ブロック + カウントダウン）
if (!allowed && resetTime) {
  showError(`試行回数の上限に達しました。${formatResetTime(resetTime)}後に再試行できます。`);
}
```

**Code Example:**
```typescript
// hooks/usePasswordResetRateLimit.ts
const RATE_LIMIT_KEY = 'password_reset_rate_limit';
const MAX_ATTEMPTS = 3;
const WINDOW_MS = 5 * 60 * 1000;

interface RateLimitAttempt {
  timestamp: number;
}

export function usePasswordResetRateLimit() {
  const [rateLimitState, setRateLimitState] = useState(() => checkRateLimit());

  const recordAttempt = () => {
    const now = Date.now();
    const state = getRateLimitState();
    
    // 時計巻き戻し検出
    const lastAttemptTime = state.attempts.length > 0 
      ? Math.max(...state.attempts.map(a => a.timestamp))
      : 0;
      
    if (now < lastAttemptTime - 1000) {
      console.warn('Clock adjustment detected');
      state.attempts = [];
    }
    
    state.attempts.push({ timestamp: now });
    
    // ウィンドウ外の古い試行を削除
    state.attempts = state.attempts.filter(
      attempt => now - attempt.timestamp < WINDOW_MS
    );
    
    localStorage.setItem(RATE_LIMIT_KEY, JSON.stringify(state));
    setRateLimitState(checkRateLimit());
  };

  // Storage Eventでクロスタブ同期
  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === RATE_LIMIT_KEY || e.key === null) {
        setRateLimitState(checkRateLimit());
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  return { ...rateLimitState, recordAttempt };
}
```

**Important Note:**
⚠️ クライアント側のレート制限はUX向上が目的であり、セキュリティの主要な防御線ではありません。バックエンドでも同じ制限を必ず実装する必要があります（既にPR #3で実装済み）。

---

## 3. URL Token Extraction & Cleanup

### Decision
**useSearchParams + useRouter + sessionStorage**方式を採用

### Rationale
- **セキュリティ**: URLからトークンを即座に削除し、ブラウザ履歴に残さない（OWASP CWE-598対策）
- **UX**: sessionStorageにバックアップすることで、ページリフレッシュ時も動作継続
- **Next.js 16互換**: App Routerの推奨APIを使用
- **履歴操作対応**: `router.replace()`で履歴スタックを置換、戻るボタンでトークン付きURLに戻らない

### Alternatives Considered
| アプローチ | セキュリティ | UX | 履歴汚染 | 選択理由 |
|----------|------------|----|---------|---------</ |
| **useSearchParams + sessionStorage** | 高 | 良好 | なし | ✅ バランスが最適 |
| useSearchParams のみ | 中 | 悪い | なし | ❌ リフレッシュでトークン消失 |
| localStorage保存 | 低 | 良好 | なし | ❌ タブ間で共有、セキュリティリスク |
| URLに残す | 低 | 良好 | あり | ❌ 履歴・ログに漏洩 |

### Implementation Details

**Security Threats & Mitigations:**
| 脅威 | 対策 |
|------|------|
| Refererヘッダー漏洩 | `Referrer-Policy: no-referrer` ヘッダー設定 |
| Webログ記録 | トークン有効期限を短くする（5-15分、バックエンド実装済み） |
| ブラウザ履歴 | `router.replace()`で履歴を置換、追加しない |
| ブラウザキャッシュ | `Cache-Control: no-store` ヘッダー設定 |
| ショルダーサーフィン | useEffect内で即座にURLから削除 |

**Next.js Configuration:**
```javascript
// next.config.js
const nextConfig = {
  async headers() {
    return [
      {
        source: '/password-reset/confirm',
        headers: [
          {
            key: 'Referrer-Policy',
            value: 'no-referrer',
          },
          {
            key: 'Cache-Control',
            value: 'no-store, no-cache, must-revalidate, private',
          },
        ],
      },
    ];
  },
};
```

**Code Example:**
```typescript
'use client'

import { useEffect, useState } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'

const SESSION_KEY = 'password_reset_token'

export default function PasswordResetConfirmPage() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const [token, setToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isInitialized, setIsInitialized] = useState(false)

  useEffect(() => {
    if (isInitialized) return // Strict Mode対策

    const urlToken = searchParams.get('token')
    const storedToken = sessionStorage.getItem(SESSION_KEY)

    if (urlToken) {
      // URLからトークンを抽出
      setToken(urlToken)
      sessionStorage.setItem(SESSION_KEY, urlToken)
      
      // URLからトークンを即座に削除（履歴を残さない）
      router.replace('/password-reset/confirm', { scroll: false })
      setIsInitialized(true)
    } else if (storedToken) {
      // リフレッシュ時にsessionStorageから復元
      setToken(storedToken)
      setIsInitialized(true)
    } else {
      // トークンが見つからない
      setError('無効なリクエストです。パスワードリセットのリンクを再度クリックしてください。')
      setIsInitialized(true)
    }
  }, [searchParams, router, isInitialized])

  // クリーンアップ: パスワードリセット成功時またはアンマウント時
  useEffect(() => {
    return () => {
      sessionStorage.removeItem(SESSION_KEY)
    }
  }, [])

  if (!isInitialized) {
    return <div role="status" aria-live="polite">読み込み中...</div>
  }

  if (error) {
    return (
      <div role="alert" aria-live="assertive">
        <h1>エラー</h1>
        <p>{error}</p>
        <a href="/password-reset">パスワードリセットをやり直す</a>
      </div>
    )
  }

  if (!token) {
    return null
  }

  return (
    <PasswordResetForm 
      token={token} 
      onSuccess={() => {
        sessionStorage.removeItem(SESSION_KEY)
        router.push('/login')
      }} 
    />
  )
}
```

**Security Checklist:**
- ✅ トークンの有効期限: 5-15分（バックエンド実装済み）
- ✅ HTTPS必須
- ✅ URLからの即座削除（`router.replace()`）
- ✅ Referrer-Policy: `no-referrer`
- ✅ Cache-Control: `no-store`
- ✅ sessionStorage使用（リフレッシュ対応、タブクローズで自動削除）
- ✅ トークン使用後の無効化（バックエンド実装済み）

---

## 4. Internationalization (i18n) Framework Selection

### Decision
**next-intl**を採用

### Rationale
- **Next.js App Router完全対応**: Next.js 16のServer Components/Client Componentsの両方で動作
- **パフォーマンス優位性**: サーバーサイドでメッセージ処理可能、クライアントバンドルサイズ増加なし
- **TypeScript完全サポート**: 型安全な翻訳キー、IDE補完機能
- **将来の拡張性**: ネームスペース機能で大規模アプリに対応、翻訳管理システム統合可能
- **軽量**: バンドルサイズ ~14KB（react-i18nextの~40KBと比較）

### Alternatives Considered
| ライブラリ | App Router対応 | パフォーマンス | バンドルサイズ | 選択理由 |
|-----------|---------------|--------------|--------------|---------|
| **next-intl** | ✅ 完全対応 | ⭐⭐⭐ | ~14KB | ✅ Next.js 16に最適化 |
| react-i18next | ⚠️ 部分的 | ⭐⭐ | ~40KB | ❌ App Router最適化なし |
| next-i18next | ❌ Pages Router専用 | ⭐⭐ | ~35KB | ❌ App Router非対応 |

### Implementation Details

**Dependencies:**
```json
{
  "dependencies": {
    "next-intl": "^3.0.0"
  }
}
```

**Directory Structure:**
```
frontend/
├── messages/
│   ├── ja.json          # 日本語メッセージ
│   └── en.json          # 英語メッセージ（将来用）
├── src/
│   ├── i18n/
│   │   └── request.ts   # i18n設定
│   └── app/
│       ├── layout.tsx
│       └── page.tsx
```

**Namespace Organization for Password Reset:**
```json
// messages/ja.json
{
  "auth": {
    "passwordReset": {
      "title": "パスワードリセット",
      "emailLabel": "メールアドレス",
      "emailPlaceholder": "example@example.com",
      "submitButton": "リセットリンクを送信",
      "successMessage": "リセットリンクを送信しました",
      "errorMessage": "エラーが発生しました",
      "backToLogin": "ログインに戻る"
    },
    "passwordResetConfirm": {
      "title": "新しいパスワードを設定",
      "newPasswordLabel": "新しいパスワード",
      "confirmPasswordLabel": "パスワード確認",
      "submitButton": "パスワードを更新",
      "successMessage": "パスワードを更新しました",
      "errorPasswordMismatch": "パスワードが一致しません",
      "errorInvalidToken": "無効なリンクです"
    },
    "validation": {
      "required": "{field}は必須です",
      "emailInvalid": "有効なメールアドレスを入力してください",
      "passwordMinLength": "パスワードは{min}文字以上必要です"
    }
  }
}
```

**Code Example (Client Component):**
```typescript
'use client';

import { useTranslations } from 'next-intl';

export default function PasswordResetForm() {
  const t = useTranslations('auth.passwordReset');
  const tValidation = useTranslations('auth.validation');

  return (
    <div>
      <h1>{t('title')}</h1>
      <label htmlFor="email">{t('emailLabel')}</label>
      <input
        id="email"
        type="email"
        placeholder={t('emailPlaceholder')}
      />
      <button type="submit">{t('submitButton')}</button>
      <a href="/login">{t('backToLogin')}</a>
    </div>
  );
}
```

**Namespace Design Principles:**
1. 機能単位で分割（`passwordReset`, `passwordResetConfirm`）
2. 共通メッセージの再利用（`validation`ネームスペース）
3. 階層は浅く（3階層以内を推奨）
4. 命名規則: camelCaseで統一

---

## 5. API Error Handling & Retry Patterns

### Decision
**ローカルステート（useState） + カスタムフック + エラー分類**方式を採用

### Rationale
- **シンプルで軽量**: パスワードリセットのような単一ページ内の操作にはReact QueryやZustandは過剰
- **エラー分類の明確性**: HTTPステータスコードベースでリトライ可能性を判定
- **ユーザーフレンドリー**: エラータイプごとに適切なメッセージとアクションを提示
- **テスト容易性**: カスタムフック化により単体テストが簡単

### Alternatives Considered
| アプローチ | 複雑度 | テスト容易性 | 適用範囲 | 選択理由 |
|----------|-------|------------|---------|---------|
| **useState + カスタムフック** | 低 | 高 | 単一ページ | ✅ シンプルで十分 |
| React Query | 高 | 中 | 複数ページ | ❌ 過剰、キャッシュ不要 |
| Zustand | 中 | 中 | グローバル | ❌ 状態共有の必要なし |

### Implementation Details

**Error Classification:**
```typescript
interface ErrorState {
  type: 'network' | 'validation' | 'expired_token' | 'rate_limit' | 'server' | null;
  message: string;
  isRetryable: boolean;
  statusCode?: number;
}
```

**Error Type Mapping:**
| エラータイプ | HTTPステータス | リトライ可能 | ユースケース |
|------------|--------------|------------|------------|
| `network` | - | ✅ はい | fetch失敗、接続タイムアウト |
| `server` | 500-599 | ✅ はい | サーバー内部エラー |
| `rate_limit` | 429 | ✅ はい (待機後) | レート制限超過 |
| `validation` | 400, 422 | ❌ いいえ | パスワード形式エラー |
| `expired_token` | 401, 403, 404 | ❌ いいえ | トークン期限切れ |

**User-Friendly Error Messages:**
```typescript
const ERROR_MESSAGES = {
  network: {
    title: 'ネットワークエラー',
    message: 'インターネット接続を確認して、もう一度お試しください',
    action: '再試行'
  },
  server: {
    title: 'サーバーエラー',
    message: '一時的なエラーが発生しました。しばらく待ってから再試行してください',
    action: '再試行'
  },
  rate_limit: {
    title: 'リクエスト制限',
    message: 'リクエストが多すぎます。1分後に再試行してください',
    action: '再試行'
  },
  validation: {
    title: 'パスワードエラー',
    message: 'パスワードは8文字以上で、英大文字、英小文字、数字を含む必要があります',
    action: '修正する'
  },
  expired_token: {
    title: 'リンク期限切れ',
    message: 'このリセットリンクは期限切れです。パスワードリセットを最初からやり直してください',
    action: '新しいリンクをリクエスト'
  }
} as const;
```

**Code Example:**
```typescript
function classifyError(error: unknown): ErrorState {
  // ネットワークエラー (リトライ可能)
  if (error instanceof TypeError && error.message.includes('fetch')) {
    return {
      type: 'network',
      message: ERROR_MESSAGES.network.message,
      isRetryable: true
    };
  }

  // HTTPエラーレスポンス
  if (error instanceof Response) {
    const status = error.status;
    
    if (status >= 500) {
      return {
        type: 'server',
        message: ERROR_MESSAGES.server.message,
        isRetryable: true,
        statusCode: status
      };
    }
    
    if (status === 429) {
      return {
        type: 'rate_limit',
        message: ERROR_MESSAGES.rate_limit.message,
        isRetryable: true,
        statusCode: 429
      };
    }
    
    if (status === 401 || status === 403 || status === 404) {
      return {
        type: 'expired_token',
        message: ERROR_MESSAGES.expired_token.message,
        isRetryable: false,
        statusCode: status
      };
    }
    
    if (status === 400 || status === 422) {
      return {
        type: 'validation',
        message: ERROR_MESSAGES.validation.message,
        isRetryable: false,
        statusCode: status
      };
    }
  }
  
  return {
    type: 'server',
    message: '予期しないエラーが発生しました',
    isRetryable: false
  };
}
```

**Retry Button UX Pattern:**
```tsx
{error && (
  <div role="alert" className="error-banner">
    <h3>{ERROR_MESSAGES[error.type].title}</h3>
    <p>{ERROR_MESSAGES[error.type].message}</p>
    
    {/* リトライ可能なエラーの場合のみボタン表示 */}
    {error.isRetryable && (
      <button 
        onClick={handleSubmit}
        disabled={isLoading}
        className="retry-button"
      >
        {isLoading ? '処理中...' : ERROR_MESSAGES[error.type].action}
      </button>
    )}
    
    {/* リトライ不可の場合は別のアクション */}
    {!error.isRetryable && error.type === 'expired_token' && (
      <a href="/password-reset" className="primary-button">
        新しいリンクをリクエスト
      </a>
    )}
  </div>
)}
```

**UX Best Practices:**
- エラーメッセージの直下にリトライボタンを配置
- `role="alert"` + `aria-live="assertive"` でスクリーンリーダーに即座に通知
- ローディング状態でボタンテキストを「処理中...」に変更
- リトライ時も入力したパスワードは保持

---

## 6. Accessibility Testing Strategy

### Decision
**@axe-core/playwright + 手動キーボードテスト + ARIA属性チェック**の組み合わせを採用

### Rationale
- **自動化**: @axe-core/playwrightで80%のWCAG違反を自動検出
- **包括性**: 手動テストでキーボードナビゲーション、スクリーンリーダー対応を確認
- **効率性**: CIパイプラインに組み込み、リグレッションを防止
- **実績**: @axe-core/playwrightはプロジェクトに既に導入済み（`package.json`に記載）

### Alternatives Considered
| アプローチ | 自動化率 | カバレッジ | 学習コスト | 選択理由 |
|----------|--------|----------|----------|---------|
| **@axe-core/playwright + 手動** | 80% | 高 | 低 | ✅ バランスが最適 |
| Lighthouse CI | 60% | 中 | 低 | ❌ WCAG特化型ではない |
| 手動テストのみ | 0% | 高 | 高 | ❌ スケーラビリティがない |
| Pa11y | 70% | 中 | 中 | ❌ Playwrightと統合困難 |

### Implementation Details

**@axe-core/playwright Test Configuration:**
```typescript
// tests/accessibility/password-reset.spec.ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('パスワードリセットフォーム - アクセシビリティ', () => {
  test('リクエストページに自動検出可能なWCAG 2.1 AA違反がないこと', async ({ page }) => {
    await page.goto('/password-reset/request');
    
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('パスワード強度インジケーターのアクセシビリティ', async ({ page }) => {
    await page.goto('/password-reset/confirm?token=test-token');
    
    // パスワード入力
    await page.locator('input[type="password"][name="password"]').fill('weak');
    
    // 動的要素を含めてスキャン
    await page.locator('[role="status"]').waitFor();
    
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .include('[role="status"]')
      .analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });
});
```

**Keyboard Navigation Testing Checklist:**

**パスワードリセット リクエストページ:**
- [ ] Tabキーでメールアドレス入力フィールドにフォーカス移動
- [ ] Tabキーで「リセットリンクを送信」ボタンにフォーカス移動
- [ ] Tabキーで「ログインに戻る」リンクにフォーカス移動
- [ ] フォーカス順序が視覚的な順序と一致している
- [ ] フォーカスインジケーターが明確に表示される（コントラスト比 ≥ 3:1）
- [ ] EnterキーまたはSpaceキーでボタンを操作可能
- [ ] キーボードトラップがない

**パスワードリセット 確認ページ:**
- [ ] Tabキーで新しいパスワード入力フィールドにフォーカス移動
- [ ] Tabキーでパスワード確認入力フィールドにフォーカス移動
- [ ] Tabキーで「パスワードをリセット」ボタンにフォーカス移動
- [ ] パスワード強度インジケーターにフォーカスは不要（ライブリージョンで自動通知）
- [ ] エラー発生時、フォーカスがエラーメッセージまたは該当フィールドに移動

**ARIA Attribute Recommendations:**

**フォームラベルと説明:**
```html
<label for="email">メールアドレス</label>
<input 
  id="email" 
  type="email" 
  name="email"
  aria-describedby="email-hint"
  aria-required="true"
  aria-invalid="false"
/>
<div id="email-hint">登録時に使用したメールアドレスを入力してください</div>
```

**エラーメッセージ（role="alert"）:**
```html
<div 
  role="alert" 
  aria-live="assertive"
  aria-atomic="true"
  class="error-message"
>
  <p id="error-email">有効なメールアドレスを入力してください</p>
</div>

<input 
  id="email" 
  type="email"
  aria-invalid="true"
  aria-describedby="email-hint error-email"
/>
```

**パスワード強度インジケーター（role="status"）:**
```html
<div 
  role="status" 
  aria-live="polite"
  aria-atomic="true"
  class="password-strength"
>
  <p id="password-strength-text">パスワード強度: 弱い</p>
  <div class="strength-meter" aria-hidden="true">
    <!-- ビジュアルインジケーター -->
  </div>
</div>

<input 
  id="new-password" 
  type="password"
  aria-describedby="password-requirements password-strength-text"
/>
```

**Screen Reader Testing:**

**NVDA (Windows):**
- `H`キーで見出しをナビゲート → 論理的な順序を確認
- `F`キーでフォームフィールドに移動 → ラベル、説明、必須状態を確認
- エラー発生時 → `role="alert"`で即座に読み上げられるか確認
- パスワード入力時 → `role="status"`で強度変更が通知されるか確認

**VoiceOver (macOS):**
- `VO + U`でローターを開く → フォームコントロール、見出しをナビゲート
- フォームフィールドに移動 → ラベル、ヒント、エラーが読み上げられるか確認
- ライブリージョン → 動的更新が通知されるか確認

**WCAG 2.1 AA Compliance Checklist:**
| テスト項目 | WCAG基準 | 確認内容 |
|----------|---------|---------|
| フォームラベル | 3.3.2 (AA) | すべての入力フィールドに適切なラベルがあるか |
| エラー識別 | 3.3.1 (A) | エラーが発生したフィールドとエラー内容がテキストで説明されているか |
| エラー提案 | 3.3.3 (AA) | エラーの修正方法が提案されているか |
| ライブリージョン | 4.1.3 (AA) | 動的コンテンツの変更が適切に通知されるか |
| キーボード操作 | 2.1.1 (A) | すべての機能がキーボードのみで操作可能か |
| フォーカスインジケーター | 2.4.7 (AA) | フォーカスが視覚的に確認できるか（コントラスト比 ≥ 3:1） |
| コントラスト | 1.4.3 (AA) | テキストとエラーメッセージのコントラスト比が4.5:1以上か |
| 色だけに依存しない | 1.4.1 (A) | エラーが色だけで識別されていないか（アイコン+テキストも使用） |

---

## Summary: Technology Stack Decisions

| 技術領域 | 選択 | 主要な理由 |
|---------|------|----------|
| パスワード強度 | zxcvbn-ts | OWASP推奨、高精度、高速（< 10ms） |
| クライアント側レート制限 | localStorage + Sliding Window | クロスタブ同期、永続性、公平性 |
| URLトークン抽出 | useSearchParams + sessionStorage | セキュリティ（履歴汚染なし）、UX（リフレッシュ対応） |
| i18n | next-intl | Next.js 16最適化、軽量（~14KB）、TypeScript完全サポート |
| エラーハンドリング | useState + カスタムフック | シンプル、エラー分類明確、テスト容易 |
| アクセシビリティテスト | @axe-core/playwright + 手動 | 自動化80%、包括的、CI統合可能 |

**Next Steps:**
Phase 1でこれらの技術選択を反映した設計（data-model.md、contracts/、quickstart.md）を作成します。

