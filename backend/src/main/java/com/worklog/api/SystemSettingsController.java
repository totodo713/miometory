package com.worklog.api;

import com.worklog.application.service.SystemSettingsService;
import com.worklog.application.service.SystemSettingsService.SystemDefaultPatterns;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system/settings")
@Validated
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;
    private final JdbcUserRepository userRepository;

    public SystemSettingsController(SystemSettingsService systemSettingsService, JdbcUserRepository userRepository) {
        this.systemSettingsService = systemSettingsService;
        this.userRepository = userRepository;
    }

    @GetMapping("/patterns")
    @PreAuthorize("hasPermission(null, 'system_settings.view')")
    public ResponseEntity<SystemDefaultPatterns> getPatterns() {
        return ResponseEntity.ok(systemSettingsService.getDefaultPatterns());
    }

    @PutMapping("/patterns")
    @PreAuthorize("hasPermission(null, 'system_settings.update')")
    public ResponseEntity<Void> updatePatterns(
            @Valid @RequestBody UpdatePatternsRequest request, Authentication authentication) {
        UUID userId = userRepository
                .findByEmail(authentication.getName())
                .map(user -> user.getId().value())
                .orElse(null);
        systemSettingsService.updateDefaultPatterns(
                request.fiscalYearStartMonth(), request.fiscalYearStartDay(), request.monthlyPeriodStartDay(), userId);
        return ResponseEntity.noContent().build();
    }

    public record UpdatePatternsRequest(
            @Min(1) @Max(12) int fiscalYearStartMonth,
            @Min(1) @Max(31) int fiscalYearStartDay,
            @Min(1) @Max(28) int monthlyPeriodStartDay) {}
}
