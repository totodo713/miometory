package com.worklog.domain.user;

import com.worklog.domain.role.RoleId;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * User aggregate root.
 * 
 * Represents an authenticated user in the system with their credentials,
 * role, and account status information.
 */
public class User {
    
    // Email validation pattern (RFC 5322 simplified)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private final UserId id;
    private String email;
    private String name;
    private String hashedPassword;
    private RoleId roleId;
    private AccountStatus accountStatus;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private Instant emailVerifiedAt;
    
    /**
     * Account status enum.
     */
    public enum AccountStatus {
        ACTIVE,       // Normal active account
        UNVERIFIED,   // Email not verified
        LOCKED,       // Account locked due to security (e.g., too many failed logins)
        DELETED       // Soft-deleted account
    }
    
    /**
     * Constructor for creating a new User.
     */
    public User(
            UserId id,
            String email,
            String name,
            String hashedPassword,
            RoleId roleId,
            AccountStatus accountStatus,
            int failedLoginAttempts,
            Instant createdAt
    ) {
        this(id, email, name, hashedPassword, roleId, accountStatus, failedLoginAttempts, 
             null, createdAt, createdAt, null, null);
    }
    
    /**
     * Rehydration constructor for restoring a User from persistence.
     */
    public User(
            UserId id,
            String email,
            String name,
            String hashedPassword,
            RoleId roleId,
            AccountStatus accountStatus,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            Instant emailVerifiedAt
    ) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.roleId = Objects.requireNonNull(roleId, "Role ID cannot be null");
        this.accountStatus = Objects.requireNonNull(accountStatus, "Account status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp cannot be null");
        
        validateAndSetEmail(email);
        validateAndSetName(name);
        
        this.hashedPassword = Objects.requireNonNull(hashedPassword, "Hashed password cannot be null");
        this.failedLoginAttempts = Math.max(0, failedLoginAttempts);
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.emailVerifiedAt = emailVerifiedAt;
    }
    
    /**
     * Factory method for creating a new unverified User.
     */
    public static User create(
            String email,
            String name,
            String hashedPassword,
            RoleId roleId
    ) {
        return new User(
            UserId.generate(),
            email,
            name,
            hashedPassword,
            roleId,
            AccountStatus.UNVERIFIED,  // New users start unverified
            0,  // No failed attempts initially
            Instant.now()
        );
    }
    
    /**
     * Records a successful login.
     */
    public void recordSuccessfulLogin() {
        if (accountStatus == AccountStatus.DELETED) {
            throw new IllegalStateException("Cannot login to deleted account");
        }
        
        this.failedLoginAttempts = 0;
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Auto-unlock if lock period has expired
        if (isLocked() && lockedUntil != null && Instant.now().isAfter(lockedUntil)) {
            this.accountStatus = AccountStatus.ACTIVE;
            this.lockedUntil = null;
        }
    }
    
    /**
     * Records a failed login attempt.
     * 
     * @param maxAttempts Maximum allowed failed attempts before locking
     * @param lockDurationMinutes Duration to lock account in minutes
     */
    public void recordFailedLogin(int maxAttempts, int lockDurationMinutes) {
        this.failedLoginAttempts++;
        this.updatedAt = Instant.now();
        
        if (this.failedLoginAttempts >= maxAttempts) {
            lock(lockDurationMinutes);
        }
    }
    
    /**
     * Locks the account for the specified duration.
     * 
     * @param durationMinutes Duration in minutes
     */
    public void lock(int durationMinutes) {
        this.accountStatus = AccountStatus.LOCKED;
        this.lockedUntil = Instant.now().plusSeconds(durationMinutes * 60L);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Manually unlocks the account (e.g., by admin).
     */
    public void unlock() {
        if (accountStatus != AccountStatus.LOCKED) {
            throw new IllegalStateException("Cannot unlock account that is not locked");
        }
        
        this.accountStatus = AccountStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Verifies the user's email address.
     */
    public void verifyEmail() {
        if (emailVerifiedAt != null) {
            throw new IllegalStateException("Email already verified");
        }
        
        this.emailVerifiedAt = Instant.now();
        
        // Transition from UNVERIFIED to ACTIVE upon email verification
        if (accountStatus == AccountStatus.UNVERIFIED) {
            this.accountStatus = AccountStatus.ACTIVE;
        }
        
        this.updatedAt = Instant.now();
    }
    
    /**
     * Changes the user's password (already hashed).
     */
    public void changePassword(String newHashedPassword) {
        if (newHashedPassword == null || newHashedPassword.isBlank()) {
            throw new IllegalArgumentException("Hashed password cannot be empty");
        }
        
        this.hashedPassword = newHashedPassword;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Updates user information.
     */
    public void update(String email, String name) {
        validateAndSetEmail(email);
        validateAndSetName(name);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Changes the user's role.
     */
    public void changeRole(RoleId newRoleId) {
        this.roleId = Objects.requireNonNull(newRoleId, "Role ID cannot be null");
        this.updatedAt = Instant.now();
    }
    
    /**
     * Soft-deletes the account.
     */
    public void delete() {
        this.accountStatus = AccountStatus.DELETED;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Restores a soft-deleted account.
     */
    public void restore() {
        if (accountStatus != AccountStatus.DELETED) {
            throw new IllegalStateException("Cannot restore account that is not deleted");
        }
        
        this.accountStatus = emailVerifiedAt != null ? AccountStatus.ACTIVE : AccountStatus.UNVERIFIED;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Checks if the account is currently locked.
     */
    public boolean isLocked() {
        return accountStatus == AccountStatus.LOCKED && 
               lockedUntil != null && 
               Instant.now().isBefore(lockedUntil);
    }
    
    /**
     * Checks if the email is verified.
     */
    public boolean isVerified() {
        return emailVerifiedAt != null;
    }
    
    /**
     * Checks if the account is active and can login.
     * Accounts with LOCKED status but expired lock time are considered loginable.
     * UNVERIFIED users cannot login until they verify their email.
     */
    public boolean canLogin() {
        // Deleted accounts cannot login
        if (accountStatus == AccountStatus.DELETED) {
            return false;
        }
        // Locked accounts with non-expired locks cannot login
        if (accountStatus == AccountStatus.LOCKED) {
            // If lock has expired, allow login (will be auto-unlocked on successful login)
            if (lockedUntil != null && Instant.now().isAfter(lockedUntil)) {
                return true;
            }
            return false;
        }

        // UNVERIFIED users cannot login until they verify their email
        if (accountStatus == AccountStatus.UNVERIFIED) {
            return false;
        }

        // ACTIVE users can login
        return accountStatus == AccountStatus.ACTIVE;
    }
    
    /**
     * Validates and sets email.
     */
    private void validateAndSetEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (email.length() > 255) {
            throw new IllegalArgumentException("Email cannot exceed 255 characters");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
        this.email = email.toLowerCase();  // Normalize to lowercase
    }
    
    /**
     * Validates and sets name.
     */
    private void validateAndSetName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Name cannot exceed 100 characters");
        }
        this.name = name;
    }
    
    // Getters
    
    public UserId getId() {
        return id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getName() {
        return name;
    }
    
    public String getHashedPassword() {
        return hashedPassword;
    }
    
    public RoleId getRoleId() {
        return roleId;
    }
    
    public AccountStatus getAccountStatus() {
        return accountStatus;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public Instant getLockedUntil() {
        return lockedUntil;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
    
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", accountStatus=" + accountStatus +
                ", isLocked=" + isLocked() +
                ", isVerified=" + isVerified() +
                '}';
    }
}
