package com.worklog.api;

import com.worklog.application.service.NotificationService;
import com.worklog.application.service.UserContextService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserContextService userContextService;

    public NotificationController(NotificationService notificationService, UserContextService userContextService) {
        this.notificationService = notificationService;
        this.userContextService = userContextService;
    }

    @GetMapping
    public NotificationService.NotificationPage listNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        if (memberId == null) {
            return new NotificationService.NotificationPage(List.of(), 0, 0, 0, 0);
        }
        int effectiveSize = Math.min(size, 100);
        return notificationService.listNotifications(memberId, isRead, page, effectiveSize);
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable UUID id, Authentication authentication) {
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        if (memberId == null) {
            return;
        }
        notificationService.markRead(id, memberId);
    }

    @PatchMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        if (memberId == null) {
            return;
        }
        notificationService.markAllRead(memberId);
    }
}
