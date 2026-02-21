package com.worklog.application.command;

import java.util.UUID;

public record CreateAssignmentCommand(UUID tenantId, UUID memberId, UUID projectId, UUID assignedBy) {}
