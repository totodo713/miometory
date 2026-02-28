package com.worklog.api;

import java.util.UUID;

/**
 * Tenant membership details for login response.
 *
 * @param memberId Member record ID
 * @param tenantId Tenant ID
 * @param tenantName Tenant display name
 * @param organizationId Organization ID (null if not assigned)
 * @param organizationName Organization display name (null if not assigned)
 */
public record TenantMembershipDto(
        UUID memberId, UUID tenantId, String tenantName, UUID organizationId, String organizationName) {}
