package com.worklog.domain.member;

import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Member aggregate root.
 *
 * Represents an employee/user in the organization who can log work hours.
 * Members belong to an organization and may have a manager for approval workflows.
 */
public class Member {

    private final MemberId id;
    private final TenantId tenantId;
    private OrganizationId organizationId;
    private String email;
    private String displayName;
    private MemberId managerId; // T012: Manager ID for proxy entry permission
    private BigDecimal standardDailyHours;
    private LocalTime defaultStartTime;
    private LocalTime defaultEndTime;
    private boolean isActive;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Constructor for creating a new Member.
     */
    public Member(
            MemberId id,
            TenantId tenantId,
            OrganizationId organizationId,
            String email,
            String displayName,
            MemberId managerId,
            boolean isActive,
            Instant createdAt) {
        this(id, tenantId, organizationId, email, displayName, managerId, isActive, createdAt, createdAt);
    }

    /**
     * Rehydration constructor for restoring a Member from persistence.
     * Use this when loading from database where both timestamps are known.
     */
    public Member(
            MemberId id,
            TenantId tenantId,
            OrganizationId organizationId,
            String email,
            String displayName,
            MemberId managerId,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt) {
        this(id, tenantId, organizationId, email, displayName, managerId, null, isActive, createdAt, updatedAt);
    }

    /**
     * Full rehydration constructor including standard daily hours.
     * Use this when loading from database where all fields including standard daily hours are known.
     */
    public Member(
            MemberId id,
            TenantId tenantId,
            OrganizationId organizationId,
            String email,
            String displayName,
            MemberId managerId,
            BigDecimal standardDailyHours,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                tenantId,
                organizationId,
                email,
                displayName,
                managerId,
                standardDailyHours,
                null,
                null,
                isActive,
                createdAt,
                updatedAt);
    }

    /**
     * Full rehydration constructor including standard daily hours and default attendance times.
     * Use this when loading from database where all fields are known.
     */
    public Member(
            MemberId id,
            TenantId tenantId,
            OrganizationId organizationId,
            String email,
            String displayName,
            MemberId managerId,
            BigDecimal standardDailyHours,
            LocalTime defaultStartTime,
            LocalTime defaultEndTime,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Member ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.organizationId = organizationId; // Nullable: null = not yet assigned to organization
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        this.managerId = managerId; // Can be null if no manager
        this.standardDailyHours = standardDailyHours; // Nullable: null = inherit from parent level
        this.defaultStartTime = defaultStartTime; // Nullable: null = inherit from parent level
        this.defaultEndTime = defaultEndTime; // Nullable: null = inherit from parent level
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp cannot be null");

        validateEmail(email);
    }

    /**
     * Factory method for creating a new Member.
     */
    public static Member create(
            TenantId tenantId, OrganizationId organizationId, String email, String displayName, MemberId managerId) {
        return new Member(
                MemberId.generate(),
                tenantId,
                organizationId,
                email,
                displayName,
                managerId,
                true, // New members are active by default
                Instant.now());
    }

    /**
     * Factory method for creating a new Member without an organization assignment.
     * Used during public registration where organization assignment happens later.
     */
    public static Member createForTenant(TenantId tenantId, String email, String displayName) {
        return new Member(
                MemberId.generate(),
                tenantId,
                null, // No organization assigned yet
                email.toLowerCase(java.util.Locale.ROOT),
                displayName,
                null, // No manager
                true, // New members are active by default
                Instant.now());
    }

    /**
     * Updates the member's standard daily working hours.
     * NULL means "inherit from parent level" in the resolution chain.
     *
     * @param standardDailyHours The standard daily hours, or null to inherit
     */
    public void updateStandardDailyHours(BigDecimal standardDailyHours) {
        this.standardDailyHours = standardDailyHours;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the member's default attendance times.
     * NULL means "inherit from parent level" in the resolution chain.
     */
    public void updateDefaultAttendanceTimes(LocalTime defaultStartTime, LocalTime defaultEndTime) {
        this.defaultStartTime = defaultStartTime;
        this.defaultEndTime = defaultEndTime;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates member information.
     */
    public void update(String email, String displayName, MemberId managerId) {
        validateEmail(email);
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        this.managerId = managerId;
        this.updatedAt = Instant.now();
    }

    /**
     * Assigns or updates the manager for this member.
     */
    public void assignManager(MemberId managerId) {
        this.managerId = managerId;
        this.updatedAt = Instant.now();
    }

    /**
     * Removes the manager assignment from this member.
     */
    public void removeManager() {
        this.managerId = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Changes the organization this member belongs to.
     * Removes the manager assignment since managers are organization-specific,
     * and a transfer to a different organization invalidates the current manager relationship.
     *
     * @param newOrganizationId the target organization
     */
    public void changeOrganization(OrganizationId newOrganizationId) {
        this.organizationId = Objects.requireNonNull(newOrganizationId, "Organization ID cannot be null");
        removeManager();
    }

    /**
     * Deactivates the member.
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the member.
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if this member has a manager.
     */
    public boolean hasManager() {
        return managerId != null;
    }

    /**
     * Checks if this member has been assigned to an organization.
     */
    public boolean hasOrganization() {
        return organizationId != null;
    }

    /**
     * Checks if the given member is the manager of this member.
     */
    public boolean isManagedBy(MemberId otherMemberId) {
        return managerId != null && managerId.equals(otherMemberId);
    }

    /**
     * Validates email format.
     */
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    // Getters

    public MemberId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public OrganizationId getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MemberId getManagerId() {
        return managerId;
    }

    public BigDecimal getStandardDailyHours() {
        return standardDailyHours;
    }

    public LocalTime getDefaultStartTime() {
        return defaultStartTime;
    }

    public LocalTime getDefaultEndTime() {
        return defaultEndTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return Objects.equals(id, member.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Member{" + "id="
                + id + ", email='"
                + email + '\'' + ", displayName='"
                + displayName + '\'' + ", managerId="
                + managerId + ", isActive="
                + isActive + '}';
    }
}
