package com.worklog.application.auth;

/**
 * Login request DTO
 */
public record LoginRequest(String email, String password, boolean rememberMe) {}
