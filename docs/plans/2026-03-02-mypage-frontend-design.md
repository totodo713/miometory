# MyPage Frontend Implementation Design

## Overview

Issues #81-#85: Header の UserMenu ドロップダウン作成、マイページ画面実装、AuthProvider 拡張、i18n キー追加、テスト実装。

## Prerequisites

- Backend Profile API (#80): **実装済み** (`GET/PUT /api/v1/profile`)

## Architecture

### New Files
- `frontend/app/components/shared/UserMenu.tsx` — ドロップダウンメニュー (NotificationBell パターン踏襲)
- `frontend/app/mypage/layout.tsx` — AuthGuard ラッパー
- `frontend/app/mypage/page.tsx` — マイページ画面

### Modified Files
- `frontend/app/components/shared/Header.tsx` — UserMenu 組み込み、モバイルドロワーにマイページリンク追加
- `frontend/app/providers/AuthProvider.tsx` — `updateUser` メソッド追加
- `frontend/app/services/api.ts` — `profile` セクション追加
- `frontend/messages/en.json` / `ja.json` — mypage + header キー追加

## Implementation Order

1. **Phase 1** (並行): #84 i18n + #83 AuthProvider/API
2. **Phase 2** (並行): #81 UserMenu + #82 MyPage
3. **Phase 3**: #85 Tests

## Key Patterns
- Dropdown: `useRef` + `mousedown` outside-click (NotificationBell.tsx)
- Layout: `AuthGuard` wrapper (worklog/layout.tsx)
- API: `apiClient.get/put` pattern (api.ts)
- Profile edit: Modal with validation, toast on success
- Email change: Prompt re-login → logout
