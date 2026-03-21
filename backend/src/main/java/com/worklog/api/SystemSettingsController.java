package com.worklog.api;

import com.worklog.application.service.SystemSettingsService;
import com.worklog.application.service.SystemSettingsService.SystemDefaultRules;
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

    @GetMapping("/rules")
    @PreAuthorize("hasPermission(null, 'system_settings.view')")
    public ResponseEntity<SystemDefaultRules> getRules() {
        return ResponseEntity.ok(systemSettingsService.getDefaultRules());
    }

    @PutMapping("/rules")
    @PreAuthorize("hasPermission(null, 'system_settings.update')")
    public ResponseEntity<Void> updateRules(
            @Valid @RequestBody UpdateRulesRequest request, Authentication authentication) {
        UUID userId = userRepository
                .findByEmail(authentication.getName())
                .map(user -> user.getId().value())
                .orElse(null);
        systemSettingsService.updateDefaultRules(
                request.fiscalYearStartMonth(), request.fiscalYearStartDay(), request.monthlyPeriodStartDay(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance-times")
    @PreAuthorize("hasPermission(null, 'system_settings.view')")
    public ResponseEntity<com.worklog.application.service.SystemSettingsService.SystemDefaultAttendanceTimes>
            getAttendanceTimes() {
        return ResponseEntity.ok(systemSettingsService.getDefaultAttendanceTimes());
    }

    @PutMapping("/attendance-times")
    @PreAuthorize("hasPermission(null, 'system_settings.update')")
    public ResponseEntity<Void> updateAttendanceTimes(
            @Valid @RequestBody UpdateAttendanceTimesRequest request, Authentication authentication) {
        UUID userId = userRepository
                .findByEmail(authentication.getName())
                .map(user -> user.getId().value())
                .orElse(null);
        systemSettingsService.updateDefaultAttendanceTimes(
                java.time.LocalTime.parse(request.startTime()), java.time.LocalTime.parse(request.endTime()), userId);
        return ResponseEntity.noContent().build();
    }

    public record UpdateRulesRequest(
            @Min(1) @Max(12) int fiscalYearStartMonth,
            @Min(1) @Max(31) int fiscalYearStartDay,
            @Min(1) @Max(28) int monthlyPeriodStartDay) {}

    public record UpdateAttendanceTimesRequest(
            @jakarta.validation.constraints.NotBlank String startTime,
            @jakarta.validation.constraints.NotBlank String endTime) {}
}
