package com.worklog.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SaveAttendanceRequest(
        UUID memberId, LocalDate date, LocalTime startTime, LocalTime endTime, String remarks, int version) {}
