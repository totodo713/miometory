package com.worklog.domain.notification;

import com.worklog.domain.shared.EntityId;
import java.util.UUID;

public record NotificationId(UUID value) implements EntityId {

    public NotificationId {
        if (value == null) {
            throw new IllegalArgumentException("NotificationId value cannot be null");
        }
    }

    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId of(UUID value) {
        return new NotificationId(value);
    }

    public static NotificationId of(String value) {
        return new NotificationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
