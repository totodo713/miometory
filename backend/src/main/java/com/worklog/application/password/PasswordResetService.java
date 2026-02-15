package com.worklog.application.password;

import com.worklog.application.validation.PasswordValidator;
import com.worklog.domain.password.PasswordResetToken;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.email.EmailService;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.persistence.PasswordResetTokenRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private final JdbcUserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final JdbcUserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            JdbcUserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            JdbcUserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * パスワードリセット申請
     * Always returns success (anti-enumeration)
     */
    @Transactional
    public void requestReset(String email) {
        // Normalize email to lowercase and trim
        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Always succeed for non-existent user (no enumeration)
            return;
        }
        User user = userOpt.get();

        // All DB operations in transaction to ensure consistency
        tokenRepository.invalidateUnusedTokensForUser(user.getId());
        String token = generateSecureToken();
        PasswordResetToken resetToken = PasswordResetToken.create(user.getId(), token, 24 * 60); // 24h
        tokenRepository.save(resetToken);

        // Send email after DB commit (transaction boundary)
        emailService.sendPasswordResetEmail(normalizedEmail, token);
    }

    /**
     * パスワードリセット確定
     * Throws if invalid/expired/used
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        // Validate password strength before processing
        PasswordValidator.validate(newPassword);

        PasswordResetToken resetToken = tokenRepository
                .findValidByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        UserId uid = resetToken.getUserId();
        User user = userRepository.findById(uid).orElseThrow(() -> new IllegalStateException("User not found"));
        String hashed = passwordEncoder.encode(newPassword);

        // Update password in User entity
        user.changePassword(hashed);
        userRepository.save(user);
        tokenRepository.markAsUsed(resetToken.getId());
        userSessionRepository.deleteByUserId(uid);
    }

    /**
     * 32バイトの安全なランダムtoken(Base64エンコード)
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
