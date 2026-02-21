# API Contracts: Admin Management

**Feature**: 015-admin-management
**Date**: 2026-02-20
**Base URL**: `/api/v1`

## Authentication & Authorization

All admin endpoints require session-based authentication. Authorization is enforced via `@PreAuthorize` annotations using the `resource.action` permission model.

| Role | Accessible Endpoints |
|------|---------------------|
| SYSTEM_ADMIN | `/api/v1/admin/tenants/**`, `/api/v1/admin/users/**` |
| TENANT_ADMIN | `/api/v1/admin/members/**`, `/api/v1/admin/projects/**`, `/api/v1/admin/assignments/**` |
| SUPERVISOR | `/api/v1/admin/assignments/**` (direct reports only), `/api/v1/worklog/daily-approvals/**` |

---

## 1. Tenant Management (System Admin)

### GET /admin/tenants

List all tenants.

**Query Parameters**:
- `status` (optional): Filter by ACTIVE/INACTIVE
- `page` (optional, default: 0): Page number
- `size` (optional, default: 20): Page size

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "uuid",
      "code": "ACME",
      "name": "Acme Corporation",
      "status": "ACTIVE",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "page": 0,
  "size": 20
}
```

### POST /admin/tenants

Create a new tenant.

**Request**:
```json
{
  "code": "ACME",
  "name": "Acme Corporation"
}
```

**Response** `201 Created`:
```json
{
  "id": "uuid",
  "code": "ACME",
  "name": "Acme Corporation",
  "status": "ACTIVE"
}
```

**Errors**: `400` (validation), `409` (duplicate code)

### PUT /admin/tenants/{id}

Update tenant name.

**Request**:
```json
{
  "name": "Acme Corp Updated"
}
```

**Response** `200 OK`: Updated tenant object.

### PATCH /admin/tenants/{id}/deactivate

Deactivate a tenant.

**Response** `204 No Content`

### PATCH /admin/tenants/{id}/activate

Activate a tenant.

**Response** `204 No Content`

---

## 2. Global User Management (System Admin)

### GET /admin/users

List all users across tenants.

**Query Parameters**:
- `tenantId` (optional): Filter by tenant
- `roleId` (optional): Filter by role
- `accountStatus` (optional): Filter by ACTIVE/UNVERIFIED/LOCKED/DELETED
- `search` (optional): Search by email or name
- `page`, `size`: Pagination

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "uuid",
      "email": "user@example.com",
      "name": "John Doe",
      "roleName": "TENANT_ADMIN",
      "tenantName": "Acme Corporation",
      "accountStatus": "ACTIVE",
      "lastLoginAt": "2026-02-19T10:00:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "page": 0,
  "size": 20
}
```

### PUT /admin/users/{id}/role

Change user role.

**Request**:
```json
{
  "roleId": "uuid"
}
```

**Response** `200 OK`: Updated user object.
**Errors**: `400` (last tenant admin protection)

### PATCH /admin/users/{id}/lock

Lock a user account.

**Request**:
```json
{
  "durationMinutes": 1440
}
```

**Response** `204 No Content`

### PATCH /admin/users/{id}/unlock

Unlock a user account.

**Response** `204 No Content`

### POST /admin/users/{id}/password-reset

Initiate password reset.

**Response** `202 Accepted`

---

## 3. Member Management (Tenant Admin)

### GET /admin/members

List all members in the authenticated user's tenant.

