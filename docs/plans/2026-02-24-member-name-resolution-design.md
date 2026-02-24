# Design: Resolve Member/Reviewer Names from Repository

**Issue**: #27 — メンバー/レビュワー名をリポジトリから取得する（TODOコメント4箇所）
**Date**: 2026-02-24

## Problem

Four TODO comments in controllers return hardcoded or null values for member display names:

| Location | Current Value | Field |
|----------|--------------|-------|
| `CalendarController.java:101` | `null` | `reviewerName` in `MonthlyApprovalSummary` |
| `CalendarController.java:145` | `"Member Name"` | `memberName` in `MonthlyCalendarResponse` |
| `ApprovalController.java:237` | `null` | `reviewerName` in `MemberApprovalResponse` |
| `RejectionController.java:43` | `null` | `rejectedByName` in `DailyRejectionItem` |

## Design Decisions

### Approach: Hybrid — `findById` for single lookups, batch method for lists

Based on code architecture review:

- **Single-ID lookups (3 locations)**: Use existing `JdbcMemberRepository.findById(MemberId)` + `Member::getDisplayName`. Matches the established pattern in `ApprovalService.java:273-275`.
- **List lookup (RejectionController)**: Add `findDisplayNamesByIds(Set<MemberId>)` batch method to avoid N+1 queries.

### Fallback Behavior

When a member ID is not found (e.g., deleted member), return `null` for the name field. The API consumers handle null display names gracefully.

### Architecture Compliance

This project has no domain port interfaces — all controllers directly inject `Jdbc*Repository` classes (established pattern). Adding `JdbcMemberRepository` to 3 controllers is consistent with existing codebase conventions. No new interfaces, services, or abstractions are introduced.

## Changes

### 1. Repository: `JdbcMemberRepository.java`

Add one batch lookup method:

```java
public Map<MemberId, String> findDisplayNamesByIds(Set<MemberId> ids)
```

- Empty set returns empty map immediately
- SQL: `SELECT id, display_name FROM members WHERE id IN (...)`
- Missing IDs are omitted from result map
- Uses existing `JdbcTemplate` (no `NamedParameterJdbcTemplate` needed)

### 2. Controller: `CalendarController.java`

**New dependency**: Inject `JdbcMemberRepository`.

**L145** — Member name for calendar response:
```java
// Before
"Member Name", // TODO: Fetch from member repository
// After
memberRepository.findById(MemberId.of(memberId))
    .map(Member::getDisplayName).orElse(null),
```

**L101** — Reviewer name in monthly approval summary:
```java
// Before
null, // TODO: Fetch reviewer name from member repository
// After
memberRepository.findById(a.getReviewedBy())
    .map(Member::getDisplayName).orElse(null),
```

### 3. Controller: `ApprovalController.java`

**New dependency**: Inject `JdbcMemberRepository`.

**L237** — Reviewer name in member approval response:
```java
// Before
null, // TODO: Fetch reviewer name from member repository
// After
a.getReviewedBy() != null
    ? memberRepository.findById(a.getReviewedBy())
        .map(Member::getDisplayName).orElse(null)
    : null,
```

### 4. Controller: `RejectionController.java`

**New dependency**: Inject `JdbcMemberRepository`.

**L43** — Rejected-by names in rejection list (batch):
```java
// Collect unique rejectedBy IDs
Set<MemberId> rejectorIds = records.stream()
    .map(DailyRejectionRecord::rejectedBy)
    .filter(Objects::nonNull)
    .map(MemberId::of)
    .collect(Collectors.toSet());

Map<MemberId, String> nameMap = memberRepository.findDisplayNamesByIds(rejectorIds);

// Use in stream mapping
nameMap.get(MemberId.of(r.rejectedBy())),
```

### 5. Tests

- Unit test for `findDisplayNamesByIds` in `JdbcMemberRepository` (or integration test via existing test base)
- Existing controller integration tests will validate name resolution if they check response fields

## Files to Change

| File | Action |
|------|--------|
| `infrastructure/repository/JdbcMemberRepository.java` | Add `findDisplayNamesByIds` method |
| `api/CalendarController.java` | Inject repo, resolve names at L101 and L145 |
| `api/ApprovalController.java` | Inject repo, resolve name at L237 |
| `api/RejectionController.java` | Inject repo, batch resolve names at L43 |

No new files created.
