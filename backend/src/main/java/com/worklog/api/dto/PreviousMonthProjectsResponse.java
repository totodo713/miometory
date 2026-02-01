package com.worklog.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response for copy previous month projects endpoint.
 * 
 * @param projectIds List of unique project IDs from the previous month
 * @param previousMonthStart Start date of the previous fiscal month
 * @param previousMonthEnd End date of the previous fiscal month
 * @param count Number of projects found
 */
public record PreviousMonthProjectsResponse(
    List<UUID> projectIds,
    LocalDate previousMonthStart,
    LocalDate previousMonthEnd,
    int count
) {
}