**Query Parameters**:
- `organizationId` (optional): Filter by organization
- `isActive` (optional): Filter by active/inactive status
- `search` (optional): Search by name or email
- `page`, `size`: Pagination

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "uuid",
      "email": "member@example.com",
      "displayName": "Jane Doe",
      "organizationName": "Engineering",
      "managerName": "John Manager",
      "isActive": true,
      "createdAt": "2026-01-15T00:00:00Z"
    }
  ],
  "totalElements": 85,
  "totalPages": 5,
  "page": 0,
  "size": 20
}
```

### POST /admin/members

Invite a new member.

**Request**:
```json
{
  "email": "new@example.com",
  "displayName": "New Member",
  "organizationId": "uuid",
  "managerId": "uuid or null"
}
```

**Response** `201 Created`: Created member object.
**Errors**: `400` (validation), `409` (duplicate email within tenant)

### PUT /admin/members/{id}

Update member details.

**Request**:
```json
{
  "email": "updated@example.com",
  "displayName": "Updated Name",
  "organizationId": "uuid",
  "managerId": "uuid or null"
}
```

**Response** `200 OK`: Updated member object.

### PATCH /admin/members/{id}/deactivate

Deactivate a member.

**Response** `204 No Content`

### PATCH /admin/members/{id}/activate

Activate a member.

**Response** `204 No Content`

### POST /admin/members/{id}/assign-tenant-admin

Assign the Tenant Admin role to a member within the same tenant. Requires `tenant_admin.assign` permission.

**Response** `200 OK`:
```json
{
  "id": "uuid",
  "email": "member@example.com",
  "displayName": "Jane Doe",
  "roleName": "TENANT_ADMIN",
  "isActive": true
}
```

**Errors**: `400` (already a tenant admin), `403` (insufficient permission), `404` (member not found)

---

## 4. Project Management (Tenant Admin)

### GET /admin/projects

List all projects in the authenticated user's tenant.

**Query Parameters**:
- `isActive` (optional): Filter by active/inactive
- `search` (optional): Search by code or name
- `page`, `size`: Pagination

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "uuid",
      "code": "PROJ-001",
      "name": "Main Project",
      "isActive": true,
      "validFrom": "2026-01-01",
      "validUntil": "2026-12-31",
      "assignedMemberCount": 12
    }
  ],
  "totalElements": 30,
  "totalPages": 2,
  "page": 0,
  "size": 20
}
```

### POST /admin/projects

Create a new project.

**Request**:
```json
{
  "code": "PROJ-002",
  "name": "New Project",
  "validFrom": "2026-03-01",
  "validUntil": "2026-12-31"
}
```

**Response** `201 Created`: Created project object.
**Errors**: `400` (validation), `409` (duplicate code within tenant)

### PUT /admin/projects/{id}

Update project details.

**Request**:
```json
{
  "name": "Updated Project Name",
  "validFrom": "2026-03-01",
  "validUntil": "2027-03-31"
}
```

**Response** `200 OK`: Updated project object.

### PATCH /admin/projects/{id}/deactivate

Deactivate a project.

**Response** `204 No Content`

### PATCH /admin/projects/{id}/activate

Activate a project.

**Response** `204 No Content`

---

## 5. Assignment Management (Tenant Admin + Supervisor)

### GET /admin/members/{memberId}/assignments

List assignments for a member.

**Response** `200 OK`:
```json
{
  "assignments": [
    {
      "id": "uuid",
      "projectId": "uuid",
      "projectCode": "PROJ-001",
      "projectName": "Main Project",
      "isActive": true,
      "assignedAt": "2026-01-15T00:00:00Z",
      "assignedByName": "Admin User"
    }
  ]
}
```

### GET /admin/projects/{projectId}/assignments

List members assigned to a project.

**Response** `200 OK`:
```json
{
  "assignments": [
    {
      "id": "uuid",
      "memberId": "uuid",
      "memberName": "Jane Doe",
      "isActive": true,
      "assignedAt": "2026-01-15T00:00:00Z"
    }
  ]
}
```

### POST /admin/assignments

Create an assignment.

**Request**:
```json
{
  "memberId": "uuid",
  "projectId": "uuid"
}
```

**Response** `201 Created`: Created assignment object.
**Errors**: `400` (validation), `403` (supervisor assigning non-direct-report), `409` (duplicate assignment)

### PATCH /admin/assignments/{id}/deactivate

