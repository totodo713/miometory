package com.worklog.application.auth;

import com.worklog.domain.user.User;

/**
 * Login response DTO
 */
public record LoginResponse(User user, String sessionId, String rememberMeToken) {}
