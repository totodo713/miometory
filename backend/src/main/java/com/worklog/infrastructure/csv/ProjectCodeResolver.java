package com.worklog.infrastructure.csv;

import com.worklog.domain.project.ProjectId;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary project code resolver.
 * 
 * TODO: Replace with proper ProjectRepository.findByCodeAndTenantId() once project management is implemented.
 * 
 * This is a temporary in-memory mapping for CSV import/export functionality.
 * In production, this should query the projects table.
 */
public class ProjectCodeResolver {
    
    private static final Map<String, UUID> CODE_TO_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ID_TO_CODE_CACHE = new ConcurrentHashMap<>();
    
    static {
        // Initialize with some test projects
        registerProject("PRJ-001", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        registerProject("PRJ-002", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        registerProject("PRJ-003", UUID.fromString("00000000-0000-0000-0000-000000000003"));
    }
    
    /**
     * Register a project code to ID mapping (for testing).
     */
    public static void registerProject(String code, UUID id) {
        CODE_TO_ID_CACHE.put(code, id);
        ID_TO_CODE_CACHE.put(id, code);
    }
    
    /**
     * Resolve project code to ProjectId.
     * 
     * @param code Project code (e.g., "PRJ-001")
     * @return ProjectId if found
     * @throws IllegalArgumentException if project code not found
     */
    public static ProjectId resolveCodeToId(String code) {
        UUID id = CODE_TO_ID_CACHE.get(code);
        if (id == null) {
            throw new IllegalArgumentException("Unknown project code: " + code);
        }
        return new ProjectId(id);
    }
    
    /**
     * Resolve ProjectId to project code.
     * If the project ID is not found in the cache, returns the UUID as a fallback
     * to prevent export failures. This is graceful degradation for data integrity.
     * 
     * @param projectId ProjectId
     * @return Project code if found, otherwise the project ID UUID string as fallback
     */
    public static String resolveIdToCode(ProjectId projectId) {
        String code = ID_TO_CODE_CACHE.get(projectId.value());
        if (code == null) {
            // Graceful fallback: use UUID string instead of throwing
            // This ensures exports don't fail for projects not in cache
            // TODO: When project management is fully implemented, resolve from database
            return projectId.value().toString();
        }
        return code;
    }
    
    /**
     * Check if a project code exists.
     */
    public static boolean exists(String code) {
        return CODE_TO_ID_CACHE.containsKey(code);
    }
}
