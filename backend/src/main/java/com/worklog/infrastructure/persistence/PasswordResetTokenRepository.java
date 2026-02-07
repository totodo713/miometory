package com.worklog.infrastructure.persistence;

import com.worklog.domain.password.PasswordResetToken;
import com.worklog.domain.user.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing password reset tokens.
 */
@Repository
public class PasswordResetTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    public PasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Insert new token */
    public void save(PasswordResetToken token) {
        String sql = """
            INSERT INTO password_reset_tokens (id, user_id, token, created_at, expires_at, used_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                used_at = EXCLUDED.used_at
            """;
        jdbcTemplate.update(
            sql,
            token.getId(),
            token.getUserId().value(),
            token.getToken(),
            Timestamp.from(token.getCreatedAt()),
            Timestamp.from(token.getExpiresAt()),
            token.getUsedAt() != null ? Timestamp.from(token.getUsedAt()) : null
        );
    }

    /**
     * Find by token
     */
    public Optional<PasswordResetToken> findByToken(String token) {
        String sql = "SELECT * FROM password_reset_tokens WHERE token = ?";
        List<PasswordResetToken> result = jdbcTemplate.query(sql, new TokenRowMapper(), token);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Find valid (unused + not expired) by token
     */
    public Optional<PasswordResetToken> findValidByToken(String token) {
        String sql = """
            SELECT * FROM password_reset_tokens WHERE token = ? AND used_at IS NULL AND expires_at > NOW()
            """;
        List<PasswordResetToken> result = jdbcTemplate.query(sql, new TokenRowMapper(), token);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Mark given token as used (set used_at)
     */
    public void markAsUsed(UUID tokenId) {
        String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), tokenId);
    }

    /**
     * Set used_at for all unused tokens for given user (invalidation)
     */
    public void invalidateUnusedTokensForUser(UserId userId) {
        String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), userId.value());
    }

    /**
     * Delete expired tokens
     */
    public void deleteExpired() {
        String sql = "DELETE FROM password_reset_tokens WHERE expires_at < NOW()";
        jdbcTemplate.update(sql);
    }

    /**
     * TokenRowMapper maps db to PasswordResetToken
     */
    private static class TokenRowMapper implements RowMapper<PasswordResetToken> {
        @Override
        public PasswordResetToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PasswordResetToken(
                rs.getObject("id", UUID.class),
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getString("token"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("used_at") != null,
                rs.getTimestamp("used_at") != null ? rs.getTimestamp("used_at").toInstant() : null
            );
        }
    }
}
