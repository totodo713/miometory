package com.worklog.application.command;

/**
 * Command to create a new tenant.
 * 
 * @param code Unique code for the tenant (alphanumeric and underscores only)
 * @param name Display name for the tenant
 */
public record CreateTenantCommand(
    String code,
    String name
) {
}
