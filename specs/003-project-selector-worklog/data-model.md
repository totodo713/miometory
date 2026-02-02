# Data Model: 工数入力画面のプロジェクト選択機能

**Feature**: 003-project-selector-worklog  
**Date**: 2025-01-20

## Entities

### MemberProjectAssignment（新規）

メンバーとプロジェクトの関連を管理するエンティティ。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | 一意識別子 |
| tenant_id | UUID | FK→tenant, NOT NULL | テナントID |
| member_id | UUID | FK→members, NOT NULL | メンバーID |
| project_id | UUID | FK→projects, NOT NULL | プロジェクトID |
| assigned_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | アサイン日時 |
| assigned_by | UUID | FK→members, NULL | アサインした人（管理者） |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | アサインメントがアクティブか |

**Unique Constraints**:
- `UNIQUE(tenant_id, member_id, project_id)` - 同一テナント内でメンバー・プロジェクトの組み合わせは一意

**Indexes**:
- `idx_member_project_assignments_member` ON (member_id)
- `idx_member_project_assignments_project` ON (project_id)
- `idx_member_project_assignments_tenant` ON (tenant_id)

---

### Project（既存 - 参照のみ）

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | プロジェクトID |
| tenant_id | UUID | FK→tenant | テナントID |
| code | VARCHAR(50) | NOT NULL | プロジェクトコード |
| name | VARCHAR(200) | NOT NULL | プロジェクト名 |
| is_active | BOOLEAN | NOT NULL | アクティブ状態 |
| valid_from | DATE | NULL | 有効開始日 |
| valid_until | DATE | NULL | 有効終了日 |

---

### Member（既存 - 参照のみ）

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | メンバーID |
| tenant_id | UUID | FK→tenant | テナントID |
| organization_id | UUID | FK→organization | 組織ID |
| email | VARCHAR(255) | NOT NULL | メールアドレス |
| display_name | VARCHAR(255) | NOT NULL | 表示名 |
| is_active | BOOLEAN | NOT NULL | アクティブ状態 |

---

## Relationships

```
┌─────────────────────┐
│      Tenant         │
└─────────────────────┘
          │
          │ 1:N
          ▼
┌─────────────────────┐         ┌─────────────────────┐
│      Member         │◄───────►│      Project        │
└─────────────────────┘   N:M   └─────────────────────┘
          │                              │
          │                              │
          └──────────┬───────────────────┘
                     │
                     ▼
        ┌─────────────────────────────┐
        │  MemberProjectAssignment    │
        │  (Join Table with metadata) │
        └─────────────────────────────┘
```

---

## State Transitions

### MemberProjectAssignment

```
[None] ──assign──► [Active] ──deactivate──► [Inactive]
                       │                         │
                       └───────activate─────────┘
```

- **assign**: 管理者がメンバーをプロジェクトにアサイン
- **deactivate**: アサインメントを無効化（履歴保持）
- **activate**: 無効化されたアサインメントを再有効化

---

## Validation Rules

1. **MemberProjectAssignment作成時**:
   - member_idが存在し、アクティブであること
   - project_idが存在すること
   - 同一のmember_id + project_idの組み合わせが存在しないこと

2. **プロジェクト一覧取得時**:
   - member_project_assignments.is_active = true
   - projects.is_active = true
   - projects.valid_from <= 現在日 <= projects.valid_until（NULLは無制限）

---

## Migration Script

```sql
-- V10__member_project_assignments.sql
CREATE TABLE IF NOT EXISTS member_project_assignments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    member_id UUID NOT NULL REFERENCES members(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES members(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    CONSTRAINT uk_member_project_assignment 
        UNIQUE(tenant_id, member_id, project_id)
);

CREATE INDEX idx_member_project_assignments_member 
    ON member_project_assignments(member_id);
CREATE INDEX idx_member_project_assignments_project 
    ON member_project_assignments(project_id);
CREATE INDEX idx_member_project_assignments_tenant 
    ON member_project_assignments(tenant_id);
CREATE INDEX idx_member_project_assignments_active 
    ON member_project_assignments(member_id, is_active) 
    WHERE is_active = true;

COMMENT ON TABLE member_project_assignments IS 
    'Tracks which members are assigned to which projects for worklog entry';
```
