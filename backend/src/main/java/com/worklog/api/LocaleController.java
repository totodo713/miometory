package com.worklog.api;

import com.worklog.application.service.UserContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user locale preference.
 */
@RestController
@RequestMapping("/api/v1/user")
public class LocaleController {

    private final UserContextService userContextService;

    public LocaleController(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    /**
     * Update the authenticated user's preferred locale.
     *
     * @param request Locale update request body
     * @param authentication Spring Security authentication
     * @return 204 No Content on success
     */
    @PatchMapping("/locale")
    public ResponseEntity<Void> updateLocale(@RequestBody LocaleUpdateRequest request, Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            userContextService.updatePreferredLocale(authentication.getName(), request.locale());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Request body for locale update.
     *
     * @param locale Locale code ("en" or "ja")
     */
    public record LocaleUpdateRequest(String locale) {}
}
