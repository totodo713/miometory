package com.worklog.application.command;

import java.util.UUID;

/**
 * Command to create a new organization.
 * 
 * @param tenantId ID of the tenant this organization belongs to
 * @param parentId Optional ID of the parent organization (null for root/level 1)
 * @param code Unique code for the organization (alphanumeric and underscores only)
 * @param name Display name for the organization
 * @param level Hierarchy level (1-6, where 1 is root)
 * @param fiscalYearPatternId Optional fiscal year pattern ID
 * @param monthlyPeriodPatternId Optional monthly period pattern ID
 */
public record CreateOrganizationCommand(
    UUID tenantId,
    UUID parentId,
    String code,
    String name,
    int level,
    UUID fiscalYearPatternId,
    UUID monthlyPeriodPatternId
) {
}
