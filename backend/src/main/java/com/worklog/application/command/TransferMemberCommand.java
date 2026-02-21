package com.worklog.application.command;

import java.util.UUID;

/**
 * Command for transferring a member to a different organization.
 *
 * @param memberId       the member to transfer
 * @param targetOrgId    the target organization
 */
public record TransferMemberCommand(UUID memberId, UUID targetOrgId) {}
