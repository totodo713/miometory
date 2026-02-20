package com.worklog.api;

import com.worklog.application.command.ApproveDailyEntryCommand;
import com.worklog.application.command.RecallDailyApprovalCommand;
import com.worklog.application.command.RejectDailyEntryCommand;
import com.worklog.application.service.DailyApprovalService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/worklog/daily-approvals")
public class DailyApprovalController {

    private final DailyApprovalService dailyApprovalService;
    private final JdbcTemplate jdbcTemplate;

    public DailyApprovalController(DailyApprovalService dailyApprovalService, JdbcTemplate jdbcTemplate) {
        this.dailyApprovalService = dailyApprovalService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'daily_approval.view')")
    public List<DailyApprovalService.DailyGroupResponse> getDailyEntries(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) UUID memberId,
            Authentication authentication) {
        UUID supervisorMemberId = resolveUserMemberId(authentication.getName());
        return dailyApprovalService.getDailyEntries(supervisorMemberId, dateFrom, dateTo, memberId);
    }

    @PostMapping("/approve")
    @PreAuthorize("hasPermission(null, 'daily_approval.approve')")
    public void approveEntries(@RequestBody ApproveRequest request, Authentication authentication) {
        UUID supervisorMemberId = resolveUserMemberId(authentication.getName());
        var command = new ApproveDailyEntryCommand(request.entryIds(), supervisorMemberId, request.comment());
        dailyApprovalService.approveEntries(command);
    }

    @PostMapping("/reject")
    @PreAuthorize("hasPermission(null, 'daily_approval.reject')")
    public void rejectEntry(@RequestBody RejectRequest request, Authentication authentication) {
        UUID supervisorMemberId = resolveUserMemberId(authentication.getName());
        var command = new RejectDailyEntryCommand(request.entryId(), supervisorMemberId, request.comment());
        dailyApprovalService.rejectEntry(command);
    }

    @PostMapping("/{approvalId}/recall")
    @PreAuthorize("hasPermission(null, 'daily_approval.recall')")
    public void recallApproval(@PathVariable UUID approvalId, Authentication authentication) {
        UUID supervisorMemberId = resolveUserMemberId(authentication.getName());
        var command = new RecallDailyApprovalCommand(approvalId, supervisorMemberId);
        dailyApprovalService.recallApproval(command);
    }

    private UUID resolveUserMemberId(String email) {
        String sql = "SELECT m.id FROM members m WHERE LOWER(m.email) = LOWER(?) LIMIT 1";
        return jdbcTemplate.queryForObject(sql, UUID.class, email);
    }

    public record ApproveRequest(List<UUID> entryIds, String comment) {}

    public record RejectRequest(UUID entryId, String comment) {}
}
