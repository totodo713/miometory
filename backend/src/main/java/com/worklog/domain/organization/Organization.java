package com.worklog.domain.organization;

import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.Code;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import java.util.UUID;

/**
 * Organization aggregate root.
 *
 * An organization represents a hierarchical unit within a tenant (e.g., company, department, team).
 * Organizations can be nested up to 6 levels deep.
 *
 * Invariants:
 * - Level must be between 1 and 6 (FR-006)
 * - Root organizations (level 1) must not have a parent
 * - Non-root organizations must have a parent
 * - Code is immutable after creation
 * - Name cannot be empty
 * - Cannot deactivate an already inactive organization
 * - Cannot activate an already active organization
 */
public class Organization extends AggregateRoot<OrganizationId> {

    private static final int MAX_HIERARCHY_LEVEL = 6;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    private OrganizationId id;
    private TenantId tenantId;
    private OrganizationId parentId;
    private Code code;
    private String name;
    private int level;
    private UUID fiscalYearPatternId;
    private UUID monthlyPeriodPatternId;
    private Status status;

    // Private constructor for factory method
    private Organization() {}

    /**
     * Creates a new root organization (level=1, no parent).
     *
     * @param id Organization identifier
     * @param tenantId Owning tenant
     * @param code Unique code within tenant
     * @param name Display name
     * @return New Organization instance with OrganizationCreated event
     */
    public static Organization create(
            OrganizationId id, TenantId tenantId, OrganizationId parentId, Code code, String name) {
        return create(id, tenantId, parentId, code, name, parentId == null ? 1 : 2);
    }

    /**
     * Creates a new organization with explicit level.
     *
     * @param id Organization identifier
     * @param tenantId Owning tenant
     * @param parentId Parent organization (null for root)
     * @param code Unique code within tenant
     * @param name Display name
     * @param level Hierarchy level (1-6)
     * @return New Organization instance with OrganizationCreated event
     */
    public static Organization create(
            OrganizationId id, TenantId tenantId, OrganizationId parentId, Code code, String name, int level) {
        validateName(name);
        validateLevel(level);
        validateParentConsistency(parentId, level);

        Organization organization = new Organization();

        OrganizationCreated event = OrganizationCreated.create(
                id.value(),
                tenantId.value(),
                parentId != null ? parentId.value() : null,
                code.value(),
                name.trim(),
                level);
        organization.raiseEvent(event);

        return organization;
    }

    /**
     * Updates the organization's name.
     *
     * @param newName New display name
     */
    public void update(String newName) {
        validateName(newName);

        if (this.status == Status.INACTIVE) {
            throw new DomainException("ORGANIZATION_INACTIVE", "Cannot update an inactive organization");
        }

        OrganizationUpdated event = OrganizationUpdated.create(this.id.value(), newName.trim());
        raiseEvent(event);
    }

    /**
     * Deactivates the organization.
     */
    public void deactivate() {
        if (this.status == Status.INACTIVE) {
            throw new DomainException("ORGANIZATION_ALREADY_INACTIVE", "Organization is already inactive");
        }

        OrganizationDeactivated event = OrganizationDeactivated.create(this.id.value());
        raiseEvent(event);
    }

    /**
     * Reactivates an inactive organization.
     */
    public void activate() {
        if (this.status == Status.ACTIVE) {
            throw new DomainException("ORGANIZATION_ALREADY_ACTIVE", "Organization is already active");
        }

        OrganizationActivated event = OrganizationActivated.create(this.id.value());
        raiseEvent(event);
    }

    /**
     * Assigns fiscal year and monthly period patterns to this organization.
     *
     * @param fiscalYearPatternId Fiscal year pattern ID (can be null for inheritance)
     * @param monthlyPeriodPatternId Monthly period pattern ID (can be null for inheritance)
     */
    public void assignPatterns(UUID fiscalYearPatternId, UUID monthlyPeriodPatternId) {
        OrganizationPatternAssigned event =
                OrganizationPatternAssigned.create(this.id.value(), fiscalYearPatternId, monthlyPeriodPatternId);
        raiseEvent(event);
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case OrganizationCreated e -> {
                this.id = OrganizationId.of(e.aggregateId());
                this.tenantId = TenantId.of(e.tenantId());
                this.parentId = e.parentId() != null ? OrganizationId.of(e.parentId()) : null;
                this.code = Code.of(e.code());
                this.name = e.name();
                this.level = e.level();
                this.status = Status.ACTIVE;
            }
            case OrganizationUpdated e -> {
                this.name = e.name();
            }
            case OrganizationDeactivated e -> {
                this.status = Status.INACTIVE;
            }
            case OrganizationActivated e -> {
                this.status = Status.ACTIVE;
            }
            case OrganizationPatternAssigned e -> {
                this.fiscalYearPatternId = e.fiscalYearPatternId();
                this.monthlyPeriodPatternId = e.monthlyPeriodPatternId();
            }
            default ->
                throw new IllegalArgumentException(
                        "Unknown event type: " + event.getClass().getName());
        }
    }

    // Validation methods

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("NAME_REQUIRED", "Name cannot be empty");
        }
        if (name.trim().length() > 256) {
            throw new DomainException("NAME_TOO_LONG", "Name cannot exceed 256 characters");
        }
    }

    private static void validateLevel(int level) {
        if (level < 1 || level > MAX_HIERARCHY_LEVEL) {
            throw new DomainException(
                    "INVALID_LEVEL", "Organization level must be between 1 and " + MAX_HIERARCHY_LEVEL);
        }
    }

    private static void validateParentConsistency(OrganizationId parentId, int level) {
        if (level == 1 && parentId != null) {
            throw new DomainException("ROOT_CANNOT_HAVE_PARENT", "Root organization (level 1) cannot have a parent");
        }
        if (level > 1 && parentId == null) {
            throw new DomainException("NON_ROOT_MUST_HAVE_PARENT", "Non-root organization must have a parent");
        }
    }

    // Getters

    @Override
    public OrganizationId getId() {
        return id;
    }

    @Override
    public String getAggregateType() {
        return "Organization";
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public OrganizationId getParentId() {
        return parentId;
    }

    public Code getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public UUID getFiscalYearPatternId() {
        return fiscalYearPatternId;
    }

    public UUID getMonthlyPeriodPatternId() {
        return monthlyPeriodPatternId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
