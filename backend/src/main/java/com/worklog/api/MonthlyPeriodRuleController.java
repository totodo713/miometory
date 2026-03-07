package com.worklog.api;

import com.worklog.application.service.TenantAccessValidator;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRule;
import com.worklog.domain.monthlyperiod.MonthlyPeriodRuleId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.MonthlyPeriodRuleRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for MonthlyPeriodRule operations.
 *
 * Provides endpoints for monthly period pattern management.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/monthly-period-rules")
public class MonthlyPeriodRuleController {

    private final MonthlyPeriodRuleRepository monthlyPeriodRuleRepository;
    private final TenantAccessValidator tenantAccessValidator;

    public MonthlyPeriodRuleController(
            MonthlyPeriodRuleRepository monthlyPeriodRuleRepository,
            TenantAccessValidator tenantAccessValidator) {
        this.monthlyPeriodRuleRepository = monthlyPeriodRuleRepository;
        this.tenantAccessValidator = tenantAccessValidator;
    }

    /**
     * Creates a new monthly period pattern.
     *
     * POST /api/v1/tenants/{tenantId}/monthly-period-rules
     */
    @PostMapping
    @PreAuthorize("hasPermission(null, 'tenant.update') or hasPermission(null, 'tenant_settings.manage')")
    public ResponseEntity<Map<String, Object>> createPattern(
            @PathVariable UUID tenantId, @RequestBody CreateMonthlyPeriodRuleRequest request, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        MonthlyPeriodRule pattern =
                MonthlyPeriodRule.create(TenantId.of(tenantId), request.name(), request.startDay());

        monthlyPeriodRuleRepository.save(pattern);

        Map<String, Object> response = toMap(pattern);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a monthly period pattern by ID.
     *
     * GET /api/v1/tenants/{tenantId}/monthly-period-rules/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'tenant.view') or hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<Map<String, Object>> getPattern(
            @PathVariable UUID tenantId, @PathVariable UUID id, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        return monthlyPeriodRuleRepository
                .findById(MonthlyPeriodRuleId.of(id))
                .map(pattern -> ResponseEntity.ok(toMap(pattern)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all monthly period patterns for a tenant.
     *
     * GET /api/v1/tenants/{tenantId}/monthly-period-rules
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'tenant.view') or hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<List<Map<String, Object>>> listPatterns(@PathVariable UUID tenantId, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        List<Map<String, Object>> patterns = monthlyPeriodRuleRepository.findByTenantId(tenantId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(patterns);
    }

    /**
     * Converts a MonthlyPeriodRule to a Map response.
     */
    private Map<String, Object> toMap(MonthlyPeriodRule pattern) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pattern.getId().value().toString());
        map.put("tenantId", pattern.getTenantId().toString());
        map.put("name", pattern.getName());
        map.put("startDay", pattern.getStartDay());
        return map;
    }

    /**
     * Request DTO for creating monthly period patterns.
     */
    public record CreateMonthlyPeriodRuleRequest(String name, int startDay) {}
}
