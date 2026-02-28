# Design: organization_id nullable + member.assign_tenant permission

**Issue:** #47
**Date:** 2026-02-28
**Status:** Approved

## Goal

Prepare the foundation for public user registration by making `members.organization_id` nullable and adding a `member.assign_tenant` permission. This is a backward-compatible change that does not affect existing data or behavior.

## Background

Currently, a Member is always associated with an Organization during signup. The target architecture separates signup from tenant assignment: users register first (no organization), then an admin assigns them to a tenant/organization later.

## Changes

### 1. DB Migration: V27__nullable_member_organization.sql

- `ALTER TABLE members ALTER COLUMN organization_id DROP NOT NULL`
- Insert `member.assign_tenant` permission
- Grant to SYSTEM_ADMIN and TENANT_ADMIN roles
- FK constraint `REFERENCES organization(id)` is retained (NULL values skip FK validation per SQL standard)

### 2. Member Entity (Member.java)

- **Remove** `Objects.requireNonNull(organizationId)` from constructor (L57)
- **Add** `createForTenant(TenantId, String email, String displayName)` factory method — creates Member with organizationId=null
- **Add** `hasOrganization()` method — returns `organizationId != null`
- Existing `create()` factory remains unchanged (still requires organizationId)

### 3. JdbcMemberRepository

- **save()**: Null-safe access for `member.getOrganizationId().value()` → ternary check
- **MemberRowMapper**: Null-safe mapping for `organization_id` column → return null OrganizationId when DB value is null

### 4. Tests

- Member domain tests: `createForTenant` with org=null, `hasOrganization()` true/false
- JdbcMemberRepository integration tests: save and findById with org=null Member

## Null Handling Strategy

- `OrganizationId` record keeps its non-null validation (constructor throws on null)
- `Member.organizationId` field becomes nullable (null = not yet assigned to organization)
- Callers check `member.getOrganizationId() != null` or use `member.hasOrganization()`

## Impact Analysis

- 12 files reference `OrganizationId` — all existing code paths work with organization-assigned Members, so no changes needed
- Backward-compatible: existing data is unaffected (all current Members have organization_id set)
