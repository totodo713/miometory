package com.worklog.application.command;

import java.time.LocalTime;
import java.util.UUID;

public record CreateAssignmentCommand(
        UUID tenantId,
        UUID memberId,
        UUID projectId,
        UUID assignedBy,
        LocalTime defaultStartTime,
        LocalTime defaultEndTime) {}
