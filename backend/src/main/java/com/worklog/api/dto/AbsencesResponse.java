package com.worklog.api.dto;

import java.util.List;

/**
 * Response DTO for a list of absences.
 */
public record AbsencesResponse(
    List<AbsenceResponse> absences,
    int total
) {
}
