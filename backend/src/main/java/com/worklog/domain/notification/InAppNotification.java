package com.worklog.domain.notification;

import com.worklog.domain.member.MemberId;
import java.time.Instant;
import java.util.UUID;

public class InAppNotification {

    private final NotificationId id;
    private final MemberId recipientMemberId;
    private final NotificationType type;
    private final UUID referenceId;
    private final String title;
    private final String message;
    private boolean isRead;
    private final Instant createdAt;

    private InAppNotification(
            NotificationId id,
            MemberId recipientMemberId,
            NotificationType type,
            UUID referenceId,
            String title,
            String message,
            boolean isRead,
            Instant createdAt) {
        this.id = id;
        this.recipientMemberId = recipientMemberId;
        this.type = type;
        this.referenceId = referenceId;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public static InAppNotification create(
            MemberId recipientMemberId, NotificationType type, UUID referenceId, String title, String message) {
        return new InAppNotification(
                NotificationId.generate(), recipientMemberId, type, referenceId, title, message, false, Instant.now());
    }

    public static InAppNotification reconstitute(
            NotificationId id,
            MemberId recipientMemberId,
            NotificationType type,
            UUID referenceId,
            String title,
            String message,
            boolean isRead,
            Instant createdAt) {
        return new InAppNotification(id, recipientMemberId, type, referenceId, title, message, isRead, createdAt);
    }

    public void markRead() {
        this.isRead = true;
    }

    public NotificationId getId() {
        return id;
    }

    public MemberId getRecipientMemberId() {
        return recipientMemberId;
    }

    public NotificationType getType() {
        return type;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return isRead;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
