package com.worklog.application.service;

import com.worklog.application.command.CreateTenantCommand;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for Tenant operations.
 *
 * Coordinates tenant-related use cases and enforces business rules.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Creates a new tenant.
     *
     * @param command The creation command
     * @return ID of the newly created tenant
     */
    @Transactional
    public UUID createTenant(CreateTenantCommand command) {
        Tenant tenant = Tenant.create(command.code(), command.name());
        tenantRepository.save(tenant);
        return tenant.getId().value();
    }

    /**
     * Updates a tenant's name.
     *
     * @param tenantId ID of the tenant to update
     * @param name New name
     */
    @Transactional
    public void updateTenant(UUID tenantId, String name) {
        Tenant tenant = tenantRepository
                .findById(new TenantId(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.update(name);
        tenantRepository.save(tenant);
    }

    /**
     * Deactivates a tenant.
     *
     * @param tenantId ID of the tenant to deactivate
     */
    @Transactional
    public void deactivateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository
                .findById(new TenantId(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.deactivate();
        tenantRepository.save(tenant);
    }

    /**
     * Activates a tenant.
     *
     * @param tenantId ID of the tenant to activate
     */
    @Transactional
    public void activateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository
                .findById(new TenantId(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.activate();
        tenantRepository.save(tenant);
    }

    /**
     * Finds a tenant by ID.
     *
     * @param tenantId ID of the tenant to find
     * @return The tenant, or null if not found
     */
    public Tenant findById(UUID tenantId) {
        return tenantRepository.findById(new TenantId(tenantId)).orElse(null);
    }
}
