package com.worklog.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.audit.AuditLog;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.AuditLogRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting audit log entries in an independent transaction.
 *
 * <p>Uses {@link Propagation#REQUIRES_NEW} to ensure that audit log persistence
 * failures never roll back the calling transaction (e.g., login). Any exception
 * during save is caught and logged, allowing the primary operation to complete
 * successfully regardless of audit log outcome.
 *
 * <p>Uses {@link AuditLogRepository#insertAuditLog} with explicit SQL casting
 * for JSONB and INET columns, since global writing converters would affect
 * all String fields across all entities.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an audit log event in a new, independent transaction.
     *
     * <p>If persistence fails, the exception is caught and logged via SLF4J.
     * The calling transaction is never affected by failures in this method.
     *
     * @param userId    the user who triggered the event (null for system events)
     * @param eventType the type of audit event (e.g., LOGIN_SUCCESS)
     * @param ipAddress the client IP address (null for system events)
     * @param details   additional event details as a JSON string (nullable)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UserId userId, String eventType, String ipAddress, String details) {
        try {
            AuditLog auditLog = AuditLog.createUserAction(userId, eventType, ipAddress, details);
            auditLogRepository.insertAuditLog(
                    auditLog.getId(),
                    userId != null ? userId.value() : null,
                    eventType,
                    ipAddress,
                    auditLog.getTimestamp(),
                    toJsonDetails(details),
                    auditLog.getRetentionDays());
        } catch (Exception e) {
            log.error(
                    "Failed to persist audit log: eventType={}, userId={}, error={}",
                    eventType,
                    userId,
                    e.getMessage(),
                    e);
        }
    }

    private String toJsonDetails(String details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("message", details));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details to JSON, using null", e);
            return null;
        }
    }
}
