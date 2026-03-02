**Note**: Development now uses devcontainer. See [QUICKSTART.md](/QUICKSTART.md) for current setup instructions.

# Quickstart: Organization Management

**Feature**: 016-org-management
**Date**: 2026-02-21

## Prerequisites

1. Backend running: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
2. Frontend running: `cd frontend && npm run dev`
3. Database running: `cd infra/docker && docker-compose -f docker-compose.dev.yml up -d`
4. Logged in as tenant admin (use test user from data-dev.sql)

## Scenario 1: Create Organization Hierarchy

**Goal**: Create a 3-level organization structure (Company → Department → Team)

1. Navigate to Admin Panel → 組織
2. Click "作成" to open the organization form
3. Create root organization:
   - Code: `COMPANY_A`
   - Name: `株式会社A`
   - Parent: (none)
4. Verify it appears in the list with Level 1, ACTIVE status
5. Create child organization:
   - Code: `DEPT_SALES`
   - Name: `営業部`
   - Parent: `株式会社A`
6. Verify Level 2, parent shows as `株式会社A`
7. Create sub-child:
   - Code: `TEAM_SALES_1`
   - Name: `営業第一チーム`
   - Parent: `営業部`
8. Verify Level 3

**Expected**: Three organizations in hierarchy, each with correct level and parent.

## Scenario 2: Assign Supervisor to Members

**Goal**: Assign a manager to a member and verify circular reference prevention

1. Navigate to 組織 → select an organization
2. View its member list
3. Click a member to see their details
4. Click "マネージャー変更" to open manager assignment
5. Select another member as manager
6. Verify the assignment is saved (manager name appears)
7. Try to assign the original member as the manager of their new manager
8. Verify the system rejects with circular reference error

**Expected**: Manager assignment works, circular references are blocked.

## Scenario 3: Organization Tree Visualization

**Goal**: View the organization hierarchy as a tree

1. Navigate to 組織 → ツリー表示 tab
2. Verify the tree shows all organizations with proper nesting
3. Click a parent node's collapse toggle
4. Verify child organizations are hidden
5. Click again to expand
6. Click on an organization node
7. Verify the details panel shows members and their managers

**Expected**: Interactive tree with collapse/expand and detail view.

## Scenario 4: Deactivate and Reactivate Organization

**Goal**: Test organization lifecycle management

1. Navigate to 組織 → select an organization with children
2. Click "無効化"
3. Verify warning about active children appears
4. Confirm deactivation
5. Verify status changes to INACTIVE
6. Verify child organizations remain ACTIVE
7. Click "有効化" to reactivate
8. Verify status returns to ACTIVE

**Expected**: Deactivation with warning, children unaffected, reactivation works.

## Scenario 5: Transfer Member Between Organizations

**Goal**: Move a member from one organization to another

1. Navigate to 組織 → select organization A with a member who has a manager
2. Note the member's current manager assignment
3. Click "組織変更" on the member
4. Select a different organization B
5. Confirm the transfer
6. Verify the member now appears in organization B's member list
7. Verify the member's manager assignment was cleared

**Expected**: Member moved, manager cleared automatically.

## Scenario 6: Assign Fiscal Year Pattern to Organization

**Goal**: Configure work period calculation for an organization

1. Navigate to 組織 → select an organization
2. Click "パターン設定"
3. Select a fiscal year pattern from the dropdown
4. Select a monthly period pattern
5. Save
6. Verify the patterns are displayed in the organization details

**Expected**: Patterns assigned and displayed.

## Test Users (from data-dev.sql)

Refer to `backend/src/main/resources/data-dev.sql` for available test users with tenant admin roles.

## API Testing (curl)

```bash
# Login (get session cookie)
curl -c /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"password123"}'

# List organizations
curl -b /tmp/cookies.txt http://localhost:8080/api/v1/admin/organizations

# Create organization
curl -b /tmp/cookies.txt -X POST http://localhost:8080/api/v1/admin/organizations \
  -H 'Content-Type: application/json' \
  -d '{"code":"NEW_ORG","name":"新しい組織"}'

# Get organization tree
curl -b /tmp/cookies.txt http://localhost:8080/api/v1/admin/organizations/tree

# Assign manager
curl -b /tmp/cookies.txt -X PUT http://localhost:8080/api/v1/admin/members/{memberId}/manager \
  -H 'Content-Type: application/json' \
  -d '{"managerId":"manager-uuid-here"}'
```
