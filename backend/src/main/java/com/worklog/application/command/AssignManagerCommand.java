package com.worklog.application.command;

import java.util.UUID;

/**
 * Command for assigning a manager (supervisor) to a member.
 *
 * @param memberId  the member to assign a manager to
 * @param managerId the manager to assign
 */
public record AssignManagerCommand(UUID memberId, UUID managerId) {}
