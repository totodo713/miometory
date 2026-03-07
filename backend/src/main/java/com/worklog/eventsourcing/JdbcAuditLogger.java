package com.worklog.eventsourcing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of the AuditLogger interface.
 *
 * Persists audit events to the audit_logs table with JSONB details.
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
    public void log(
            UUID tenantId,
            UUID userId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> details) {
        String mergedDetailsJson = serializeMergedDetails(tenantId, resourceType, resourceId, details);

        jdbcTemplate.update("""
            INSERT INTO audit_logs (user_id, event_type, ip_address, timestamp, details, retention_days)
            VALUES (?, ?, NULL, CURRENT_TIMESTAMP, ?::jsonb, 365)
            """, userId, action, mergedDetailsJson);
    }

    /**
     * Merges tenantId, resourceType, and resourceId into the details JSONB
     * so that context previously stored in dedicated columns is preserved.
     */
    private String serializeMergedDetails(
            UUID tenantId, String resourceType, UUID resourceId, Map<String, Object> details) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (tenantId != null) {
            merged.put("tenant_id", tenantId.toString());
        }
        if (resourceType != null) {
            merged.put("resource_type", resourceType);
        }
        if (resourceId != null) {
            merged.put("resource_id", resourceId.toString());
        }
        if (details != null && !details.isEmpty()) {
            merged.putAll(details);
        }
        if (merged.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit details", e);
        }
    }
}
