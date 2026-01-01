package com.worklog.eventsourcing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of the AuditLogger interface.
 * 
 * Persists audit events to the audit_log table with JSONB details.
 */
@Repository
public class JdbcAuditLogger implements AuditLogger {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void log(UUID tenantId, UUID userId, String action, String resourceType, UUID resourceId, Map<String, Object> details) {
        String detailsJson = serializeDetails(details);
        
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (tenant_id, user_id, action, resource_type, resource_id, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """,
            tenantId,
            userId,
            action,
            resourceType,
            resourceId,
            detailsJson
        );
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit details", e);
        }
    }
}
