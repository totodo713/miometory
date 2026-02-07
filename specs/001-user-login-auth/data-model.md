# Data Model: ユーザーログイン認証・認可システム

**Feature Branch**: `001-user-login-auth`  
**Date**: 2026-02-03  
**Status**: Phase 1 Complete  
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md)

## Summary

This document defines the complete data model for user authentication and authorization, including entity schemas, validation rules, state transitions, and database indices. All entities align with functional requirements (FR-001 to FR-020) and technical decisions from research.md.

---

## Entity Definitions

### 1. User (ユーザー)

**Description**: System user with authentication credentials and account metadata.

**Table**: `users`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique user identifier |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | User's email address (login identifier) |
| `hashed_password` | VARCHAR(255) | NOT NULL | bcrypt-hashed password |
| `name` | VARCHAR(255) | NOT NULL | User's full name |
| `role_id` | UUID | NOT NULL, FOREIGN KEY → roles(id) | Reference to user's role |
| `account_status` | VARCHAR(50) | NOT NULL, DEFAULT 'unverified' | Account state (see state transitions) |
| `failed_login_attempts` | INTEGER | NOT NULL, DEFAULT 0 | Counter for login failures (resets on success) |
| `locked_until` | TIMESTAMP | NULL | Temporary lock expiration (NULL if not locked) |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Account creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last update timestamp |
| `last_login_at` | TIMESTAMP | NULL | Last successful login timestamp |
| `email_verified_at` | TIMESTAMP | NULL | Email verification timestamp (NULL if unverified) |

**Indices**:
```sql
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_created_at ON users(created_at);
```

**Validation Rules**:
- **FR-002**: Email format validation: `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`
- **FR-003**: Password strength (before hashing):
  - Minimum 8 characters
  - At least 1 digit (`[0-9]`)
  - At least 1 uppercase letter (`[A-Z]`)
  - Regex: `^(?=.*[0-9])(?=.*[A-Z]).{8,}$`
- **FR-004**: Password stored as bcrypt hash (60-character string)
- `name`: 1-255 characters, no leading/trailing whitespace
- `failed_login_attempts`: 0-10 (reset to 0 on successful login)
- `locked_until`: Must be future timestamp if account_status = 'locked'

**State Transitions**:
```
[unverified] ──email_verification──> [active]
     │
     └──────────────────────────────> [active] (on first login if FR-017 allows restricted access)

[active] ──5_failed_logins──> [locked]
     │
     └──password_change──> [active] (invalidate other sessions)

[locked] ──15_minutes_elapsed──> [active] (auto-unlock via locked_until expiration)
     │
     └──admin_unlock──> [active]

[active/locked/unverified] ──admin_action──> [deleted] (soft delete: set account_status = 'deleted')
```

**Business Rules**:
- FR-006: After 5 failed login attempts, `account_status` → 'locked', `locked_until` → NOW() + 15 minutes
- FR-016: New accounts created with `account_status` = 'unverified', `email_verified_at` = NULL
- FR-017: Unverified accounts can login but have restricted functionality
- FR-018: Email verification sets `email_verified_at` → NOW(), `account_status` → 'active'
- Password change: Invalidate all sessions except current (delete from `user_sessions` and `persistent_logins`)

---

### 2. Role (ロール)

**Description**: User role grouping permissions (e.g., ADMIN, USER, MODERATOR).

**Table**: `roles`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique role identifier |
| `name` | VARCHAR(100) | NOT NULL, UNIQUE | Role name (e.g., 'ADMIN', 'USER') |
| `description` | TEXT | NULL | Human-readable role description |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Role creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last update timestamp |

**Indices**:
```sql
CREATE UNIQUE INDEX idx_roles_name ON roles(name);
```

**Validation Rules**:
- `name`: 1-100 characters, uppercase alphanumeric + underscore only, no spaces
  - Valid: `ADMIN`, `CONTENT_MODERATOR`, `REPORT_VIEWER`
  - Invalid: `admin` (not uppercase), `Content Moderator` (spaces), `Admin!` (special chars)
- `description`: Optional, max 1000 characters

