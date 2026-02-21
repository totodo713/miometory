package com.worklog.domain.dailyapproval;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record DailyEntryApprovalId(UUID value) implements EntityId {

    public DailyEntryApprovalId {
        if (value == null) {
            throw new IllegalArgumentException("DailyEntryApprovalId value cannot be null");
        }
    }

    public static DailyEntryApprovalId generate() {
        return new DailyEntryApprovalId(UUID.randomUUID());
    }

    public static DailyEntryApprovalId of(UUID value) {
        return new DailyEntryApprovalId(value);
    }

    public static DailyEntryApprovalId of(String value) {
        return new DailyEntryApprovalId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
