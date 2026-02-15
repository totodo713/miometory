package com.worklog.infrastructure.persistence;

import com.worklog.domain.permission.Permission;
import com.worklog.domain.permission.PermissionId;
import com.worklog.domain.role.RoleId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Permission aggregate using Spring Data JDBC.
 *
 * Provides CRUD operations and custom queries for permission management.
 */
@Repository
public interface PermissionRepository extends CrudRepository<Permission, UUID> {

    /**
     * Find a permission by name.
     */
    @Query("SELECT * FROM permissions WHERE name = :name")
    Optional<Permission> findByName(@Param("name") String name);

    /**
     * Find a permission by PermissionId.
     */
    default Optional<Permission> findById(PermissionId permissionId) {
        return findById(permissionId.value());
    }

    /**
     * Find all permissions for a specific role.
     */
    @Query("""
        SELECT p.* FROM permissions p
        INNER JOIN role_permissions rp ON p.id = rp.permission_id
        WHERE rp.role_id = :roleId
        """)
    List<Permission> findByRoleId(@Param("roleId") UUID roleId);

    /**
     * Find all permissions for a specific role using RoleId.
     */
    default List<Permission> findByRole(RoleId roleId) {
        return findByRoleId(roleId.value());
    }

    /**
     * Check if a permission exists by name.
     */
    @Query("SELECT COUNT(*) > 0 FROM permissions WHERE name = :name")
    boolean existsByName(@Param("name") String name);

    /**
     * Delete a permission by PermissionId.
     */
    default void deleteById(PermissionId permissionId) {
        deleteById(permissionId.value());
    }
}
