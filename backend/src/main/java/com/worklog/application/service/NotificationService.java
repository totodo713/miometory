package com.worklog.application.service;

import com.worklog.domain.notification.NotificationType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createNotification(
            UUID recipientMemberId, NotificationType type, UUID referenceId, String title, String message) {
        jdbcTemplate.update(
                """
                INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, ?, false, ?)
                """,
                UUID.randomUUID(),
                recipientMemberId,
                type.name(),
                referenceId,
                title,
                message,
                Timestamp.from(Instant.now()));
    }

    @Transactional(readOnly = true)
    public NotificationPage listNotifications(UUID recipientMemberId, Boolean isRead, int page, int size) {
        var sb = new StringBuilder(
                "SELECT id, type, reference_id, title, message, is_read, created_at FROM in_app_notifications WHERE recipient_member_id = ?");
        var countSb = new StringBuilder("SELECT COUNT(*) FROM in_app_notifications WHERE recipient_member_id = ?");
        var params = new java.util.ArrayList<Object>();
        params.add(recipientMemberId);
        var countParams = new java.util.ArrayList<Object>();
        countParams.add(recipientMemberId);

        if (isRead != null) {
            sb.append(" AND is_read = ?");
            countSb.append(" AND is_read = ?");
            params.add(isRead);
            countParams.add(isRead);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<NotificationRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new NotificationRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("type"),
                        rs.getObject("reference_id", UUID.class) != null
                                ? rs.getObject("reference_id", UUID.class).toString()
                                : null,
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getBoolean("is_read"),
                        rs.getTimestamp("created_at").toInstant().toString()),
                params.toArray());

        int unreadCount = getUnreadCount(recipientMemberId);
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new NotificationPage(content, totalElements, totalPages, page, unreadCount);
    }

    public void markRead(UUID notificationId, UUID recipientMemberId) {
        jdbcTemplate.update(
                "UPDATE in_app_notifications SET is_read = true WHERE id = ? AND recipient_member_id = ?",
                notificationId,
                recipientMemberId);
    }

    public void markAllRead(UUID recipientMemberId) {
        jdbcTemplate.update(
                "UPDATE in_app_notifications SET is_read = true WHERE recipient_member_id = ? AND is_read = false",
                recipientMemberId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(UUID recipientMemberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM in_app_notifications WHERE recipient_member_id = ? AND is_read = false",
                Integer.class,
                recipientMemberId);
        return count != null ? count : 0;
    }

    public record NotificationRow(
            String id,
            String type,
            String referenceId,
            String title,
            String message,
            boolean isRead,
            String createdAt) {}

    public record NotificationPage(
            List<NotificationRow> content, long totalElements, int totalPages, int number, int unreadCount) {}
}
