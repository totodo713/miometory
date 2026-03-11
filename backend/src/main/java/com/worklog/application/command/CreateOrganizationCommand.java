package com.worklog.application.command;

import java.util.UUID;

/**
 * Command to create a new organization.
 *
 * @param tenantId ID of the tenant this organization belongs to
 * @param parentId Optional ID of the parent organization (null for root/level 1)
 * @param code Unique code for the organization (alphanumeric and underscores only)
 * @param name Display name for the organization
 * @param fiscalYearRuleId Optional fiscal year rule ID
 * @param monthlyPeriodRuleId Optional monthly period rule ID
 */
public record CreateOrganizationCommand(
        UUID tenantId, UUID parentId, String code, String name, UUID fiscalYearRuleId, UUID monthlyPeriodRuleId) {}
