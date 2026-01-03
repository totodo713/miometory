package com.worklog.domain.absence;

/**
 * Enumeration of absence types.
 * 
 * Represents different categories of non-working time that can be recorded.
 */
public enum AbsenceType {
    /**
     * Paid time off (vacation, holiday)
     */
    PAID_LEAVE,
    
    /**
     * Sick leave (illness, medical appointments)
     */
    SICK_LEAVE,
    
    /**
     * Special leave (bereavement, jury duty, etc.)
     */
    SPECIAL_LEAVE,
    
    /**
     * Other types of absence not covered above
     */
    OTHER;

    /**
     * Returns a human-readable description of the absence type.
     */
    public String getDescription() {
        return switch (this) {
            case PAID_LEAVE -> "Paid Leave";
            case SICK_LEAVE -> "Sick Leave";
            case SPECIAL_LEAVE -> "Special Leave";
            case OTHER -> "Other";
        };
    }
}
