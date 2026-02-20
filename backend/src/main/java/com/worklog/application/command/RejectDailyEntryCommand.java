package com.worklog.application.command;

import java.util.UUID;

public record RejectDailyEntryCommand(UUID entryId, UUID supervisorId, String comment) {}
