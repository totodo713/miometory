package com.worklog.domain.attendance;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record DailyAttendanceId(UUID value) implements EntityId {

    public DailyAttendanceId {
        if (value == null) {
            throw new IllegalArgumentException("DailyAttendanceId value cannot be null");
        }
    }

    public static DailyAttendanceId generate() {
        return new DailyAttendanceId(UUID.randomUUID());
    }

    public static DailyAttendanceId of(UUID value) {
        return new DailyAttendanceId(value);
    }

    public static DailyAttendanceId of(String value) {
        return new DailyAttendanceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
