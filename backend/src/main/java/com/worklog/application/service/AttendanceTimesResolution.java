package com.worklog.application.service;

import java.time.LocalTime;

/**
 * Result of resolving default attendance times through the 4-tier hierarchy.
 *
 * @param startTime The resolved default start time
 * @param endTime The resolved default end time
 * @param source Where the value was resolved from: "member", "organization:{uuid}", "tenant", or "system"
 */
public record AttendanceTimesResolution(LocalTime startTime, LocalTime endTime, String source) {}
