package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.Role;
import com.worklog.domain.role.RoleId;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Role aggregate using Spring Data JDBC.
 *
 * Provides CRUD operations and custom queries for role management.
 */
@Repository
public interface RoleRepository extends CrudRepository<Role, RoleId> {

    /**
     * Find a role by name (case-insensitive).
     */
    @Query("SELECT * FROM roles WHERE UPPER(name) = UPPER(:name)")
    Optional<Role> findByName(@Param("name") String name);

    /**
     * Check if a role exists by name.
     */
    @Query("SELECT COUNT(*) > 0 FROM roles WHERE UPPER(name) = UPPER(:name)")
    boolean existsByName(@Param("name") String name);
}
