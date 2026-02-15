package com.worklog.api;

import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern;
import com.worklog.domain.monthlyperiod.MonthlyPeriodPatternId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.MonthlyPeriodPatternRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for MonthlyPeriodPattern operations.
 *
 * Provides endpoints for monthly period pattern management.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/monthly-period-patterns")
public class MonthlyPeriodPatternController {

    private final MonthlyPeriodPatternRepository monthlyPeriodPatternRepository;

    public MonthlyPeriodPatternController(MonthlyPeriodPatternRepository monthlyPeriodPatternRepository) {
        this.monthlyPeriodPatternRepository = monthlyPeriodPatternRepository;
    }

    /**
     * Creates a new monthly period pattern.
     *
     * POST /api/v1/tenants/{tenantId}/monthly-period-patterns
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPattern(
            @PathVariable UUID tenantId, @RequestBody CreateMonthlyPeriodPatternRequest request) {
        MonthlyPeriodPattern pattern =
                MonthlyPeriodPattern.create(TenantId.of(tenantId), request.name(), request.startDay());

        monthlyPeriodPatternRepository.save(pattern);

        Map<String, Object> response = toMap(pattern);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a monthly period pattern by ID.
     *
     * GET /api/v1/tenants/{tenantId}/monthly-period-patterns/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPattern(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return monthlyPeriodPatternRepository
                .findById(MonthlyPeriodPatternId.of(id))
                .map(pattern -> ResponseEntity.ok(toMap(pattern)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all monthly period patterns for a tenant.
     *
     * GET /api/v1/tenants/{tenantId}/monthly-period-patterns
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPatterns(@PathVariable UUID tenantId) {
        List<Map<String, Object>> patterns = monthlyPeriodPatternRepository.findByTenantId(tenantId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(patterns);
    }

    /**
     * Converts a MonthlyPeriodPattern to a Map response.
     */
    private Map<String, Object> toMap(MonthlyPeriodPattern pattern) {
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
    public record CreateMonthlyPeriodPatternRequest(String name, int startDay) {}
}
