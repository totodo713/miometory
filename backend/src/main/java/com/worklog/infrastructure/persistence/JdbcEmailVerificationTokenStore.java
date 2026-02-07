package com.worklog.infrastructure.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * JDBC-based email verification token store.
 * Stores tokens in PostgreSQL for reliability and cluster support.
 */
@Repository
public class JdbcEmailVerificationTokenStore {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;
    private static final long EXPIRATION_HOURS = 24;
    
    private final JdbcClient jdbcClient;
    
    public JdbcEmailVerificationTokenStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
    
    /**
     * Generates a new verification token for the user.
     * 
     * @param userId The user ID
     * @return The generated token
     */
    @Transactional
    public String generateToken(UUID userId) {
        byte[] bytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(EXPIRATION_HOURS * 3600);
        
        jdbcClient.sql("""
            INSERT INTO email_verification_tokens (user_id, token, created_at, expires_at)
            VALUES (?, ?, ?, ?)
            """)
            .param(userId)
            .param(token)
            .param(Timestamp.from(now))
            .param(Timestamp.from(expiresAt))
            .update();
        
        return token;
    }
    
    /**
     * Validates and consumes a verification token.
     * 
     * @param token The token to validate
     * @return The user ID if token is valid
     * @throws IllegalArgumentException if token is invalid or expired
     */
    @Transactional
    public UUID validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid verification token");
        }
        
        // Find token
        var result = jdbcClient.sql("""
            SELECT user_id, expires_at, used_at
            FROM email_verification_tokens
            WHERE token = ?
            """)
            .param(token)
            .query((rs, rowNum) -> new TokenData(
                UUID.fromString(rs.getString("user_id")),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("used_at") != null ? rs.getTimestamp("used_at").toInstant() : null
            ))
            .optional();
        
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Invalid verification token");
        }
        
        TokenData data = result.get();
        
        // Check if already used
        if (data.usedAt != null) {
            throw new IllegalArgumentException("Verification token has already been used");
        }
        
        // Check if expired
        if (Instant.now().isAfter(data.expiresAt)) {
            throw new IllegalArgumentException("Verification token has expired");
        }
        
        // Mark as used
        jdbcClient.sql("""
            UPDATE email_verification_tokens
            SET used_at = ?
            WHERE token = ?
            """)
            .param(Timestamp.from(Instant.now()))
            .param(token)
            .update();
        
        return data.userId;
    }
    
    /**
     * Checks if token exists (for testing).
     */
    public boolean exists(String token) {
        return jdbcClient.sql("""
            SELECT COUNT(*) FROM email_verification_tokens WHERE token = ?
            """)
            .param(token)
            .query(Integer.class)
            .single() > 0;
    }
    
    /**
     * Clears all tokens (for testing).
     */
    @Transactional
    public void clear() {
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
    }
    
    private record TokenData(UUID userId, Instant expiresAt, Instant usedAt) {}
}
