package com.worklog.domain.attendance;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

/**
 * Strongly-typed identifier for DailyAttendance entities.
 *
 * Represents the unique identifier for a daily attendance record.
 */
public record DailyAttendanceId(UUID value) implements EntityId {

    public DailyAttendanceId {
        if (value == null) {
            throw new IllegalArgumentException("DailyAttendanceId value cannot be null");
        }
    }

    /**
     * Generates a new random DailyAttendanceId.
     */
    public static DailyAttendanceId generate() {
        return new DailyAttendanceId(UUID.randomUUID());
    }

    /**
     * Creates a DailyAttendanceId from a UUID.
     */
    public static DailyAttendanceId of(UUID value) {
        return new DailyAttendanceId(value);
    }

    /**
     * Creates a DailyAttendanceId from a string representation.
     */
    public static DailyAttendanceId of(String value) {
        return new DailyAttendanceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
