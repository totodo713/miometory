package com.worklog.domain.tenant;

import com.worklog.domain.shared.AggregateRoot;
import com.worklog.domain.shared.Code;
import com.worklog.domain.shared.DomainEvent;
import com.worklog.domain.shared.DomainException;
import java.util.UUID;

/**
 * Tenant aggregate root.
 *
 * A tenant represents a top-level organizational boundary in the system.
 * All data (organizations, projects, members, etc.) is scoped to a tenant.
 *
 * Invariants:
 * - Code is immutable after creation
 * - Name cannot be empty
 * - Cannot deactivate an already inactive tenant
 * - Cannot activate an already active tenant
 */
public class Tenant extends AggregateRoot<TenantId> {

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    private TenantId id;
    private Code code;
    private String name;
    private Status status;
    private UUID defaultFiscalYearPatternId;
    private UUID defaultMonthlyPeriodPatternId;

    // Private constructor for factory method
    private Tenant() {}

    /**
     * Creates a new tenant.
     *
     * @param code Unique code for the tenant
     * @param name Display name for the tenant
     * @return New Tenant instance with TenantCreated event
     */
    public static Tenant create(String code, String name) {
        validateName(name);

        Tenant tenant = new Tenant();
        TenantId tenantId = TenantId.generate();
        Code validCode = Code.of(code);

        TenantCreated event = TenantCreated.create(tenantId.value(), validCode.value(), name.trim());
        tenant.raiseEvent(event);

        return tenant;
    }

    /**
     * Creates a tenant with a specific ID (for reconstitution).
     */
    public static Tenant createWithId(TenantId id, String code, String name) {
        validateName(name);

        Tenant tenant = new Tenant();
        Code validCode = Code.of(code);

        TenantCreated event = TenantCreated.create(id.value(), validCode.value(), name.trim());
        tenant.raiseEvent(event);

        return tenant;
    }

    /**
     * Updates the tenant's name.
     *
     * @param newName New display name
     */
    public void update(String newName) {
        validateName(newName);

        if (this.status == Status.INACTIVE) {
            throw new DomainException("TENANT_INACTIVE", "Cannot update an inactive tenant");
        }

        TenantUpdated event = TenantUpdated.create(this.id.value(), newName.trim());
        raiseEvent(event);
    }

    /**
     * Deactivates the tenant.
     *
     * @param reason Optional reason for deactivation
     */
    public void deactivate(String reason) {
        if (this.status == Status.INACTIVE) {
            throw new DomainException("TENANT_ALREADY_INACTIVE", "Tenant is already inactive");
        }

        TenantDeactivated event = TenantDeactivated.create(this.id.value(), reason);
        raiseEvent(event);
    }

    /**
     * Deactivates the tenant without a reason.
     */
    public void deactivate() {
        deactivate(null);
    }

    /**
     * Assigns default patterns for this tenant.
     * Either or both may be null (clearing the tenant-level default).
     */
    public void assignDefaultPatterns(UUID fiscalYearPatternId, UUID monthlyPeriodPatternId) {
        if (this.status == Status.INACTIVE) {
            throw new DomainException("TENANT_INACTIVE", "Cannot update an inactive tenant");
        }

        TenantDefaultPatternsAssigned event =
                TenantDefaultPatternsAssigned.create(this.id.value(), fiscalYearPatternId, monthlyPeriodPatternId);
        raiseEvent(event);
    }

    /**
     * Reactivates an inactive tenant.
     */
    public void activate() {
        if (this.status == Status.ACTIVE) {
            throw new DomainException("TENANT_ALREADY_ACTIVE", "Tenant is already active");
        }

        TenantActivated event = TenantActivated.create(this.id.value());
        raiseEvent(event);
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case TenantCreated e -> {
                this.id = TenantId.of(e.aggregateId());
                this.code = Code.of(e.code());
                this.name = e.name();
                this.status = Status.ACTIVE;
            }
            case TenantUpdated e -> {
                this.name = e.name();
            }
            case TenantDeactivated e -> {
                this.status = Status.INACTIVE;
            }
            case TenantActivated e -> {
                this.status = Status.ACTIVE;
            }
            case TenantDefaultPatternsAssigned e -> {
                this.defaultFiscalYearPatternId = e.defaultFiscalYearPatternId();
                this.defaultMonthlyPeriodPatternId = e.defaultMonthlyPeriodPatternId();
            }
            default ->
                throw new IllegalArgumentException(
                        "Unknown event type: " + event.getClass().getName());
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("NAME_REQUIRED", "Name cannot be null or blank");
        }
        if (name.trim().length() > 256) {
            throw new DomainException("NAME_TOO_LONG", "Name cannot exceed 256 characters");
        }
    }

    // Getters

    @Override
    public TenantId getId() {
        return id;
    }

    @Override
    public String getAggregateType() {
        return "Tenant";
    }

    public Code getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public UUID getDefaultFiscalYearPatternId() {
        return defaultFiscalYearPatternId;
    }

    public UUID getDefaultMonthlyPeriodPatternId() {
        return defaultMonthlyPeriodPatternId;
    }
}
