package com.worklog.domain.project;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * MemberProjectAssignment entity.
 *
 * Represents the assignment of a member to a project, allowing them
 * to log work hours against that project.
 */
public class MemberProjectAssignment {

    private final MemberProjectAssignmentId id;
    private final TenantId tenantId;
    private final MemberId memberId;
    private final ProjectId projectId;
    private final Instant assignedAt;
    private final MemberId assignedBy; // Can be null
    private boolean isActive;

    /**
     * Constructor for reconstituting from persistence.
     */
    public MemberProjectAssignment(
            MemberProjectAssignmentId id,
            TenantId tenantId,
            MemberId memberId,
            ProjectId projectId,
            Instant assignedAt,
            MemberId assignedBy,
            boolean isActive) {
        this.id = Objects.requireNonNull(id, "Assignment ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.memberId = Objects.requireNonNull(memberId, "Member ID cannot be null");
        this.projectId = Objects.requireNonNull(projectId, "Project ID cannot be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "Assigned timestamp cannot be null");
        this.assignedBy = assignedBy; // Can be null
        this.isActive = isActive;
    }

    /**
     * Factory method for creating a new assignment.
     */
    public static MemberProjectAssignment create(
            TenantId tenantId, MemberId memberId, ProjectId projectId, MemberId assignedBy) {
        return new MemberProjectAssignment(
                MemberProjectAssignmentId.generate(),
                tenantId,
                memberId,
                projectId,
                Instant.now(),
                assignedBy,
                true // New assignments are active by default
                );
    }

    /**
     * Deactivates this assignment.
     * The member will no longer be able to create new entries for this project.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Activates this assignment.
     * The member will be able to create entries for this project again.
     */
    public void activate() {
        this.isActive = true;
    }

    // Getters

    public MemberProjectAssignmentId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public MemberId getMemberId() {
        return memberId;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public MemberId getAssignedBy() {
        return assignedBy;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemberProjectAssignment that = (MemberProjectAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemberProjectAssignment{" + "id="
                + id + ", memberId="
                + memberId + ", projectId="
                + projectId + ", isActive="
                + isActive + '}';
    }
}
