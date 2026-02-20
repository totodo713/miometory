package com.worklog.application.command;

import java.util.UUID;

/**
 * Command for updating an existing member's details.
 *
 * @param memberId       the member to update
 * @param email          new email address
 * @param displayName    new display name
 * @param organizationId new organization (nullable)
 * @param managerId      new manager (nullable)
 * @param updatedBy      UUID of the admin performing the action
 */
public record UpdateMemberCommand(
        UUID memberId, String email, String displayName, UUID organizationId, UUID managerId, UUID updatedBy) {}
