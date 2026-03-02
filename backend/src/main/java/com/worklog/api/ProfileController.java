package com.worklog.api;

import com.worklog.api.dto.ProfileResponse;
import com.worklog.api.dto.UpdateProfileRequest;
import com.worklog.api.dto.UpdateProfileResponse;
import com.worklog.application.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        ProfileResponse profile = profileService.getProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @RequestBody @Valid UpdateProfileRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UpdateProfileResponse result =
                profileService.updateProfile(authentication.getName(), request.displayName(), request.email());

        if (result.emailChanged()) {
            new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
        }

        return ResponseEntity.ok(result);
    }
}
