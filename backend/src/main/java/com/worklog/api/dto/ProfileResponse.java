package com.worklog.api.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID id, String email, String displayName, String organizationName, String managerName, boolean isActive) {}
