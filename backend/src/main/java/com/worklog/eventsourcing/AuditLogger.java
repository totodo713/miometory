package com.worklog.eventsourcing;

import java.util.Map;
import java.util.UUID;

/**
 * Logs audit events for compliance and debugging purposes.
 *
 * The audit logger captures important actions performed on resources,
 * providing a trail of who did what and when.
 */
public interface AuditLogger {

    /**
     * Logs an audit event.
     *
     * @param tenantId The ID of the tenant in which the action occurred
     * @param userId The ID of the user who performed the action (nullable for system actions)
     * @param action The action that was performed (e.g., "CREATE", "UPDATE", "DELETE")
     * @param resourceType The type of resource affected (e.g., "Tenant", "Organization")
     * @param resourceId The ID of the affected resource
     * @param details Additional details about the action (will be serialized to JSON)
     */
    void log(
            UUID tenantId,
            UUID userId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> details);

    /**
     * Logs a system-level audit event (no user context).
     *
     * @param action The action that was performed
     * @param resourceType The type of resource affected
     * @param resourceId The ID of the affected resource
     * @param details Additional details about the action
     */
    default void logSystemAction(String action, String resourceType, UUID resourceId, Map<String, Object> details) {
        log(null, null, action, resourceType, resourceId, details);
    }
}
