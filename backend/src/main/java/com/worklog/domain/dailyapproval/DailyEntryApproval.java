package com.worklog.domain.dailyapproval;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import java.time.Instant;
import java.util.UUID;

public class DailyEntryApproval {

    private final DailyEntryApprovalId id;
    private final UUID workLogEntryId;
    private final MemberId memberId;
    private final MemberId supervisorId;
    private DailyApprovalStatus status;
    private String comment;
    private final Instant createdAt;
    private Instant updatedAt;

    private DailyEntryApproval(
            DailyEntryApprovalId id,
            UUID workLogEntryId,
            MemberId memberId,
            MemberId supervisorId,
            DailyApprovalStatus status,
            String comment,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.workLogEntryId = workLogEntryId;
        this.memberId = memberId;
        this.supervisorId = supervisorId;
        this.status = status;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DailyEntryApproval create(
            UUID workLogEntryId, MemberId memberId, MemberId supervisorId, DailyApprovalStatus status, String comment) {
        if (status == DailyApprovalStatus.REJECTED && (comment == null || comment.isBlank())) {
            throw new DomainException("COMMENT_REQUIRED", "Comment is required when rejecting an entry");
        }
        Instant now = Instant.now();
        return new DailyEntryApproval(
                DailyEntryApprovalId.generate(), workLogEntryId, memberId, supervisorId, status, comment, now, now);
    }

    public static DailyEntryApproval reconstitute(
            DailyEntryApprovalId id,
            UUID workLogEntryId,
            MemberId memberId,
            MemberId supervisorId,
            DailyApprovalStatus status,
            String comment,
            Instant createdAt,
            Instant updatedAt) {
        return new DailyEntryApproval(
                id, workLogEntryId, memberId, supervisorId, status, comment, createdAt, updatedAt);
    }

    public void recall() {
        if (this.status != DailyApprovalStatus.APPROVED) {
            throw new DomainException("INVALID_STATUS", "Only APPROVED entries can be recalled");
        }
        this.status = DailyApprovalStatus.RECALLED;
        this.updatedAt = Instant.now();
    }

    public DailyEntryApprovalId getId() {
        return id;
    }

    public UUID getWorkLogEntryId() {
        return workLogEntryId;
    }

    public MemberId getMemberId() {
        return memberId;
    }

    public MemberId getSupervisorId() {
        return supervisorId;
    }

    public DailyApprovalStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
