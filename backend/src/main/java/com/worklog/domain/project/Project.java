package com.worklog.domain.project;

import com.worklog.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Project aggregate root.
 *
 * Represents a project that members can log work hours against.
 * Projects have a validity period and can be activated/deactivated.
 */
public class Project {

    private final ProjectId id;
    private final TenantId tenantId;
    private String code; // Business identifier (e.g., "PROJ-123")
    private String name;
    private boolean isActive; // T013: Whether project accepts new entries
    private LocalDate validFrom; // T013: Project start date (nullable)
    private LocalDate validUntil; // T013: Project end date (nullable)
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Constructor for creating a new Project.
     */
    public Project(
            ProjectId id,
            TenantId tenantId,
            String code,
            String name,
            boolean isActive,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Project ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.code = Objects.requireNonNull(code, "Project code cannot be null");
        this.name = Objects.requireNonNull(name, "Project name cannot be null");
        this.isActive = isActive;
        this.validFrom = validFrom; // Can be null
        this.validUntil = validUntil; // Can be null
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.updatedAt = createdAt;

        validateCode(code);
        validateName(name);
        validateValidityPeriod(validFrom, validUntil);
    }

    /**
     * Factory method for creating a new Project.
     */
    public static Project create(
            TenantId tenantId, String code, String name, LocalDate validFrom, LocalDate validUntil) {
        return new Project(
                ProjectId.generate(),
                tenantId,
                code,
                name,
                true, // New projects are active by default
                validFrom,
                validUntil,
                Instant.now());
    }

    /**
     * Updates project information.
     */
    public void update(String code, String name) {
        validateCode(code);
        validateName(name);
        this.code = Objects.requireNonNull(code, "Project code cannot be null");
        this.name = Objects.requireNonNull(name, "Project name cannot be null");
        this.updatedAt = Instant.now();
    }

    /**
     * Sets the validity period for the project.
     */
    public void setValidityPeriod(LocalDate validFrom, LocalDate validUntil) {
        validateValidityPeriod(validFrom, validUntil);
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.updatedAt = Instant.now();
    }

    /**
     * Deactivates the project.
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the project.
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if the project is valid (within validity period) on the given date.
     * Returns true if:
     * - No validity period is set (validFrom and validUntil are both null), OR
     * - Date is on or after validFrom (if set), AND
     * - Date is on or before validUntil (if set)
     */
    public boolean isValidOn(LocalDate date) {
        Objects.requireNonNull(date, "Date cannot be null");

        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }

        if (validUntil != null && date.isAfter(validUntil)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the project is active AND valid on the given date.
     * This is the main business rule check for whether work can be logged to this project.
     */
    public boolean isActiveOn(LocalDate date) {
        return isActive && isValidOn(date);
    }

    /**
     * Validates project code format.
     */
    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Project code cannot be empty");
        }
        if (code.length() > 50) {
            throw new IllegalArgumentException("Project code cannot exceed 50 characters");
        }
    }

    /**
     * Validates project name.
     */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be empty");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Project name cannot exceed 200 characters");
        }
    }

    /**
     * Validates that validFrom <= validUntil if both are set.
     */
    private void validateValidityPeriod(LocalDate validFrom, LocalDate validUntil) {
        if (validFrom != null && validUntil != null) {
            if (validFrom.isAfter(validUntil)) {
                throw new IllegalArgumentException("Project validFrom date (" + validFrom
                        + ") cannot be after validUntil date (" + validUntil + ")");
            }
        }
    }

    // Getters

    public ProjectId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
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
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Project{" + "id="
                + id + ", code='"
                + code + '\'' + ", name='"
                + name + '\'' + ", isActive="
                + isActive + ", validFrom="
                + validFrom + ", validUntil="
                + validUntil + '}';
    }
}
