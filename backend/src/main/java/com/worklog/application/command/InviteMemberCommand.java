package com.worklog.application.command;

import java.util.UUID;

/**
 * Command for inviting a new member to a tenant.
 *
 * @param email        email address of the new member
 * @param displayName  display name of the new member
 * @param organizationId organization to assign the member to (nullable)
 * @param managerId    manager of the new member (nullable)
 * @param invitedBy    UUID of the admin performing the action
 */
public record InviteMemberCommand(
        String email, String displayName, UUID organizationId, UUID managerId, UUID invitedBy) {}
