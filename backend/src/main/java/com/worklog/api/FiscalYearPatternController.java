package com.worklog.api;

import com.worklog.domain.fiscalyear.FiscalYearPattern;
import com.worklog.domain.fiscalyear.FiscalYearPatternId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.FiscalYearPatternRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for FiscalYearPattern operations.
 * 
 * Provides endpoints for fiscal year pattern management.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fiscal-year-patterns")
public class FiscalYearPatternController {

    private final FiscalYearPatternRepository fiscalYearPatternRepository;

    public FiscalYearPatternController(FiscalYearPatternRepository fiscalYearPatternRepository) {
        this.fiscalYearPatternRepository = fiscalYearPatternRepository;
    }

    /**
     * Creates a new fiscal year pattern.
     * 
     * POST /api/v1/tenants/{tenantId}/fiscal-year-patterns
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPattern(
        @PathVariable UUID tenantId,
        @RequestBody CreateFiscalYearPatternRequest request
    ) {
        FiscalYearPattern pattern = FiscalYearPattern.create(
            TenantId.of(tenantId),
            request.name(),
            request.startMonth(),
            request.startDay()
        );
        
        fiscalYearPatternRepository.save(pattern);
        
        Map<String, Object> response = toMap(pattern);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a fiscal year pattern by ID.
     * 
     * GET /api/v1/tenants/{tenantId}/fiscal-year-patterns/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPattern(
        @PathVariable UUID tenantId,
        @PathVariable UUID id
    ) {
        return fiscalYearPatternRepository.findById(FiscalYearPatternId.of(id))
            .map(pattern -> ResponseEntity.ok(toMap(pattern)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all fiscal year patterns for a tenant.
     * 
     * GET /api/v1/tenants/{tenantId}/fiscal-year-patterns
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPatterns(@PathVariable UUID tenantId) {
        List<Map<String, Object>> patterns = fiscalYearPatternRepository.findByTenantId(tenantId)
            .stream()
            .map(this::toMap)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(patterns);
    }

    /**
     * Converts a FiscalYearPattern to a Map response.
     */
    private Map<String, Object> toMap(FiscalYearPattern pattern) {
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
    public record CreateFiscalYearPatternRequest(
        String name,
        int startMonth,
        int startDay
    ) {}
}