**Seed Data**:
```sql
INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'System administrator with full access'),
    ('00000000-0000-0000-0000-000000000002', 'USER', 'Standard user with limited access'),
    ('00000000-0000-0000-0000-000000000003', 'MODERATOR', 'Content moderator with approval permissions')
ON CONFLICT (id) DO NOTHING;
```

---

### 3. Permission (権限)

**Description**: Fine-grained function-level permission (e.g., `user.create`, `report.view`).

**Table**: `permissions`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique permission identifier |
| `name` | VARCHAR(100) | NOT NULL, UNIQUE | Permission name (dot-separated format) |
| `description` | TEXT | NULL | Human-readable permission description |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Permission creation timestamp |

**Indices**:
```sql
CREATE UNIQUE INDEX idx_permissions_name ON permissions(name);
```

**Validation Rules**:
- **FR-019**: `name` format: `<resource>.<action>` (e.g., `user.create`, `report.export`)
  - Resource: lowercase alphanumeric + underscore
  - Action: lowercase alphanumeric
  - Regex: `^[a-z_]+\.[a-z]+$`
  - Valid: `user.create`, `work_log.edit`, `report.view`
  - Invalid: `User.Create` (uppercase), `user-create` (hyphen), `user` (no action)
- `description`: Optional, max 500 characters

**Seed Data**:
```sql
INSERT INTO permissions (name, description) VALUES
    ('user.create', 'Create new users'),
    ('user.edit', 'Edit existing user details'),
    ('user.delete', 'Delete users'),
    ('user.view', 'View user details'),
    ('user.assign_role', 'Assign roles to users'),
    ('role.create', 'Create new roles'),
    ('role.edit', 'Edit existing roles'),
    ('role.delete', 'Delete roles'),
    ('role.view', 'View role details'),
    ('report.view', 'View reports'),
    ('report.export', 'Export reports'),
    ('audit_log.view', 'View audit logs'),
    ('admin.access', 'Access admin panel'),
    ('work_log.create', 'Create work log entries'),
    ('work_log.edit', 'Edit own work log entries'),
    ('work_log.edit_all', 'Edit any user''s work log entries'),
    ('work_log.view', 'View work log entries'),
    ('work_log.approve', 'Approve work log entries')
ON CONFLICT (name) DO NOTHING;
```

---

### 4. RolePermission (ロール権限紐付け)

**Description**: Many-to-many association between roles and permissions.

**Table**: `role_permissions`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `role_id` | UUID | NOT NULL, FOREIGN KEY → roles(id) ON DELETE CASCADE | Reference to role |
| `permission_id` | UUID | NOT NULL, FOREIGN KEY → permissions(id) ON DELETE CASCADE | Reference to permission |
| PRIMARY KEY | (role_id, permission_id) | Composite primary key | Prevents duplicate assignments |

**Indices**:
```sql
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);
```

**Business Rules**:
- FR-019: A role can have multiple permissions; a permission can belong to multiple roles
- Deleting a role cascades to remove all its permission associations
- Deleting a permission cascades to remove all its role associations

**Seed Data**:
```sql
-- ADMIN role: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions
ON CONFLICT DO NOTHING;

-- USER role: limited permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id FROM permissions
WHERE name IN (
    'user.view',
    'report.view',
    'work_log.create',
    'work_log.edit',
    'work_log.view'
)
ON CONFLICT DO NOTHING;

-- MODERATOR role: approval + view permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions
WHERE name IN (
    'user.view',
    'report.view',
    'report.export',
    'work_log.view',
    'work_log.approve',
    'audit_log.view'
)
ON CONFLICT DO NOTHING;
```

---

### 5. UserSession (ユーザーセッション)

**Description**: Active user sessions for audit and concurrent session tracking.

