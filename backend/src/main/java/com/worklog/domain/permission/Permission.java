package com.worklog.domain.permission;

import java.time.Instant;
import java.util.Objects;

/**
 * Permission aggregate root.
 *
 * Represents a specific permission/action in the system.
 * Examples: user.create, user.delete, report.view, admin.access
 */
public class Permission {

    private final PermissionId id;
    private String name;
    private String description;
    private final Instant createdAt;

    /**
     * Constructor for creating a new Permission.
     */
    public Permission(PermissionId id, String name, String description, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Permission ID cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");

        validateAndSetName(name);
        this.description = description; // Description can be null
    }

    /**
     * Factory method for creating a new Permission.
     */
    public static Permission create(String name, String description) {
        return new Permission(PermissionId.generate(), name, description, Instant.now());
    }

    /**
     * Validates and sets permission name.
     * Permission names follow the pattern: resource.action (e.g., user.create, report.view)
     */
    private void validateAndSetName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Permission name cannot be empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Permission name cannot exceed 100 characters");
        }
        if (!name.matches("^[a-z_]+\\.[a-z_]+$")) {
            throw new IllegalArgumentException(
                    "Permission name must follow pattern 'resource.action' (lowercase, underscore allowed)");
        }
        this.name = name;
    }

    // Getters

    public PermissionId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Permission{" + "id=" + id + ", name='" + name + '\'' + ", description='" + description + '\'' + '}';
    }
}
