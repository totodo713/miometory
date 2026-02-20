package com.worklog.application.command;

import java.util.List;
import java.util.UUID;

public record ApproveDailyEntryCommand(List<UUID> entryIds, UUID supervisorId, String comment) {}
