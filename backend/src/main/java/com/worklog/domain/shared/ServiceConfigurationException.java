package com.worklog.domain.shared;

/**
 * Exception thrown when a required service configuration is missing or invalid.
 * For example, when a default role required for user signup is not found in the database.
 */
public class ServiceConfigurationException extends RuntimeException {

    public ServiceConfigurationException(String message) {
        super(message);
    }
}
