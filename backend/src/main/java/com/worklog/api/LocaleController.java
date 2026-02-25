package com.worklog.api;

import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user locale preference.
 */
@RestController
@RequestMapping("/api/v1/user")
public class LocaleController {

    private final JdbcUserRepository userRepository;

    public LocaleController(JdbcUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Update the authenticated user's preferred locale.
     *
     * @param request Locale update request body
     * @param httpRequest HTTP request for session access
     * @return 204 No Content on success
     */
    @PatchMapping("/locale")
    public ResponseEntity<Void> updateLocale(@RequestBody LocaleUpdateRequest request, HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = (UUID) session.getAttribute("userId");

        if (request.locale() == null
                || (!request.locale().equals("en") && !request.locale().equals("ja"))) {
            return ResponseEntity.badRequest().build();
        }

        userRepository.updateLocale(UserId.of(userId), request.locale());
        return ResponseEntity.noContent().build();
    }

    /**
     * Request body for locale update.
     *
     * @param locale Locale code ("en" or "ja")
     */
    public record LocaleUpdateRequest(String locale) {}
}
