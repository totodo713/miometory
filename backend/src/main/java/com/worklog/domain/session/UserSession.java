package com.worklog.domain.session;

import com.worklog.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UserSession entity.
 *
 * Tracks active user sessions for security and session management.
 */
public class UserSession {

    private final UUID sessionId;
    private final UserId userId;
    private final String ipAddress;
    private final String userAgent;
    private final Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccessedAt;

    /**
     * Constructor for creating a new UserSession.
     */
    public UserSession(
            UUID sessionId, UserId userId, String ipAddress, String userAgent, Instant createdAt, Instant expiresAt) {
        this(sessionId, userId, ipAddress, userAgent, createdAt, expiresAt, createdAt);
    }

    /**
     * Rehydration constructor for restoring a UserSession from persistence.
     */
    public UserSession(
            UUID sessionId,
            UserId userId,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant expiresAt,
            Instant lastAccessedAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "Session ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires timestamp cannot be null");
        this.lastAccessedAt = Objects.requireNonNull(lastAccessedAt, "Last accessed timestamp cannot be null");

        this.ipAddress = ipAddress; // Can be null
        this.userAgent = userAgent; // Can be null

        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Expiration time cannot be before creation time");
        }
    }

    /**
     * Factory method for creating a new UserSession.
     *
     * @param userId User ID
     * @param ipAddress IP address (can be null)
     * @param userAgent User agent string (can be null)
     * @param sessionDurationMinutes Session duration in minutes
     */
    public static UserSession create(UserId userId, String ipAddress, String userAgent, int sessionDurationMinutes) {
        Instant now = Instant.now();
        return new UserSession(
                UUID.randomUUID(), userId, ipAddress, userAgent, now, now.plusSeconds(sessionDurationMinutes * 60L));
    }

    /**
     * Updates last accessed time and extends expiration.
     *
     * @param extensionMinutes Minutes to extend from now
     */
    public void touch(int extensionMinutes) {
        this.lastAccessedAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(extensionMinutes * 60L);
    }

    /**
     * Checks if the session is expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the session is still valid.
     */
    public boolean isValid() {
        return !isExpired();
    }

    // Getters

    public UUID getSessionId() {
        return sessionId;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSession that = (UserSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return "UserSession{" + "sessionId="
                + sessionId + ", userId="
                + userId + ", ipAddress='"
                + ipAddress + '\'' + ", isExpired="
                + isExpired() + '}';
    }
}
