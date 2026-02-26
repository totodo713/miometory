package com.worklog.application.service;

import com.worklog.api.AdminTenantController.BootstrapMember;
import com.worklog.api.AdminTenantController.BootstrapOrganization;
import com.worklog.api.AdminTenantController.BootstrapResult;
import com.worklog.api.AdminTenantController.BootstrapTenantRequest;
import com.worklog.api.AdminTenantController.CreatedMember;
import com.worklog.api.AdminTenantController.CreatedOrganization;
import com.worklog.application.command.CreateOrganizationCommand;
import com.worklog.application.command.InviteMemberCommand;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminTenantService {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AdminOrganizationService adminOrganizationService;
    private final AdminMemberService adminMemberService;
    private final AdminMasterDataService adminMasterDataService;

    public AdminTenantService(
            TenantRepository tenantRepository,
            JdbcTemplate jdbcTemplate,
            AdminOrganizationService adminOrganizationService,
            AdminMemberService adminMemberService,
            AdminMasterDataService adminMasterDataService) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.adminOrganizationService = adminOrganizationService;
        this.adminMemberService = adminMemberService;
        this.adminMasterDataService = adminMasterDataService;
    }

    @Transactional(readOnly = true)
    public TenantPage listTenants(String status, int page, int size) {
        var sb = new StringBuilder("SELECT id, code, name, status, created_at FROM tenant WHERE 1=1");
        var countSb = new StringBuilder("SELECT COUNT(*) FROM tenant WHERE 1=1");
        var params = new java.util.ArrayList<Object>();
        var countParams = new java.util.ArrayList<Object>();

        if (status != null && !status.isBlank()) {
            sb.append(" AND UPPER(status) = UPPER(?)");
            countSb.append(" AND UPPER(status) = UPPER(?)");
            params.add(status);
            countParams.add(status);
        }

        Long total = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long totalElements = total != null ? total : 0;

        sb.append(" ORDER BY code LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<TenantRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new TenantRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant().toString()),
                params.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new TenantPage(content, totalElements, totalPages, page);
    }

    public UUID createTenant(String code, String name) {
        // Check for duplicate code
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM tenant WHERE LOWER(code) = LOWER(?)", Boolean.class, code);
        if (Boolean.TRUE.equals(exists)) {
            throw new DomainException("DUPLICATE_CODE", "A tenant with this code already exists");
        }

        Tenant tenant = Tenant.create(code, name);
        tenantRepository.save(tenant);

        // Insert into tenant projection table (event sourced aggregate, projection needs manual insert)
        // @Transactional on the class ensures atomicity between event store and projection writes
        jdbcTemplate.update(
                "INSERT INTO tenant (id, code, name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())"
                        + " ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at",
                tenant.getId().value(),
                code,
                name);

        return tenant.getId().value();
    }

    public void updateTenant(UUID id, String name) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        tenant.update(name);
        tenantRepository.save(tenant);

        // Update projection
        jdbcTemplate.update("UPDATE tenant SET name = ?, updated_at = NOW() WHERE id = ?", name, id);
    }

    public void deactivateTenant(UUID id) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        tenant.deactivate();
        tenantRepository.save(tenant);

        // Update projection
        jdbcTemplate.update("UPDATE tenant SET status = 'INACTIVE', updated_at = NOW() WHERE id = ?", id);
    }

    public void activateTenant(UUID id) {
        Tenant tenant = tenantRepository
                .findById(TenantId.of(id))
                .orElseThrow(() -> new DomainException("TENANT_NOT_FOUND", "Tenant not found"));

        tenant.activate();
        tenantRepository.save(tenant);

        // Update projection
        jdbcTemplate.update("UPDATE tenant SET status = 'ACTIVE', updated_at = NOW() WHERE id = ?", id);
    }

    public BootstrapResult bootstrapTenant(UUID tenantId, BootstrapTenantRequest request) {
        // Verify tenant exists and is ACTIVE
        List<String> statuses =
                jdbcTemplate.queryForList("SELECT status FROM tenant WHERE id = ?", String.class, tenantId);
        if (statuses.isEmpty()) {
            throw new DomainException("TENANT_NOT_FOUND", "Tenant not found");
        }
        if (!"ACTIVE".equals(statuses.get(0))) {
            throw new DomainException("TENANT_INACTIVE", "Tenant is not active");
        }

        // Prevent double bootstrap: check no organizations exist yet
        Long orgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization WHERE tenant_id = ?", Long.class, tenantId);
        if (orgCount != null && orgCount > 0) {
            throw new DomainException(
                    "ALREADY_BOOTSTRAPPED", "Tenant already has organizations; bootstrap can only run once");
        }

        // Copy active presets as initial tenant master data
        adminMasterDataService.copyPresetsToTenant(tenantId);

        // Create organizations
        Map<String, UUID> orgCodeToId = new HashMap<>();
        List<CreatedOrganization> createdOrgs = new ArrayList<>();

        for (BootstrapOrganization org : request.organizations()) {
            UUID orgId = adminOrganizationService.createOrganization(new CreateOrganizationCommand(
                    tenantId,
                    org.parentId(),
                    org.code(),
                    org.name(),
                    org.fiscalYearPatternId(),
                    org.monthlyPeriodPatternId()));
            orgCodeToId.put(org.code(), orgId);
            createdOrgs.add(new CreatedOrganization(orgId, org.code()));
        }

        // Invite members
        List<CreatedMember> createdMembers = new ArrayList<>();
        List<MemberToPromote> toPromote = new ArrayList<>();

        for (BootstrapMember member : request.members()) {
            UUID orgId = orgCodeToId.get(member.organizationCode());
            if (orgId == null) {
                throw new DomainException(
                        "INVALID_ORG_CODE",
                        "Organization code '" + member.organizationCode() + "' not found in bootstrap request");
            }

            var result = adminMemberService.inviteMember(
                    new InviteMemberCommand(member.email(), member.displayName(), orgId, null, null), tenantId);

            createdMembers.add(
                    new CreatedMember(result.memberId().toString(), member.email(), result.temporaryPassword()));

            if (member.tenantAdmin()) {
                toPromote.add(new MemberToPromote(result.memberId(), tenantId));
            }
        }

        // Assign TENANT_ADMIN role
        for (MemberToPromote promote : toPromote) {
            adminMemberService.assignTenantAdmin(promote.memberId, promote.tenantId);
        }

        return new BootstrapResult(createdOrgs, createdMembers);
    }

    private record MemberToPromote(UUID memberId, UUID tenantId) {}

    public record TenantRow(String id, String code, String name, String status, String createdAt) {}

    public record TenantPage(List<TenantRow> content, long totalElements, int totalPages, int number) {}
}
