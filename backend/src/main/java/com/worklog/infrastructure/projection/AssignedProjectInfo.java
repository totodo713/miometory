package com.worklog.infrastructure.projection;

import java.util.UUID;

/**
 * Projection for assigned project information.
 *
 * Used when fetching the list of projects a member is assigned to.
 * Contains only the fields needed for display in the project selector.
 */
public record AssignedProjectInfo(UUID projectId, String code, String name) {}
