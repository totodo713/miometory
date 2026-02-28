package com.worklog.infrastructure.persistence;

import com.worklog.domain.role.RoleId;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for User aggregate.
 *
 * Provides CRUD operations and custom queries for user management,
 * authentication, and account status tracking.
 */
@Repository
public class JdbcUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds a user by ID.
     *
     * @param id User ID
     * @return Optional containing the user if found
     */
    public Optional<User> findById(UserId id) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE id = ?
            """;

        List<User> results = jdbcTemplate.query(sql, new UserRowMapper(), id.value());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find a user by email (case-insensitive).
     *
     * @param email Email address
     * @return Optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE LOWER(email) = LOWER(?)
            """;

        List<User> results = jdbcTemplate.query(sql, new UserRowMapper(), email);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Check if a user exists by email (case-insensitive).
     *
     * @param email Email address
     * @return true if a user with this email exists
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) > 0 FROM users WHERE LOWER(email) = LOWER(?)";
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, email);
        return result != null && result;
    }

    /**
     * Finds which of the given emails already exist in the users table (case-insensitive).
     *
     * @param emails Collection of email addresses to check
     * @return Set of existing emails (lowercased)
     */
    public Set<String> findExistingEmails(Collection<String> emails) {
        if (emails.isEmpty()) {
            return Set.of();
        }

        String placeholders = emails.stream().map(e -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT LOWER(email) FROM users WHERE LOWER(email) IN (" + placeholders + ")";
        Object[] params = emails.stream().map(e -> e.toLowerCase(Locale.ROOT)).toArray();

        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, params));
    }

    /**
     * Find all users with a specific account status.
     *
     * @param status Account status
     * @return List of users with the specified status
     */
    public List<User> findByAccountStatus(User.AccountStatus status) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE account_status = ?
            """;

        return jdbcTemplate.query(sql, new UserRowMapper(), status.name().toLowerCase());
    }

    /**
     * Find all locked users whose lock has expired.
     *
     * @param now Current timestamp
     * @return List of users with expired locks
     */
    public List<User> findExpiredLockedUsers(Instant now) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE account_status = 'locked' AND locked_until < ?
            """;

        return jdbcTemplate.query(sql, new UserRowMapper(), Timestamp.from(now));
    }

    /**
     * Find all unverified users created before a certain date.
     *
     * @param before Cutoff timestamp
     * @return List of unverified users created before the specified date
     */
    public List<User> findUnverifiedUsersBefore(Instant before) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE account_status = 'unverified' AND created_at < ?
            """;

        return jdbcTemplate.query(sql, new UserRowMapper(), Timestamp.from(before));
    }

    /**
     * Saves a user (insert or update using UPSERT).
     *
     * @param user The user to save
     * @return The saved user
     */
    public User save(User user) {
        String upsertSql = """
            INSERT INTO users (id, email, name, hashed_password, role_id, account_status,
                             failed_login_attempts, locked_until, created_at, updated_at,
                             last_login_at, email_verified_at, preferred_locale)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                name = EXCLUDED.name,
                hashed_password = EXCLUDED.hashed_password,
                role_id = EXCLUDED.role_id,
                account_status = EXCLUDED.account_status,
                failed_login_attempts = EXCLUDED.failed_login_attempts,
                locked_until = EXCLUDED.locked_until,
                updated_at = EXCLUDED.updated_at,
                last_login_at = EXCLUDED.last_login_at,
                email_verified_at = EXCLUDED.email_verified_at,
                preferred_locale = EXCLUDED.preferred_locale
            """;

        jdbcTemplate.update(
                upsertSql,
                user.getId().value(),
                user.getEmail(),
                user.getName(),
                user.getHashedPassword(),
                user.getRoleId().value(),
                user.getAccountStatus().name().toLowerCase(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil() != null ? Timestamp.from(user.getLockedUntil()) : null,
                Timestamp.from(user.getCreatedAt()),
                Timestamp.from(user.getUpdatedAt()),
                user.getLastLoginAt() != null ? Timestamp.from(user.getLastLoginAt()) : null,
                user.getEmailVerifiedAt() != null ? Timestamp.from(user.getEmailVerifiedAt()) : null,
                user.getPreferredLocale());

        return user;
    }

    /**
     * Updates only the preferred locale for a user.
     *
     * @param id User ID
     * @param locale Locale code ("en" or "ja")
     */
    public void updateLocale(UserId id, String locale) {
        String sql = "UPDATE users SET preferred_locale = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, locale, Timestamp.from(Instant.now()), id.value());
    }

    /**
     * Deletes a user by ID.
     *
     * @param id User ID
     */
    public void deleteById(UserId id) {
        String sql = "DELETE FROM users WHERE id = ?";
        jdbcTemplate.update(sql, id.value());
    }

    /**
     * Deletes all users (for testing purposes).
     */
    public void deleteAll() {
        String sql = "DELETE FROM users";
        jdbcTemplate.update(sql);
    }

    /**
     * Searches users by email partial match (case-insensitive).
     * Used by admin tenant assignment search.
     *
     * @param emailPartial Partial email string to search for
     * @return List of matching users (max 20 results)
     */
    public List<User> searchByEmailPartial(String emailPartial) {
        String sql = """
            SELECT id, email, name, hashed_password, role_id, account_status,
                   failed_login_attempts, locked_until, created_at, updated_at,
                   last_login_at, email_verified_at, preferred_locale
            FROM users
            WHERE LOWER(email) LIKE LOWER(?) ESCAPE '\\'
            AND account_status != 'deleted'
            ORDER BY email
            LIMIT 20
            """;

        String pattern = "%" + escapeLike(emailPartial) + "%";
        return jdbcTemplate.query(sql, new UserRowMapper(), pattern);
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Counts total number of users.
     *
     * @return Total user count
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * RowMapper for User entity.
     */
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instant lockedUntil = null;
            if (rs.getTimestamp("locked_until") != null) {
                lockedUntil = rs.getTimestamp("locked_until").toInstant();
            }

            Instant lastLoginAt = null;
            if (rs.getTimestamp("last_login_at") != null) {
                lastLoginAt = rs.getTimestamp("last_login_at").toInstant();
            }

            Instant emailVerifiedAt = null;
            if (rs.getTimestamp("email_verified_at") != null) {
                emailVerifiedAt = rs.getTimestamp("email_verified_at").toInstant();
            }

            return new User(
                    UserId.of(rs.getObject("id", UUID.class)),
                    rs.getString("email"),
                    rs.getString("name"),
                    rs.getString("hashed_password"),
                    RoleId.of(rs.getObject("role_id", UUID.class)),
                    User.AccountStatus.valueOf(rs.getString("account_status").toUpperCase()),
                    rs.getInt("failed_login_attempts"),
                    lockedUntil,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    lastLoginAt,
                    emailVerifiedAt,
                    rs.getString("preferred_locale"));
        }
    }
}
