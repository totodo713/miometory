# Design: Remove Member Creation from Signup & Extend Login Response

**Issue**: #49
**Date**: 2026-02-28
**Status**: Draft

## Overview

Remove automatic Member record creation from signup flow and extend the login response to include tenant affiliation state and membership details.

## Prerequisites (Completed)

- Phase 1 (DB + Domain): TenantAffiliationStatus enum, Member.createForTenant(), V27/V28 migrations
- Phase 2 (Services): UserStatusService with getUserStatus(), TenantAssignmentService

## Changes

### 3-1. AuthServiceImpl.signup()

**File**: `backend/src/main/java/com/worklog/application/auth/AuthServiceImpl.java`

- Delete L106-115 (Member creation block)
- Remove constructor params: `memberRepository`, `defaultTenantId`, `defaultOrganizationId`
- Remove corresponding fields and unused imports

### 3-2. LoginResponseDto Extension

**File**: `backend/src/main/java/com/worklog/api/LoginResponseDto.java`

Add fields:
- `tenantAssignmentState` (String) — maps from TenantAffiliationStatus enum
- `memberships` (List<TenantMembershipDto>)

**New file**: `backend/src/main/java/com/worklog/api/TenantMembershipDto.java`

```java
public record TenantMembershipDto(
    String memberId,
    String tenantId,
    String tenantName,
    String organizationId,
    String organizationName) {}
```

### 3-3. AuthController.login()

**File**: `backend/src/main/java/com/worklog/api/AuthController.java`

- Inject `UserStatusService` (replace current `UserContextService` dependency)
- After successful login, call `userStatusService.getUserStatus(email)`
- Map `UserStatusResponse` to `TenantMembershipDto` list
- If single tenant (memberships.size() == 1), auto-save `selectedTenantId` in user_sessions via `userStatusService.selectTenant()`
- Remove `userContextService` dependency (memberId now derivable from UserStatusResponse)

### 3-4. SecurityConfig

**File**: `backend/src/main/java/com/worklog/infrastructure/config/SecurityConfig.kt`

- Add `.requestMatchers("/api/v1/user/**").authenticated()` to prod filter chain

### 3-5. Configuration Cleanup

- Remove `default-tenant-id` and `default-organization-id` from `application.yaml`
- Remove same from `application-dev.yaml`

### 3-6. Tests

- AuthServiceImpl: verify signup does NOT create Member
- AuthController: verify login response includes tenantAssignmentState and memberships
- Update existing tests that assume Member creation during signup

## Design Decisions

- **New TenantMembershipDto**: API-layer DTO separate from UserStatusService.MembershipDto for layer independence
- **Auto-select single tenant**: When user has exactly one membership, auto-set selectedTenantId in session to reduce UX friction
- **Remove UserContextService from AuthController**: UserStatusService provides superset of needed data
