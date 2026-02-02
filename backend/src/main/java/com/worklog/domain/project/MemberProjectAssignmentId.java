package com.worklog.domain.project;

import com.worklog.domain.shared.EntityId;

import java.util.UUID;

/**
 * Strongly-typed identifier for MemberProjectAssignment entities.
 * 
 * Represents the unique identifier for a member-to-project assignment.
 */
public record MemberProjectAssignmentId(UUID value) implements EntityId {
    
    public MemberProjectAssignmentId {
        if (value == null) {
            throw new IllegalArgumentException("MemberProjectAssignmentId value cannot be null");
        }
    }
    
    /**
     * Generates a new random MemberProjectAssignmentId.
     */
    public static MemberProjectAssignmentId generate() {
        return new MemberProjectAssignmentId(UUID.randomUUID());
    }
    
    /**
     * Creates a MemberProjectAssignmentId from a UUID.
     */
    public static MemberProjectAssignmentId of(UUID value) {
        return new MemberProjectAssignmentId(value);
    }
    
    /**
     * Creates a MemberProjectAssignmentId from a string representation.
     */
    public static MemberProjectAssignmentId of(String value) {
        return new MemberProjectAssignmentId(UUID.fromString(value));
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
