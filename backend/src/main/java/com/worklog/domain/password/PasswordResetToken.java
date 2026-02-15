package com.worklog.domain.password;

import com.worklog.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PasswordResetToken entity.
 *
 * Represents a token for password reset functionality.
 * Tokens are single-use and expire after a set duration.
 */
public class PasswordResetToken {

    private final UUID id;
    private final UserId userId;
    private final String token; // Validated in constructor
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean used;
    private Instant usedAt;

    /**
     * Constructor for creating a new PasswordResetToken.
     */
    public PasswordResetToken(UUID id, UserId userId, String token, Instant createdAt, Instant expiresAt) {
        this(id, userId, token, createdAt, expiresAt, false, null);
    }

    /**
     * Rehydration constructor for restoring a PasswordResetToken from persistence.
     */
    public PasswordResetToken(
            UUID id, UserId userId, String token, Instant createdAt, Instant expiresAt, boolean used, Instant usedAt) {
        this.id = Objects.requireNonNull(id, "Token ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires timestamp cannot be null");

        // Validate token
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be empty");
        }
        if (token.length() < 32) {
            throw new IllegalArgumentException("Token must be at least 32 characters");
        }
        this.token = token;

        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Expiration time cannot be before creation time");
        }

        this.used = used;
        this.usedAt = usedAt;

        if (used && usedAt == null) {
            throw new IllegalArgumentException("Used timestamp must be set if token is marked as used");
        }
    }

    /**
     * Factory method for creating a new PasswordResetToken.
     *
     * @param userId User ID
     * @param token Secure random token string
     * @param validityMinutes Token validity in minutes
     */
    public static PasswordResetToken create(UserId userId, String token, int validityMinutes) {
        Instant now = Instant.now();
        return new PasswordResetToken(UUID.randomUUID(), userId, token, now, now.plusSeconds(validityMinutes * 60L));
    }

    /**
     * Marks the token as used.
     */
    public void markAsUsed() {
        if (used) {
            throw new IllegalStateException("Token already used");
        }
        if (isExpired()) {
            throw new IllegalStateException("Cannot use expired token");
        }

        this.used = true;
        this.usedAt = Instant.now();
    }

    /**
     * Checks if the token is expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the token is valid (not used and not expired).
     */
    public boolean isValid() {
        return !used && !isExpired();
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordResetToken that = (PasswordResetToken) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PasswordResetToken{" + "id="
                + id + ", userId="
                + userId + ", used="
                + used + ", isExpired="
                + isExpired() + '}';
    }
}
