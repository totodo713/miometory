package com.worklog.api;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for user login endpoint.
 *
 * @param user User details (id, email, name, accountStatus)
 * @param sessionExpiresAt When the session will expire (ISO 8601 timestamp)
 * @param rememberMeToken Optional remember-me token (if user checked "Remember Me")
 * @param warning Optional warning message (e.g., account expiring soon)
 * @param tenantAffiliationState User's tenant affiliation state (UNAFFILIATED, AFFILIATED_NO_ORG, FULLY_ASSIGNED)
 * @param memberships List of tenant memberships for the user (empty if unaffiliated)
 */
public record LoginResponseDto(
        UserDto user,
        Instant sessionExpiresAt,
        String rememberMeToken,
        String warning,
        String tenantAffiliationState,
        List<TenantMembershipDto> memberships) {}
