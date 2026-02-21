# API Contract: Member Manager Assignment

**Base Path**: `/api/v1/admin/members`
**Auth**: Session-based, requires `member.*` permissions

---

## PUT /api/v1/admin/members/{id}/manager

Assign or change a member's manager (supervisor).

**Permission**: `member.update`

**Path Parameters**: `id` (UUID) — Member ID

**Request Body**:
```json
{
  "managerId": "uuid"
}
```

**Validation**:
- `managerId`: Required, must reference an active member in the same tenant
- Self-assignment prohibited (managerId cannot equal member id)
- Circular reference detection: full transitive chain traversal (A→B→C→...→A)

**Response 204**: No content

**Error Responses**:
- `404 MEMBER_NOT_FOUND` — Member not found
- `404 MANAGER_NOT_FOUND` — Proposed manager not found
- `400 SELF_ASSIGNMENT` — Cannot assign member as their own manager
- `400 CIRCULAR_REFERENCE` — Assignment would create a circular reporting chain
- `400 MANAGER_INACTIVE` — Proposed manager is inactive

---

## DELETE /api/v1/admin/members/{id}/manager

Remove manager assignment from a member.

**Permission**: `member.update`

**Path Parameters**: `id` (UUID) — Member ID

**Response 204**: No content

**Error Responses**:
- `404 MEMBER_NOT_FOUND` — Member not found
- `400 NO_MANAGER_ASSIGNED` — Member has no manager to remove

---

## PUT /api/v1/admin/members/{id}/organization

Transfer a member to a different organization. Clears the member's manager assignment.

**Permission**: `member.update`

**Path Parameters**: `id` (UUID) — Member ID

**Request Body**:
```json
{
  "organizationId": "uuid"
}
```

**Validation**:
- `organizationId`: Required, must reference an active organization in the same tenant
- Cannot transfer to the same organization

**Response 204**: No content

**Error Responses**:
- `404 MEMBER_NOT_FOUND` — Member not found
- `404 ORGANIZATION_NOT_FOUND` — Target organization not found
- `400 ORGANIZATION_INACTIVE` — Target organization is inactive
- `400 SAME_ORGANIZATION` — Member already belongs to this organization

**Side Effects**:
- Member's `manager_id` is set to NULL
- Member's `organization_id` is updated
- Member's `version` is incremented
- Member's `updated_at` is set to current timestamp

---

## POST /api/v1/admin/members (Existing — Enhanced)

Create a new member within an organization. Already exists in AdminMemberController.

**Permission**: `member.create`

**Request Body** (existing):
```json
{
  "email": "string",
  "displayName": "string",
  "organizationId": "uuid",
  "managerId": "uuid | null"
}
```

**Enhancement**: Validate `managerId` against circular reference rules (self-assignment and transitivity) during creation, consistent with manager assignment endpoint.
