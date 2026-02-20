package com.worklog.application.command;

import java.util.UUID;

public record RecallDailyApprovalCommand(UUID approvalId, UUID supervisorId) {}
