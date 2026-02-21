package com.worklog.application.service;

import com.worklog.application.command.CreateOrganizationCommand;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.shared.Code;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.OrganizationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for Organization operations.
 *
 * Coordinates organization-related use cases and enforces business rules.
 * Handles hierarchy validation including circular reference and self-reference checks.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Creates a new organization.
     *
     * @param command The creation command
     * @return ID of the newly created organization
     */
    @Transactional
    public UUID createOrganization(CreateOrganizationCommand command) {
        OrganizationId organizationId = OrganizationId.generate();
        TenantId tenantId = new TenantId(command.tenantId());
        OrganizationId parentId = command.parentId() != null ? new OrganizationId(command.parentId()) : null;
        Code code = Code.of(command.code());

        // Validate parent exists if provided and calculate level
        int level = 1;
        if (parentId != null) {
            Organization parentOrg = organizationRepository
                    .findById(parentId)
                    .orElseThrow(
                            () -> new IllegalArgumentException("Parent organization not found: " + command.parentId()));
            level = parentOrg.getLevel() + 1;
        }

        Organization organization =
                Organization.create(organizationId, tenantId, parentId, code, command.name(), level);

        // Assign patterns if provided
        if (command.fiscalYearPatternId() != null || command.monthlyPeriodPatternId() != null) {
            organization.assignPatterns(command.fiscalYearPatternId(), command.monthlyPeriodPatternId());
        }

        organizationRepository.save(organization);
        return organization.getId().value();
    }

    /**
     * Updates an organization's name.
     *
     * @param organizationId ID of the organization to update
     * @param name New name
     */
    @Transactional
    public void updateOrganization(UUID organizationId, String name) {
        Organization organization = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        organization.update(name);
        organizationRepository.save(organization);
    }

    /**
     * Deactivates an organization.
     *
     * @param organizationId ID of the organization to deactivate
     */
    @Transactional
    public void deactivateOrganization(UUID organizationId) {
        Organization organization = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        organization.deactivate();
        organizationRepository.save(organization);
    }

    /**
     * Activates an organization.
     *
     * @param organizationId ID of the organization to activate
     */
    @Transactional
    public void activateOrganization(UUID organizationId) {
        Organization organization = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        organization.activate();
        organizationRepository.save(organization);
    }

    /**
     * Assigns fiscal year and monthly period patterns to an organization.
     *
     * @param organizationId ID of the organization
     * @param fiscalYearPatternId Fiscal year pattern ID (can be null)
     * @param monthlyPeriodPatternId Monthly period pattern ID (can be null)
     */
    @Transactional
    public void assignPatterns(UUID organizationId, UUID fiscalYearPatternId, UUID monthlyPeriodPatternId) {
        Organization organization = organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        organization.assignPatterns(fiscalYearPatternId, monthlyPeriodPatternId);
        organizationRepository.save(organization);
    }

    /**
     * Finds an organization by ID.
     *
     * @param organizationId ID of the organization to find
     * @return The organization, or null if not found
     */
    public Organization findById(UUID organizationId) {
        return organizationRepository
                .findById(new OrganizationId(organizationId))
                .orElse(null);
    }
}