**Table**: `user_sessions`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique session identifier |
| `user_id` | UUID | NOT NULL, FOREIGN KEY → users(id) ON DELETE CASCADE | Reference to user |
| `session_id` | VARCHAR(255) | NOT NULL, UNIQUE | Spring Security session ID (JSESSIONID) |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Session creation timestamp |
| `last_accessed_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last activity timestamp |
| `expires_at` | TIMESTAMP | NOT NULL | Session expiration timestamp |
| `ip_address` | INET | NULL | Client IP address |
| `user_agent` | TEXT | NULL | Client user agent string |

**Indices**:
```sql
CREATE UNIQUE INDEX idx_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_created_at ON user_sessions(created_at);
```

**Business Rules**:
- FR-013: `expires_at` = `last_accessed_at` + 30 minutes (updated on each request)
- FR-014: If remember-me enabled, `expires_at` = `created_at` + 30 days
- Sessions with `expires_at` < NOW() are considered expired (can be deleted via scheduled job)
- Multiple sessions per user allowed (unlimited concurrent devices)
- On password change: Delete all sessions except current user's active session

**Cleanup Strategy**:
```kotlin
@Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
fun cleanupExpiredSessions() {
    userSessionRepository.deleteExpiredSessions(Instant.now())
}
```

---

### 6. PersistentLogin (永続ログイントークン)

**Description**: Remember-me tokens for 30-day session persistence (Spring Security standard table).

**Table**: `persistent_logins`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `username` | VARCHAR(64) | NOT NULL | User's email (maps to User.email) |
| `series` | VARCHAR(64) | PRIMARY KEY | Token series identifier |
| `token` | VARCHAR(64) | NOT NULL | Token value (hashed) |
| `last_used` | TIMESTAMP | NOT NULL | Last token usage timestamp |

**Indices**:
```sql
CREATE INDEX idx_persistent_logins_username ON persistent_logins(username);
CREATE INDEX idx_persistent_logins_last_used ON persistent_logins(last_used);
```

**Business Rules**:
- FR-014: Tokens valid for 30 days from `last_used`
- Spring Security automatically manages token rotation on each use
- Tokens with `last_used` < NOW() - 30 days can be deleted via scheduled job

---

### 7. PasswordResetToken (パスワードリセットトークン)

**Description**: Temporary tokens for password reset flow (24-hour validity).

**Table**: `password_reset_tokens`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique token identifier |
| `user_id` | UUID | NOT NULL, FOREIGN KEY → users(id) ON DELETE CASCADE | Reference to user |
| `token` | VARCHAR(255) | NOT NULL, UNIQUE | Secure random token (URL-safe) |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Token creation timestamp |
| `expires_at` | TIMESTAMP | NOT NULL | Token expiration (created_at + 24 hours) |
| `used_at` | TIMESTAMP | NULL | Token usage timestamp (NULL if unused) |

**Indices**:
```sql
CREATE UNIQUE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
```

**Validation Rules**:
- `token`: 32-character URL-safe random string (alphanumeric + hyphen/underscore)
- `expires_at`: Must be > `created_at` (typically created_at + 24 hours)

**Business Rules**:
- FR-012: Tokens valid for 24 hours (`expires_at` = `created_at` + 24 hours)
- FR-012: Once used, `used_at` set to NOW() → token cannot be reused
- When user requests new reset: Invalidate all previous tokens (set `used_at` = NOW() or delete)
- Edge case: If user requests multiple resets, only the latest token is valid

**Cleanup Strategy**:
```kotlin
@Scheduled(cron = "0 0 4 * * *") // Daily at 4 AM
fun cleanupExpiredTokens() {
    passwordResetTokenRepository.deleteExpiredTokens(Instant.now())
}
```

---

### 8. AuditLog (監査ログ)

**Description**: Immutable log of authentication/authorization events for security auditing.

**Table**: `audit_logs`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, DEFAULT gen_random_uuid() | Unique log entry identifier |
| `user_id` | UUID | NULL, FOREIGN KEY → users(id) ON DELETE SET NULL | Reference to user (NULL for anonymous events) |
| `event_type` | VARCHAR(100) | NOT NULL | Event category (see event types below) |
| `ip_address` | INET | NULL | Client IP address |
| `timestamp` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Event occurrence timestamp |
| `details` | JSONB | NULL | Additional event-specific data |
| `retention_days` | INTEGER | NOT NULL, DEFAULT 90 | Retention period for this log entry |

**Indices**:
```sql
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_retention_cleanup ON audit_logs(timestamp) WHERE retention_days = 90;
```

**Event Types** (FR-015):
- **LOGIN_SUCCESS**: Successful authentication
- **LOGIN_FAILURE**: Failed authentication (wrong password, nonexistent user)
- **LOGOUT**: User-initiated logout
- **PASSWORD_CHANGE**: User changed password
- **PASSWORD_RESET_REQUEST**: User requested password reset
- **PASSWORD_RESET_COMPLETE**: User completed password reset
- **EMAIL_VERIFICATION**: User verified email address
- **ACCOUNT_LOCKED**: Account locked due to failed login attempts (FR-006)
- **ACCOUNT_UNLOCKED**: Account unlocked (auto or admin action)
- **PERMISSION_DENIED**: User attempted unauthorized action (FR-009)
- **ROLE_CHANGED**: Admin changed user's role
- **EMAIL_SEND_FAILURE**: Email sending failed (FR-011)
- **AUDIT_LOG_CLEANUP**: Scheduled deletion of expired audit logs

**Details JSONB Structure** (examples):
```json
// LOGIN_SUCCESS
{
  "session_id": "abc123...",
  "user_agent": "Mozilla/5.0...",
  "remember_me": true
}