Deactivate an assignment.

**Response** `204 No Content`

### PATCH /admin/assignments/{id}/activate

Activate an assignment.

**Response** `204 No Content`

---

## 6. Daily Approval (Supervisor)

### GET /worklog/daily-approvals

List pending entries from direct reports, grouped by date.

**Query Parameters**:
- `memberId` (optional): Filter by specific team member
- `dateFrom` (optional): Start date filter
- `dateTo` (optional): End date filter
- `status` (optional): Filter by approval status (UNAPPROVED, APPROVED, REJECTED)

**Response** `200 OK`:
```json
{
  "dailyGroups": [
    {
      "date": "2026-02-19",
      "members": [
        {
          "memberId": "uuid",
          "memberName": "Jane Doe",
          "entries": [
            {
              "entryId": "uuid",
              "projectCode": "PROJ-001",
              "projectName": "Main Project",
              "hours": 4.0,
              "comment": "Feature development",
              "approvalStatus": "UNAPPROVED",
              "approvalId": null
            }
          ],
          "totalHours": 8.0
        }
      ]
    }
  ]
}
```

### POST /worklog/daily-approvals/approve

Approve one or more entries (supports bulk).

**Request**:
```json
{
  "entryIds": ["uuid1", "uuid2"],
  "comment": "Looks good"
}
```

**Response** `200 OK`:
```json
{
  "approved": [
    { "entryId": "uuid1", "approvalId": "uuid" },
    { "entryId": "uuid2", "approvalId": "uuid" }
  ]
}
```

### POST /worklog/daily-approvals/reject

Reject an entry.

**Request**:
```json
{
  "entryId": "uuid",
  "comment": "Please correct the hours"
}
```

**Response** `200 OK`:
```json
{
  "entryId": "uuid",
  "approvalId": "uuid",
  "status": "REJECTED"
}
```

**Errors**: `400` (comment required for rejection)

### POST /worklog/daily-approvals/{approvalId}/recall

Recall a daily approval.

**Response** `200 OK`:
```json
{
  "approvalId": "uuid",
  "status": "RECALLED"
}
```

**Errors**: `400` (monthly already approved)

---

## 7. Notifications

### GET /notifications

Get notifications for the authenticated user.

**Query Parameters**:
- `isRead` (optional): Filter by read/unread
- `page`, `size`: Pagination

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "DAILY_APPROVED",
      "title": "Entry approved",
      "message": "Your entry for 2026-02-19 (PROJ-001) was approved by John Manager",
      "isRead": false,
      "createdAt": "2026-02-19T17:00:00Z",
      "referenceId": "uuid"
    }
  ],
  "unreadCount": 5,
  "totalElements": 42,
  "page": 0,
  "size": 20
}
```

### PATCH /notifications/{id}/read

Mark a notification as read.

**Response** `204 No Content`

### PATCH /notifications/read-all

Mark all notifications as read.

**Response** `204 No Content`

---

## 8. Admin Navigation Context

### GET /admin/context

Returns the authenticated user's admin capabilities for rendering navigation.

**Response** `200 OK`:
```json
{
  "role": "TENANT_ADMIN",
  "permissions": [
    "member.view", "member.create", "member.update", "member.deactivate",
    "project.view", "project.create", "project.update", "project.deactivate",
    "assignment.view", "assignment.create", "assignment.deactivate",
    "tenant_admin.assign"
  ],
  "tenantId": "uuid",
  "tenantName": "Acme Corporation",
  "memberId": "uuid"
}
```

---

## Error Response Format

All error responses follow the existing pattern:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Human-readable error message",
  "details": {
    "field": "specific field error"
  },
  "timestamp": "2026-02-20T10:00:00Z"
}
```

| HTTP Status | Usage |
|-------------|-------|
| 400 | Validation errors, business rule violations |
| 401 | Not authenticated |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | Conflict (duplicate code, last admin protection) |
