package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for submitting a fiscal month for approval.
 * Contains the member ID, fiscal month period, and the ID of the person submitting.
 */
public record SubmitMonthRequest(
        UUID memberId, LocalDate fiscalMonthStart, LocalDate fiscalMonthEnd, UUID submittedBy) {
    public SubmitMonthRequest {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        if (fiscalMonthStart == null) {
            throw new IllegalArgumentException("fiscalMonthStart is required");
        }
        if (fiscalMonthEnd == null) {
            throw new IllegalArgumentException("fiscalMonthEnd is required");
        }
        // submittedBy is optional, will default to memberId in controller if not provided
    }
}
