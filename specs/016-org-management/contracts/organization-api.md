# API Contract: Organization Management

**Base Path**: `/api/v1/admin/organizations`
**Auth**: Session-based, requires `organization.*` permissions

---

## GET /api/v1/admin/organizations

List organizations with pagination, search, and filtering.

**Permission**: `organization.view`

**Query Parameters**:

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| page | int | No | 0 | Page number (0-based) |
| size | int | No | 20 | Page size (max 100) |
| search | string | No | | Search by name or code |
| isActive | boolean | No | | Filter by status (true=ACTIVE, false=INACTIVE, omit=all) |
| parentId | UUID | No | | Filter by parent organization |

**Response 200**:
```json
{
  "content": [
    {
      "id": "uuid",
      "tenantId": "uuid",
      "parentId": "uuid | null",
      "parentName": "string | null",
      "code": "string",
      "name": "string",
      "level": 1,
      "status": "ACTIVE",
      "memberCount": 5,
      "fiscalYearPatternId": "uuid | null",
      "monthlyPeriodPatternId": "uuid | null",
      "createdAt": "2026-02-21T00:00:00Z",
      "updatedAt": "2026-02-21T00:00:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "number": 0
}
```

---

## POST /api/v1/admin/organizations

Create a new organization.

**Permission**: `organization.create`

**Request Body**:
```json
{
  "code": "string (1-32 chars, alphanumeric + underscore)",
  "name": "string (1-256 chars)",
  "parentId": "uuid | null"
}
```

**Validation**:
- `code`: Required, 1-32 chars, `[A-Za-z0-9_]+`, unique within tenant
- `name`: Required, 1-256 chars, non-blank
- `parentId`: If provided, must reference an existing active organization in the same tenant
- Hierarchy depth must not exceed 6 levels

**Response 201**:
```json
{
  "id": "uuid"
}
```

**Error Responses**:
- `400 VALIDATION_ERROR` — Invalid input
- `409 CODE_ALREADY_EXISTS` — Organization code already used in tenant
- `400 MAX_DEPTH_EXCEEDED` — Would exceed 6-level hierarchy limit
- `404 PARENT_NOT_FOUND` — Parent organization not found

---

## PUT /api/v1/admin/organizations/{id}

Update organization name.

**Permission**: `organization.update`

**Path Parameters**: `id` (UUID)

**Request Body**:
```json
{
  "name": "string (1-256 chars)"
}
```

**Response 204**: No content

**Error Responses**:
- `400 VALIDATION_ERROR` — Invalid name
- `404 ORGANIZATION_NOT_FOUND` — Organization not found
- `400 ORGANIZATION_INACTIVE` — Cannot update inactive organization

---

## PATCH /api/v1/admin/organizations/{id}/deactivate

Deactivate an organization.

**Permission**: `organization.deactivate`

**Path Parameters**: `id` (UUID)

**Response 200**:
```json
{
  "warnings": ["This organization has 3 active child organizations that will remain active."]
}
```

**Error Responses**:
- `404 ORGANIZATION_NOT_FOUND`
- `400 ORGANIZATION_ALREADY_INACTIVE`

---

## PATCH /api/v1/admin/organizations/{id}/activate

Reactivate an organization.

**Permission**: `organization.deactivate`

**Path Parameters**: `id` (UUID)

**Response 204**: No content

**Error Responses**:
- `404 ORGANIZATION_NOT_FOUND`
- `400 ORGANIZATION_ALREADY_ACTIVE`

---

## GET /api/v1/admin/organizations/tree

Get the full organization hierarchy as a tree structure.

**Permission**: `organization.view`

**Query Parameters**:

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| includeInactive | boolean | No | false | Include inactive organizations |

**Response 200**:
```json
[
  {
    "id": "uuid",
    "code": "string",
    "name": "string",
    "level": 1,
    "status": "ACTIVE",
    "memberCount": 5,
    "children": [
      {
        "id": "uuid",
        "code": "string",
        "name": "string",
        "level": 2,
        "status": "ACTIVE",
        "memberCount": 3,
        "children": []
      }
    ]
  }
]
```

---

## PUT /api/v1/admin/organizations/{id}/patterns

Assign fiscal year and monthly period patterns.

**Permission**: `organization.update`

**Path Parameters**: `id` (UUID)

**Request Body**:
```json
{
  "fiscalYearPatternId": "uuid | null",
  "monthlyPeriodPatternId": "uuid | null"
}
```

**Response 204**: No content

**Error Responses**:
- `404 ORGANIZATION_NOT_FOUND`
- `404 PATTERN_NOT_FOUND` — Referenced pattern does not exist

---

## GET /api/v1/admin/organizations/{id}/members

List members belonging to an organization.

**Permission**: `organization.view`

**Path Parameters**: `id` (UUID)

**Query Parameters**:

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| page | int | No | 0 | Page number |
| size | int | No | 20 | Page size (max 100) |
| isActive | boolean | No | | Filter by member active status |

**Response 200**:
```json
{
  "content": [
    {
      "id": "uuid",
      "email": "string",
      "displayName": "string",
      "managerId": "uuid | null",
      "managerName": "string | null",
      "isActive": true,
      "createdAt": "2026-02-21T00:00:00Z"
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "number": 0
}
```
