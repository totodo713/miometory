package com.worklog.infrastructure.persistence;

import com.worklog.domain.session.UserSession;
import com.worklog.domain.user.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for UserSession entity.
 *
 * Provides session management operations for tracking active user sessions.
 */
@Repository
public class JdbcUserSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds a session by session ID.
     *
     * @param sessionId Session ID (UUID as string)
     * @return Optional containing the session if found
     */
    public Optional<UserSession> findBySessionId(String sessionId) {
        String sql = """
            SELECT id, user_id, session_id, created_at, last_accessed_at,
                   expires_at, ip_address, user_agent
            FROM user_sessions
            WHERE session_id = ?
            """;

        List<UserSession> results = jdbcTemplate.query(sql, new UserSessionRowMapper(), sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds a session by its database ID.
     *
     * @param id Database ID (UUID)
     * @return Optional containing the session if found
     */
    public Optional<UserSession> findById(UUID id) {
        String sql = """
            SELECT id, user_id, session_id, created_at, last_accessed_at,
                   expires_at, ip_address, user_agent
            FROM user_sessions
            WHERE id = ?
            """;

        List<UserSession> results = jdbcTemplate.query(sql, new UserSessionRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds all sessions for a specific user.
     *
     * @param userId User ID
     * @return List of sessions for the user
     */
    public List<UserSession> findByUserId(UserId userId) {
        String sql = """
            SELECT id, user_id, session_id, created_at, last_accessed_at,
                   expires_at, ip_address, user_agent
            FROM user_sessions
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, new UserSessionRowMapper(), userId.value());
    }

    /**
     * Finds all expired sessions.
     *
     * @param now Current timestamp
     * @return List of expired sessions
     */
    public List<UserSession> findExpiredSessions(Instant now) {
        String sql = """
            SELECT id, user_id, session_id, created_at, last_accessed_at,
                   expires_at, ip_address, user_agent
            FROM user_sessions
            WHERE expires_at < ?
            """;

        return jdbcTemplate.query(sql, new UserSessionRowMapper(), Timestamp.from(now));
    }

    /**
     * Saves a session (insert or update using UPSERT).
     *
     * @param session The session to save
     * @return The saved session
     */
    public UserSession save(UserSession session) {
        String upsertSql = """
            INSERT INTO user_sessions (id, user_id, session_id, created_at, last_accessed_at,
                                      expires_at, ip_address, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?)
            ON CONFLICT (session_id) DO UPDATE SET
                last_accessed_at = EXCLUDED.last_accessed_at,
                expires_at = EXCLUDED.expires_at
            """;

        // Generate a unique ID for the session if it's a new one
        // The session_id (UUID) is used as the unique identifier
        UUID dbId = UUID.randomUUID(); // Database internal ID

        jdbcTemplate.update(
                upsertSql,
                dbId,
                session.getUserId().value(),
                session.getSessionId().toString(),
                Timestamp.from(session.getCreatedAt()),
                Timestamp.from(session.getLastAccessedAt()),
                Timestamp.from(session.getExpiresAt()),
                session.getIpAddress(),
                session.getUserAgent());

        return session;
    }

    /**
     * Deletes a session by session ID.
     *
     * @param sessionId Session ID (UUID as string)
     */
    public void deleteBySessionId(String sessionId) {
        String sql = "DELETE FROM user_sessions WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }

    /**
     * Deletes a session by database ID.
     *
     * @param id Database ID (UUID)
     */
    public void deleteById(UUID id) {
        String sql = "DELETE FROM user_sessions WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    /**
     * Deletes all sessions for a specific user.
     *
     * @param userId User ID
     */
    public void deleteByUserId(UserId userId) {
        String sql = "DELETE FROM user_sessions WHERE user_id = ?";
        jdbcTemplate.update(sql, userId.value());
    }

    /**
     * Deletes all expired sessions.
     *
     * @param now Current timestamp
     * @return Number of sessions deleted
     */
    public int deleteExpiredSessions(Instant now) {
        String sql = "DELETE FROM user_sessions WHERE expires_at < ?";
        return jdbcTemplate.update(sql, Timestamp.from(now));
    }

    /**
     * Deletes all sessions (for testing purposes).
     */
    public void deleteAll() {
        String sql = "DELETE FROM user_sessions";
        jdbcTemplate.update(sql);
    }

    /**
     * Counts total number of sessions.
     *
     * @return Total session count
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM user_sessions";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * RowMapper for UserSession entity.
     */
    private static class UserSessionRowMapper implements RowMapper<UserSession> {
        @Override
        public UserSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserSession(
                    UUID.fromString(rs.getString("session_id")),
                    UserId.of(rs.getObject("user_id", UUID.class)),
                    rs.getString("ip_address"),
                    rs.getString("user_agent"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("last_accessed_at").toInstant());
        }
    }
}
