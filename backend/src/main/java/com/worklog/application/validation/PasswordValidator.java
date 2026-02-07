package com.worklog.application.validation;

import java.util.regex.Pattern;

/**
 * Password validation utility.
 * 
 * Enforces password strength requirements per FR-003:
 * - Minimum 8 characters
 * - At least 1 lowercase letter
 * - At least 1 uppercase letter
 * - At least 1 digit
 */
public class PasswordValidator {
    
    // Password strength pattern: min 8 chars, 1 lowercase, 1 uppercase, 1 digit
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,}$"
    );
    
    /**
     * Validates password strength.
     * 
     * @param password The password to validate
     * @throws IllegalArgumentException if password does not meet requirements
     */
    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                "Password must be at least 8 characters long and contain at least one lowercase letter, one uppercase letter, and one digit"
            );
        }
    }
    
    /**
     * Checks if password meets strength requirements.
     * 
     * @param password The password to check
     * @return true if password is valid, false otherwise
     */
    public static boolean isValid(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
