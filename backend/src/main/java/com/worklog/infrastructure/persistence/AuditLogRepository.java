package com.worklog.infrastructure.persistence;

import com.worklog.domain.audit.AuditLog;
import com.worklog.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AuditLog entity using Spring Data JDBC.
 *
 * <p>Provides operations for audit trail management and compliance.
 * Uses explicit SQL casting for JSONB and INET columns since global
 * writing converters would affect all String fields across entities.
 */
@Repository
public interface AuditLogRepository extends CrudRepository<AuditLog, UUID> {

    /**
     * Inserts a new audit log entry with explicit PostgreSQL type casting
     * for JSONB (details) and INET (ip_address) columns.
     *
     * <p>This method must be used instead of {@link CrudRepository#save} because
     * Spring Data JDBC cannot automatically map Java String to PostgreSQL JSONB/INET types.
     */
    @Modifying
    @Query("""
        INSERT INTO audit_logs (id, user_id, event_type, ip_address, timestamp, details, retention_days)
        VALUES (:id, :userId, :eventType, CAST(:ipAddress AS inet), :timestamp, CAST(:details AS jsonb), :retentionDays)
        """)
    void insertAuditLog(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("ipAddress") String ipAddress,
            @Param("timestamp") Instant timestamp,
            @Param("details") String details,
            @Param("retentionDays") int retentionDays);

    /**
     * Find audit logs by user ID.
     */
    @Query("SELECT * FROM audit_logs WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    List<AuditLog> findByUserIdOrderByTimestampDesc(@Param("userId") UserId userId, @Param("limit") int limit);

    /**
     * Find audit logs by user using UserId.
     */

    /**
     * Find audit logs by event type.
     */
    @Query("SELECT * FROM audit_logs WHERE event_type = :eventType ORDER BY timestamp DESC LIMIT :limit")
    List<AuditLog> findByEventType(@Param("eventType") String eventType, @Param("limit") int limit);

    /**
     * Find audit logs within a time range.
     */
    @Query("SELECT * FROM audit_logs WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<AuditLog> findByTimestampBetween(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Find audit logs by user and event type.
     */
    @Query("""
        SELECT * FROM audit_logs
        WHERE user_id = :userId AND event_type = :eventType
        ORDER BY timestamp DESC LIMIT :limit
        """)
    List<AuditLog> findByUserIdAndEventType(
            @Param("userId") UserId userId, @Param("eventType") String eventType, @Param("limit") int limit);

    /**
     * Find expired audit logs that should be deleted.
     */
    @Query("""
        SELECT * FROM audit_logs
        WHERE timestamp < :expirationDate
        """)
    List<AuditLog> findExpiredLogs(@Param("expirationDate") Instant expirationDate);

    /**
     * Delete expired audit logs (for cleanup job).
     * Returns the number of deleted rows.
     */
    @Modifying
    @Query("""
        DELETE FROM audit_logs
        WHERE timestamp + (COALESCE(retention_days, 90) || ' days')::INTERVAL < :now
        """)
    int deleteExpiredLogs(@Param("now") Instant now);

    /**
     * Count audit logs by event type within a time range.
     */
    @Query("""
        SELECT COUNT(*) FROM audit_logs
        WHERE event_type = :eventType
        AND timestamp BETWEEN :start AND :end
        """)
    long countByEventTypeAndTimestampBetween(
            @Param("eventType") String eventType, @Param("start") Instant start, @Param("end") Instant end);
}
