package com.worklog.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record CreateProjectCommand(
        UUID tenantId, String code, String name, LocalDate validFrom, LocalDate validUntil) {}
