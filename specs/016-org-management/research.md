# Research: Organization Management

**Feature**: 016-org-management
**Date**: 2026-02-21

## Decision 1: Organization Aggregate — Event Sourced vs Direct JDBC

**Decision**: Keep Organization as an event-sourced aggregate (existing pattern).

**Rationale**: The Organization aggregate already exists with full event sourcing support (OrganizationCreated, OrganizationUpdated, OrganizationDeactivated, OrganizationActivated, OrganizationPatternAssigned events). The OrganizationRepository already handles event replay and reconstruction. Changing to direct JDBC would break existing event history and require a migration strategy.

**Alternatives considered**:
- Direct JDBC (like Member): Rejected — Organization already has event history in the event_store table. Switching would orphan existing events and lose audit trail.
- Hybrid approach: Rejected — unnecessary complexity when the event-sourced pattern already works.

**Impact**: The OrganizationRepository needs to be extended to synchronously update the `organization` projection table (currently not done — events are stored but projections are not updated automatically like WorkLogEntry's `updateProjection()` pattern). This is a critical gap that must be addressed.

## Decision 2: Member Organization Transfer — Domain Model Change

**Decision**: Make Member's `organizationId` field mutable by removing the `final` modifier and adding a `changeOrganization(OrganizationId)` method.

**Rationale**: FR-015 requires tenant administrators to transfer members between organizations. The current `final OrganizationId organizationId` prevents this. Since Member is not event-sourced (direct JDBC), this is a straightforward field change with no event history implications.

**Alternatives considered**:
- Deactivate + recreate: Rejected — would change the member's UUID, breaking references in work log entries, approval workflows, and notification history.
- Event-source Member: Rejected — massive scope increase with no proportional benefit for this feature.

**Impact**: Change `Member.java` field from `private final OrganizationId organizationId` to `private OrganizationId organizationId`. Add `changeOrganization()` method that also calls `removeManager()` (per FR-014). Update `JdbcMemberRepository.save()` to persist the new organizationId.

## Decision 3: Circular Reference Detection Algorithm

**Decision**: Use iterative chain traversal with the existing `JdbcMemberRepository` pattern, bounded by tenant member count.

**Rationale**: FR-008 requires full transitive circular detection (A→B→C→...→A at any depth). The existing `findAllSubordinates(managerId)` method already uses a recursive CTE with a max depth of 10 levels. For circular detection, we need to walk the manager chain upward from the proposed manager and check if the target member appears. This is O(n) where n is the chain length (typically <10 in practice).

**Alternatives considered**:
- Graph library (JGraphT): Rejected — adds external dependency for a simple chain traversal.
- Database-level constraint: Rejected — PostgreSQL doesn't support recursive check constraints for this pattern.
- Depth-limited detection: Rejected — spec explicitly requires "any depth" detection.

**Implementation**:
```java
public boolean wouldCreateCircularReference(MemberId memberId, MemberId proposedManagerId) {
    if (memberId.equals(proposedManagerId)) return true; // Self-assignment

    MemberId current = proposedManagerId;
    Set<MemberId> visited = new HashSet<>();
    while (current != null) {
        if (current.equals(memberId)) return true; // Circular!
        if (!visited.add(current)) break; // Safety: already visited
        Member manager = findById(current).orElse(null);
        current = (manager != null) ? manager.getManagerId() : null;
    }
    return false;
}
```

## Decision 4: Organization Projection Synchronization

**Decision**: Add synchronous projection updates to OrganizationRepository.save(), following the JdbcWorkLogRepository.updateProjection() pattern.

**Rationale**: The existing `organization` projection table (V2 migration) has all required columns (id, tenant_id, parent_id, code, name, level, status, version, fiscal_year_pattern_id, monthly_period_pattern_id). Currently, events are stored in the event_store but the projection table is not kept in sync. The admin UI needs to query the projection table for list/search/filter operations.

**Alternatives considered**:
- Query event_store directly: Rejected — would require replaying all events for each list query, O(n*m) complexity.
- Async event handler: Rejected — adds eventual consistency complexity; synchronous projection is simpler and already used by WorkLogEntry.
- Separate projection service: Rejected — overengineering for a single aggregate.

**Implementation**: Add `updateProjection()` method to OrganizationRepository that handles INSERT/UPDATE/DELETE on the `organization` table within the same @Transactional boundary as event_store append.

## Decision 5: New Permissions for Organization Management

**Decision**: Add new permissions following the existing `{resource}.{action}` pattern.

**Rationale**: V18 migration seeds permissions for member, project, tenant, etc. Organization management needs its own set of permissions gated by the TENANT_ADMIN role.

**New permissions**:
- `organization.view` — View organization list and tree
- `organization.create` — Create new organizations
- `organization.update` — Update organization name, assign patterns
- `organization.deactivate` — Deactivate/reactivate organizations

**Role assignments**:
- TENANT_ADMIN: all organization permissions
- SUPERVISOR: organization.view only (to see hierarchy)

## Decision 6: Frontend Organization Tree Implementation

**Decision**: Custom collapsible tree component using recursive rendering with Tailwind CSS indentation.

**Rationale**: The tree needs to be interactive (click to view details, collapse/expand), display up to 100 organizations, and follow existing Tailwind CSS patterns. A custom component is simpler than adding a tree library dependency.

**Alternatives considered**:
- react-arborist: Rejected — adds external dependency, heavyweight for simple tree display.
- Flat list with indentation: Rejected — doesn't provide collapse/expand behavior.
- D3.js tree: Rejected — overkill for admin panel, different styling paradigm.

**Implementation**: Recursive `TreeNode` component with:
- Left padding based on level (`pl-{level * 4}`)
- Expand/collapse chevron toggle
- Click handler to select organization and show details
- Status badge (active/inactive)
- Member count indicator

## Decision 7: API Endpoint Structure

**Decision**: Follow existing admin endpoint pattern: `/api/v1/admin/organizations/...`

**Rationale**: All admin endpoints use `/api/v1/admin/{resource}` prefix. Organization management follows the same pattern with standard CRUD + tree endpoint.

**Endpoints**:
- `GET /api/v1/admin/organizations` — Paginated list with search/filter
- `POST /api/v1/admin/organizations` — Create organization
- `PUT /api/v1/admin/organizations/{id}` — Update organization name
- `PATCH /api/v1/admin/organizations/{id}/deactivate` — Deactivate
- `PATCH /api/v1/admin/organizations/{id}/activate` — Reactivate
- `GET /api/v1/admin/organizations/tree` — Hierarchical tree structure
- `PUT /api/v1/admin/organizations/{id}/patterns` — Assign fiscal/monthly patterns
- `GET /api/v1/admin/organizations/{id}/members` — List members in organization
- `PUT /api/v1/admin/members/{id}/manager` — Assign/change manager
- `DELETE /api/v1/admin/members/{id}/manager` — Remove manager
- `PUT /api/v1/admin/members/{id}/organization` — Transfer member to different org
