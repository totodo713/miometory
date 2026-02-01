package com.worklog.api.dto;

import java.util.List;

/**
 * Response DTO for subordinates endpoint.
 * Returns a list of members who report to the specified manager.
 */
public record SubordinatesResponse(
    List<MemberResponse> subordinates,
    int count,
    boolean includesIndirect
) {}
