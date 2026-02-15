package com.worklog.domain.role;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("roles")
public class Role {

    @Id
    private final RoleId id;

    private String name;
    private String description;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Constructor for creating a new Role.
     */
    public Role(RoleId id, String name, String description, Instant createdAt) {
        this(id, name, description, createdAt, createdAt);
    }

    /**
     * Rehydration constructor for restoring a Role from persistence.
     */
    public Role(RoleId id, String name, String description, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Role ID cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp cannot be null");

        validateAndSetName(name);
        this.description = description; // Description can be null
    }

    /**
     * Factory method for creating a new Role.
     */
    public static Role create(String name, String description) {
        return new Role(RoleId.generate(), name, description, Instant.now());
    }

    /**
     * Updates role information.
     */
    public void update(String name, String description) {
        validateAndSetName(name);
        this.description = description;
        this.updatedAt = Instant.now();
    }

    /**
     * Validates and sets role name.
     */
    private void validateAndSetName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be empty");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("Role name cannot exceed 50 characters");
        }
        // Role names should be uppercase by convention (e.g., ADMIN, USER)
        this.name = name.toUpperCase();
    }

    // Getters

    public RoleId getId() {
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Role{" + "id=" + id + ", name='" + name + '\'' + ", description='" + description + '\'' + '}';
    }
}