// LOGIN_FAILURE
{
  "email": "user@example.com",
  "reason": "invalid_password",
  "failed_attempts": 3
}

// PERMISSION_DENIED
{
  "requested_permission": "user.delete",
  "resource_id": "uuid-of-user",
  "endpoint": "/api/users/123"
}

// EMAIL_SEND_FAILURE
{
  "recipient": "user@example.com",
  "email_type": "password_reset",
  "error_message": "SMTP connection timeout"
}
```

**Business Rules**:
- **FR-020**: Logs older than `retention_days` deleted automatically (see research.md Topic 6)
- **Immutability**: Audit logs never updated or soft-deleted (only hard-deleted after retention)
- **Sensitive Data**: Never log passwords, tokens, or full session cookies
- **Anonymized Events**: `user_id` can be NULL for events without authenticated context (e.g., failed login with nonexistent email)

**Cleanup Strategy**:
```kotlin
@Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
fun deleteExpiredAuditLogs() {
    val cutoffDate = Instant.now().minus(90, ChronoUnit.DAYS)
    auditLogRepository.deleteExpiredLogs(cutoffDate, batchSize = 1000)
}
```

---

## Entity Relationships Diagram

```
┌─────────────────┐
│     roles       │
│  - id (PK)      │
│  - name         │
│  - description  │
└────────┬────────┘
         │ 1
         │
         │ N
┌────────▼──────────────┐       N ┌─────────────────────┐
│  role_permissions     │◄────────┤   permissions       │
│  - role_id (FK)       │         │  - id (PK)          │
│  - permission_id (FK) │         │  - name             │
└───────────────────────┘         │  - description      │
                                  └─────────────────────┘

         1
         │
         │ N
┌────────▼────────┐
│     users       │
│  - id (PK)      │
│  - email        │
│  - password     │
│  - role_id (FK) │
│  - status       │
└────────┬────────┘
         │ 1
         │
         ├──────────────────┐
         │ N                │ N
┌────────▼────────┐  ┌──────▼────────────────┐
│ user_sessions   │  │ password_reset_tokens │
│ - id (PK)       │  │ - id (PK)             │
│ - user_id (FK)  │  │ - user_id (FK)        │
│ - session_id    │  │ - token               │
└─────────────────┘  │ - expires_at          │
                     └───────────────────────┘
         │ 1
         │
         │ N
┌────────▼────────┐
│   audit_logs    │
│ - id (PK)       │
│ - user_id (FK)  │
│ - event_type    │
│ - timestamp     │
└─────────────────┘

┌──────────────────────┐
│  persistent_logins   │
│ - series (PK)        │
│ - username           │  (references users.email)
│ - token              │
│ - last_used          │
└──────────────────────┘
```

---

## Database Migration Script

**File**: `backend/src/main/resources/db/migration/V003__user_auth.sql`

```sql
-- Roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_roles_name ON roles(name);

-- Permissions table
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_permissions_name ON permissions(name);

