package com.worklog.domain.approval;

import com.worklog.domain.shared.EntityId;

import java.util.UUID;

/**
 * Strongly-typed identifier for MonthlyApproval aggregates.
 * 
 * Used to uniquely identify monthly approval records for a member's
 * time tracking approval workflow.
 */
public record MonthlyApprovalId(UUID value) implements EntityId {
    
    public MonthlyApprovalId {
        if (value == null) {
            throw new IllegalArgumentException("MonthlyApprovalId value cannot be null");
        }
    }
    
    /**
     * Generates a new random MonthlyApprovalId.
     */
    public static MonthlyApprovalId generate() {
        return new MonthlyApprovalId(UUID.randomUUID());
    }
    
    /**
     * Creates a MonthlyApprovalId from a UUID.
     */
    public static MonthlyApprovalId of(UUID value) {
        return new MonthlyApprovalId(value);
    }
    
    /**
     * Creates a MonthlyApprovalId from a string representation.
     */
    public static MonthlyApprovalId of(String value) {
        return new MonthlyApprovalId(UUID.fromString(value));
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}
