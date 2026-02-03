package com.worklog.infrastructure.persistence;

import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User aggregate using Spring Data JDBC.
 * 
 * Provides CRUD operations and custom queries for user management.
 */
@Repository
public interface UserRepository extends CrudRepository<User, UUID> {
    
    /**
     * Find a user by email (case-insensitive).
     */
    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * Find a user by UserId.
     */
    default Optional<User> findById(UserId userId) {
        return findById(userId.value());
    }
    
    /**
     * Check if a user exists by email.
     */
    @Query("SELECT COUNT(*) > 0 FROM users WHERE LOWER(email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * Find all users with a specific account status.
     */
    @Query("SELECT * FROM users WHERE account_status = :status")
    List<User> findByAccountStatus(@Param("status") String status);
    
    /**
     * Find all locked users whose lock has expired.
     */
    @Query("SELECT * FROM users WHERE account_status = 'LOCKED' AND locked_until < :now")
    List<User> findExpiredLockedUsers(@Param("now") Instant now);
    
    /**
     * Find all unverified users created before a certain date.
     */
    @Query("SELECT * FROM users WHERE account_status = 'UNVERIFIED' AND created_at < :before")
    List<User> findUnverifiedUsersBefore(@Param("before") Instant before);
    
    /**
     * Delete a user by UserId.
     */
    default void deleteById(UserId userId) {
        deleteById(userId.value());
    }
    
    /**
     * Save a user.
     */
    default User save(User user) {
        return save(user);
    }
}
