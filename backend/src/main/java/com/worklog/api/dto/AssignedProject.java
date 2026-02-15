package com.worklog.api.dto;

/**
 * DTO for an assigned project in the dropdown list.
 *
 * Contains the project ID, code, and name for display in the project selector.
 */
public record AssignedProject(String id, String code, String name) {}
