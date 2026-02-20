package com.worklog.api;

import com.worklog.application.service.NotificationService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public NotificationController(NotificationService notificationService, JdbcTemplate jdbcTemplate) {
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public NotificationService.NotificationPage listNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID memberId = resolveUserMemberId(authentication.getName());
        return notificationService.listNotifications(memberId, isRead, page, size);
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable UUID id, Authentication authentication) {
        UUID memberId = resolveUserMemberId(authentication.getName());
        notificationService.markRead(id, memberId);
    }

    @PatchMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        UUID memberId = resolveUserMemberId(authentication.getName());
        notificationService.markAllRead(memberId);
    }

    private UUID resolveUserMemberId(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }
}
