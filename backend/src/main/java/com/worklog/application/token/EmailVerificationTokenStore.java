package com.worklog.application.token;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Instant;
import java.util.UUID;

/**
 * In-memory email verification token store.
 * 
 * NOTE: This is a simple implementation for MVP. In production, tokens should be:
 * 1. Stored in database (password_reset_tokens table or similar)
 * 2. Encrypted/hashed
 * 3. Have proper expiration with cleanup jobs
 * 
 * For now, we use in-memory storage to make tests pass.
 */
public class EmailVerificationTokenStore {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;
    private static final long EXPIRATION_HOURS = 24;
    
    // token -> userId mapping with expiration
    private final ConcurrentMap<String, TokenData> tokens = new ConcurrentHashMap<>();
    
    /**
     * Generates a new verification token for the user.
     * 
     * @param userId The user ID
     * @return The generated token (min 32 characters)
     */
    public String generateToken(UUID userId) {
        byte[] bytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_HOURS * 3600);
        tokens.put(token, new TokenData(userId, expiresAt));
        
        return token;
    }
    
    /**
     * Validates and consumes a verification token.
     * 
     * @param token The token to validate
     * @return The user ID if token is valid
     * @throws IllegalArgumentException if token is invalid or expired
     */
    public UUID validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid verification token");
        }
        
        TokenData data = tokens.remove(token);
        
        if (data == null) {
            throw new IllegalArgumentException("Invalid or already used verification token");
        }
        
        if (Instant.now().isAfter(data.expiresAt)) {
            throw new IllegalArgumentException("Verification token has expired");
        }
        
        return data.userId;
    }
    
    /**
     * Checks if token exists (for testing).
     */
    public boolean exists(String token) {
        return tokens.containsKey(token);
    }
    
    /**
     * Clears all tokens (for testing).
     */
    public void clear() {
        tokens.clear();
    }
    
    private record TokenData(UUID userId, Instant expiresAt) {}
}
