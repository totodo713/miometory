package com.worklog.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateProjectCommand(UUID projectId, String name, LocalDate validFrom, LocalDate validUntil) {}