-- Role-Permission association table
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id),
    account_status VARCHAR(50) NOT NULL DEFAULT 'unverified' CHECK (account_status IN ('active', 'unverified', 'locked', 'deleted')),
    failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0 AND failed_login_attempts <= 10),
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP,
    email_verified_at TIMESTAMP,
    CONSTRAINT chk_locked_until_future CHECK (locked_until IS NULL OR locked_until > NOW())
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_created_at ON users(created_at);

-- User sessions table
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    ip_address INET,
    user_agent TEXT
);

CREATE UNIQUE INDEX idx_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_created_at ON user_sessions(created_at);

-- Spring Security persistent logins (remember-me)
CREATE TABLE persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

CREATE INDEX idx_persistent_logins_username ON persistent_logins(username);
CREATE INDEX idx_persistent_logins_last_used ON persistent_logins(last_used);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    CONSTRAINT chk_expires_at_after_created CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

-- Audit logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    ip_address INET,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    details JSONB,
    retention_days INTEGER NOT NULL DEFAULT 90
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_retention_cleanup ON audit_logs(timestamp) WHERE retention_days = 90;

-- Seed initial data (roles, permissions, role_permissions)
-- See "Seed Data" sections in entity definitions above
-- (INSERT statements moved to V003__user_auth.sql for completeness)
```

---

## Kotlin Entity Classes

**User.kt**:
```kotlin
@Table("users")
data class User(
    @Id val id: UUID = UUID.randomUUID(),
    val email: String,
    val hashedPassword: String,
    val name: String,
    val roleId: UUID,
    val accountStatus: AccountStatus = AccountStatus.UNVERIFIED,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val lastLoginAt: Instant? = null,
    val emailVerifiedAt: Instant? = null
) {
    enum class AccountStatus {
        ACTIVE, UNVERIFIED, LOCKED, DELETED
    }
    
    fun isLocked(): Boolean = 
        accountStatus == AccountStatus.LOCKED && 
        lockedUntil != null && 
        lockedUntil.isAfter(Instant.now())
    
    fun isVerified(): Boolean = 
        emailVerifiedAt != null
}
```

**Role.kt**:
```kotlin
@Table("roles")
data class Role(
    @Id val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String?,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

**Permission.kt**:
```kotlin
@Table("permissions")
data class Permission(
    @Id val id: UUID = UUID.randomUUID(),
    val name: String, // e.g., "user.create", "report.view"
    val description: String?,
    val createdAt: Instant = Instant.now()
)
```

**AuditLog.kt**:
```kotlin
@Table("audit_logs")
data class AuditLog(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID?,
    val eventType: String,
    val ipAddress: String?,
    val timestamp: Instant = Instant.now(),
    val details: String?, // JSON string
    val retentionDays: Int = 90
) {
    companion object {
        const val LOGIN_SUCCESS = "LOGIN_SUCCESS"
        const val LOGIN_FAILURE = "LOGIN_FAILURE"
        const val LOGOUT = "LOGOUT"
        const val PASSWORD_CHANGE = "PASSWORD_CHANGE"
        const val PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST"
        const val PASSWORD_RESET_COMPLETE = "PASSWORD_RESET_COMPLETE"
        const val EMAIL_VERIFICATION = "EMAIL_VERIFICATION"
        const val ACCOUNT_LOCKED = "ACCOUNT_LOCKED"
        const val ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED"
        const val PERMISSION_DENIED = "PERMISSION_DENIED"
        const val ROLE_CHANGED = "ROLE_CHANGED"
        const val EMAIL_SEND_FAILURE = "EMAIL_SEND_FAILURE"
        const val AUDIT_LOG_CLEANUP = "AUDIT_LOG_CLEANUP"
    }
}
```

---

## Summary

This data model provides:
- **7 core entities** (User, Role, Permission, RolePermission, UserSession, PasswordResetToken, AuditLog) + 1 Spring Security table (PersistentLogin)
- **Complete validation rules** aligned with FR-001 to FR-020
- **State transitions** for account lifecycle management
- **Performance indices** for frequently queried columns
- **Audit trail** with 90-day retention (FR-020)
- **Security patterns** (bcrypt hashing, token-based resets, session tracking)

All entities ready for Kotlin implementation and PostgreSQL migration (V003__user_auth.sql).

**Next Steps**: Generate API contracts (auth-api.yaml, user-api.yaml) and quickstart.md.
