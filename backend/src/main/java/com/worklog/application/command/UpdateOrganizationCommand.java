package com.worklog.application.command;

/**
 * Command to update an organization's name.
 *
 * @param name New display name for the organization
 */
public record UpdateOrganizationCommand(String name) {}
