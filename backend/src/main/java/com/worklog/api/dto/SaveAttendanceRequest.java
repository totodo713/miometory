package com.worklog.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request DTO for saving (creating or updating) a daily attendance record.
 *
 * @param date The attendance date
 * @param startTime Start time (nullable)
 * @param endTime End time (nullable)
 * @param remarks Remarks (nullable, max 500 characters)
 * @param version Expected version for optimistic locking (nullable for new records)
 */
public record SaveAttendanceRequest(
        LocalDate date, LocalTime startTime, LocalTime endTime, String remarks, Integer version) {}
