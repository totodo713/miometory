package com.worklog.application.auth;

/**
 * Registration request DTO
 */
public record RegistrationRequest(
    String email,
    String name,
    String password
) {}
