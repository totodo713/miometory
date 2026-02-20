package com.worklog.shared;

/**
 * Constants for admin role names matching V18 seed data.
 *
 * These role names correspond to entries in the {@code roles} table
 * seeded by {@code V18__admin_permissions_seed.sql}.
 */
public final class AdminRole {

    private AdminRole() {}

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String SUPERVISOR = "SUPERVISOR";
}
