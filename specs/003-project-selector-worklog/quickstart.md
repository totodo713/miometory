# Quickstart: 工数入力画面のプロジェクト選択機能

**Feature**: 003-project-selector-worklog  
**Date**: 2025-01-20

## Overview

この機能は、工数入力画面（DailyEntryForm）において、プロジェクトIDの手動入力を廃止し、ユーザーにアサインされたプロジェクト一覧からドロップダウンで選択できるようにします。

## Prerequisites

- 開発環境がセットアップ済み（README.md参照）
- Docker Desktop起動済み（PostgreSQL, Redis）
- backend/frontend両方のビルドが成功すること

## Quick Implementation Steps

### 1. Database Migration

```bash
# 新規マイグレーションファイルを作成
# backend/src/main/resources/db/migration/V10__member_project_assignments.sql
```

### 2. Backend Implementation

#### 2.1 Domain Layer
- `MemberProjectAssignment.java` - アサインメントエンティティ
- `MemberProjectAssignmentId.java` - ID値オブジェクト

#### 2.2 Repository Layer
- `JdbcMemberProjectAssignmentRepository.java` - データアクセス

#### 2.3 API Layer
- `MemberController.java` に `GET /api/v1/members/{memberId}/projects` を追加
- `AssignedProjectsResponse.java` - レスポンスDTO

### 3. Frontend Implementation

#### 3.1 API Client
```typescript
// frontend/app/services/api.ts
members: {
  // 既存...
  getAssignedProjects: (memberId: string, options?: { activeOnly?: boolean; date?: string }) => {
    const query = new URLSearchParams({
      activeOnly: (options?.activeOnly ?? true).toString(),
      ...(options?.date && { date: options.date })
    });
    return apiClient.get<AssignedProjectsResponse>(
      `/api/v1/members/${memberId}/projects?${query}`
    );
  }
}
```

#### 3.2 ProjectSelector Component
```tsx
// frontend/app/components/worklog/ProjectSelector.tsx
interface ProjectSelectorProps {
  memberId: string;
  value: string;
  onChange: (projectId: string) => void;
  disabled?: boolean;
  date?: string;
}
```

#### 3.3 DailyEntryForm Integration
- 既存の `<input type="text">` を `<ProjectSelector>` に置き換え
- 既存エントリは `disabled={true}` で表示

## Testing Checklist

### Backend Tests
- [ ] `MemberProjectAssignmentTest` - ドメインロジック
- [ ] `JdbcMemberProjectAssignmentRepositoryTest` - リポジトリ
- [ ] `MemberControllerTest` - API統合テスト

### Frontend Tests  
- [ ] `ProjectSelector.test.tsx` - コンポーネント単体
- [ ] `DailyEntryForm.test.tsx` - 統合テスト更新
- [ ] `project-selector.spec.ts` - E2Eテスト

## API Usage Examples

### Get Assigned Projects
```bash
curl -X GET "http://localhost:8080/api/v1/members/{memberId}/projects?activeOnly=true" \
  -H "Cookie: JSESSIONID=..." \
  -H "Content-Type: application/json"
```

**Response:**
```json
{
  "projects": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "code": "PROJ-001",
      "name": "顧客ポータル開発",
      "isActive": true,
      "validFrom": "2025-01-01",
      "validUntil": null
    }
  ],
  "count": 1
}
```

## Key Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `V10__member_project_assignments.sql` | CREATE | DBマイグレーション |
| `MemberProjectAssignment.java` | CREATE | ドメインエンティティ |
| `JdbcMemberProjectAssignmentRepository.java` | CREATE | リポジトリ実装 |
| `MemberController.java` | UPDATE | API追加 |
| `api.ts` | UPDATE | フロントエンドAPIクライアント |
| `ProjectSelector.tsx` | CREATE | 選択コンポーネント |
| `DailyEntryForm.tsx` | UPDATE | コンポーネント統合 |
| `worklog.ts` | UPDATE | 型定義追加 |

## Environment Variables

既存の環境変数で動作。追加の設定不要。

## Rollback Plan

1. フロントエンドの`ProjectSelector`を元の`<input>`に戻す
2. マイグレーション`V10`はテーブル追加のみなので、削除不要（互換性維持）
