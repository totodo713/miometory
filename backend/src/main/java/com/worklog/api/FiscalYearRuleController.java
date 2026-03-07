package com.worklog.api;

import com.worklog.application.service.TenantAccessValidator;
import com.worklog.domain.fiscalyear.FiscalYearRule;
import com.worklog.domain.fiscalyear.FiscalYearRuleId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearRuleRepository;
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
 * REST controller for FiscalYearRule operations.
 *
 * Provides endpoints for fiscal year pattern management.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fiscal-year-rules")
public class FiscalYearRuleController {

    private final FiscalYearRuleRepository fiscalYearRuleRepository;
    private final TenantAccessValidator tenantAccessValidator;

    public FiscalYearRuleController(
            FiscalYearRuleRepository fiscalYearRuleRepository, TenantAccessValidator tenantAccessValidator) {
        this.fiscalYearRuleRepository = fiscalYearRuleRepository;
        this.tenantAccessValidator = tenantAccessValidator;
    }

    /**
     * Creates a new fiscal year pattern.
     *
     * POST /api/v1/tenants/{tenantId}/fiscal-year-rules
     */
    @PostMapping
    @PreAuthorize("hasPermission(null, 'tenant.update') or hasPermission(null, 'tenant_settings.manage')")
    public ResponseEntity<Map<String, Object>> createPattern(
            @PathVariable UUID tenantId, @RequestBody CreateFiscalYearRuleRequest request, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        FiscalYearRule pattern = FiscalYearRule.create(
                TenantId.of(tenantId), request.name(), request.startMonth(), request.startDay());

        fiscalYearRuleRepository.save(pattern);

        Map<String, Object> response = toMap(pattern);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a fiscal year pattern by ID.
     *
     * GET /api/v1/tenants/{tenantId}/fiscal-year-rules/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'tenant.view') or hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<Map<String, Object>> getPattern(
            @PathVariable UUID tenantId, @PathVariable UUID id, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        return fiscalYearRuleRepository
                .findById(FiscalYearRuleId.of(id))
                .map(pattern -> ResponseEntity.ok(toMap(pattern)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all fiscal year patterns for a tenant.
     *
     * GET /api/v1/tenants/{tenantId}/fiscal-year-rules
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'tenant.view') or hasPermission(null, 'tenant_settings.view')")
    public ResponseEntity<List<Map<String, Object>>> listPatterns(@PathVariable UUID tenantId, Authentication auth) {
        tenantAccessValidator.validateAccess(auth, tenantId);

        List<Map<String, Object>> patterns = fiscalYearRuleRepository.findByTenantId(tenantId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(patterns);
    }

    /**
     * Converts a FiscalYearRule to a Map response.
     */
    private Map<String, Object> toMap(FiscalYearRule pattern) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pattern.getId().value().toString());
        map.put("tenantId", pattern.getTenantId().toString());
        map.put("name", pattern.getName());
        map.put("startMonth", pattern.getStartMonth());
        map.put("startDay", pattern.getStartDay());
        return map;
    }

    /**
     * Request DTO for creating fiscal year patterns.
     */
    public record CreateFiscalYearRuleRequest(String name, int startMonth, int startDay) {}
}
