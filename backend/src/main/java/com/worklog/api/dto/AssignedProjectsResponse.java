package com.worklog.api.dto;

import java.util.List;

/**
 * Response DTO for assigned projects endpoint.
 *
 * Returns a list of projects that a member is assigned to and can log time against.
 */
public record AssignedProjectsResponse(List<AssignedProject> projects, int count) {}
