package com.worklog.domain.audit;

import com.worklog.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * AuditLog entity.
 * 
 * Immutable audit trail for security and compliance.
 * Records user actions and system events for accountability.
 */
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Id;

@Table("audit_logs")
public class AuditLog {
    
    // Event type constants
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET_COMPLETE = "PASSWORD_RESET_COMPLETE";
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String EMAIL_SEND_FAILURE = "EMAIL_SEND_FAILURE";
    public static final String AUDIT_LOG_CLEANUP = "AUDIT_LOG_CLEANUP";
    
    @Id
    private final UUID id;
    private final UserId userId;  // Can be null for system events
    private final String eventType;
    private final String ipAddress;  // Can be null
    private final Instant timestamp;
    private final String details;  // JSON string, can be null
    private final int retentionDays;
    
    /**
     * Constructor for creating a new AuditLog.
     * Audit logs are immutable once created.
     */
    public AuditLog(
            UUID id,
            UserId userId,
            String eventType,
            String ipAddress,
            Instant timestamp,
            String details,
            int retentionDays
    ) {
        this.id = Objects.requireNonNull(id, "Audit log ID cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be empty");
        }
        if (eventType.length() > 50) {
            throw new IllegalArgumentException("Event type cannot exceed 50 characters");
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Retention days must be at least 1");
        }
        
        this.userId = userId;  // Can be null for system events
        this.ipAddress = ipAddress;  // Can be null
        this.details = details;  // Can be null
        this.retentionDays = retentionDays;
    }
    
    /**
     * Factory method for creating a new user action audit log.
     */
    public static AuditLog createUserAction(
            UserId userId,
            String eventType,
            String ipAddress,
            String details
    ) {
        return new AuditLog(
            UUID.randomUUID(),
            userId,
            eventType,
            ipAddress,
            Instant.now(),
            details,
            90  // Default retention: 90 days
        );
    }
    
    /**
     * Factory method for creating a new system event audit log.
     */
    public static AuditLog createSystemEvent(
            String eventType,
            String details
    ) {
        return new AuditLog(
            UUID.randomUUID(),
            null,  // No user for system events
            eventType,
            null,  // No IP address for system events
            Instant.now(),
            details,
            90  // Default retention: 90 days
        );
    }
    
    /**
     * Checks if this audit log should be retained based on current date.
     * 
     * @return true if the log should be kept, false if it can be deleted
     */
    public boolean shouldRetain() {
        Instant expirationDate = timestamp.plusSeconds(retentionDays * 24L * 60L * 60L);
        return Instant.now().isBefore(expirationDate);
    }
    
    /**
     * Checks if this is a user event (has userId).
     */
    public boolean isUserEvent() {
        return userId != null;
    }
    
    /**
     * Checks if this is a system event (no userId).
     */
    public boolean isSystemEvent() {
        return userId == null;
    }
    
    // Getters
    
    public UUID getId() {
        return id;
    }
    
    public UserId getUserId() {
        return userId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getDetails() {
        return details;
    }
    
    public int getRetentionDays() {
        return retentionDays;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", userId=" + userId +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
