package com.worklog.api.dto;

import java.util.UUID;

/**
 * Request DTO for approving a submitted month.
 * Contains the ID of the manager/reviewer approving the submission.
 */
public record ApproveMonthRequest(
    UUID reviewedBy
) {
    public ApproveMonthRequest {
        if (reviewedBy == null) {
            throw new IllegalArgumentException("reviewedBy is required");
        }
    }
}
