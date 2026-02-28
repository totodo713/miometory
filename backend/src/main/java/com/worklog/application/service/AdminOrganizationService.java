package com.worklog.application.service;

import com.worklog.application.command.AssignManagerCommand;
import com.worklog.application.command.CreateOrganizationCommand;
import com.worklog.application.command.TransferMemberCommand;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.shared.Code;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for tenant-scoped organization management by Tenant Admins.
 */
@Service
@Transactional
public class AdminOrganizationService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminOrganizationService(
            OrganizationRepository organizationRepository,
            JdbcMemberRepository memberRepository,
            JdbcTemplate jdbcTemplate) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lists organizations within a tenant with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public OrganizationPage listOrganizations(
            UUID tenantId, String search, Boolean isActive, UUID parentId, int page, int size) {
        var sb = new StringBuilder("""
            SELECT o.id, o.tenant_id, o.parent_id, parent.name AS parent_name,
                   o.code, o.name, o.level, o.status,
                   o.fiscal_year_pattern_id, o.monthly_period_pattern_id,
                   o.created_at, o.updated_at,
                   COALESCE((SELECT COUNT(*) FROM members m WHERE m.organization_id = o.id), 0) AS member_count
            FROM organization o
            LEFT JOIN organization parent ON parent.id = o.parent_id
            WHERE o.tenant_id = ?
            """);

        var countSb = new StringBuilder("SELECT COUNT(*) FROM organization o WHERE o.tenant_id = ?");
        var params = new ArrayList<Object>();
        params.add(tenantId);
        var countParams = new ArrayList<Object>();
        countParams.add(tenantId);

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(o.name) LIKE ? ESCAPE '\\' OR LOWER(o.code) LIKE ? ESCAPE '\\')";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + escapeLike(search).toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (isActive != null) {
            String statusValue = isActive ? "ACTIVE" : "INACTIVE";
            String clause = " AND o.status = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(statusValue);
            countParams.add(statusValue);
        }
        if (parentId != null) {
            String clause = " AND o.parent_id = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(parentId);
            countParams.add(parentId);
        }

        Long totalElements = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long total = totalElements != null ? totalElements : 0;

        sb.append(" ORDER BY o.level, o.name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<OrganizationRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new OrganizationRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getString("parent_name"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("level"),
                        rs.getString("status"),
                        rs.getLong("member_count"),
                        rs.getObject("fiscal_year_pattern_id", UUID.class),
                        rs.getObject("monthly_period_pattern_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                params.toArray());

        int totalPages = (int) Math.ceil((double) total / size);
        return new OrganizationPage(content, total, totalPages, page);
    }

    /**
     * Builds a hierarchical tree of all organizations within a tenant.
     * Queries the projection table for all organizations and assembles
     * a recursive tree structure in memory, including member counts per org.
     *
     * @param tenantId        the tenant ID
     * @param includeInactive whether to include inactive organizations (default: false)
     * @return root-level tree nodes with nested children
     */
    @Transactional(readOnly = true)
    public List<OrganizationTreeNode> getOrganizationTree(UUID tenantId, boolean includeInactive) {
        var sb = new StringBuilder("""
            SELECT o.id, o.code, o.name, o.level, o.status, o.parent_id,
                   COALESCE((SELECT COUNT(*) FROM members m WHERE m.organization_id = o.id), 0) AS member_count
            FROM organization o
            WHERE o.tenant_id = ?
            """);

        var params = new ArrayList<Object>();
        params.add(tenantId);

        if (!includeInactive) {
            sb.append(" AND o.status = 'ACTIVE'");
        }

        sb.append(" ORDER BY o.level, o.name");

        // Flat row for intermediate processing
        record FlatNode(UUID id, String code, String name, int level, String status, UUID parentId, long memberCount) {}

        List<FlatNode> flatNodes = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new FlatNode(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("level"),
                        rs.getString("status"),
                        rs.getObject("parent_id", UUID.class),
                        rs.getLong("member_count")),
                params.toArray());

        // Build tree in memory
        Map<UUID, OrganizationTreeNode> nodeMap = new HashMap<>();
        List<OrganizationTreeNode> roots = new ArrayList<>();

        // First pass: create all nodes
        for (FlatNode flat : flatNodes) {
            var node = new OrganizationTreeNode(
                    flat.id(),
                    flat.code(),
                    flat.name(),
                    flat.level(),
                    flat.status(),
                    flat.memberCount(),
                    new ArrayList<>());
            nodeMap.put(flat.id(), node);
        }

        // Second pass: link children to parents (using mutable lists for building)
        for (FlatNode flat : flatNodes) {
            OrganizationTreeNode node = nodeMap.get(flat.id());
            if (flat.parentId() != null && nodeMap.containsKey(flat.parentId())) {
                nodeMap.get(flat.parentId()).children().add(node);
            } else {
                roots.add(node);
            }
        }

        // Wrap children lists in unmodifiable views before returning
        return roots.stream().map(AdminOrganizationService::makeUnmodifiable).toList();
    }

    /**
     * Creates a new organization.
     * Validates parent organization if provided, checks code uniqueness,
     * and calculates level from the parent hierarchy.
     */
    public UUID createOrganization(CreateOrganizationCommand command) {
        OrganizationId parentOrgId = null;
        int level = 1;

        // Validate parent if provided and calculate level
        if (command.parentId() != null) {
            parentOrgId = OrganizationId.of(command.parentId());
            Organization parentOrg = organizationRepository
                    .findById(parentOrgId)
                    .orElseThrow(() -> new DomainException("PARENT_NOT_FOUND", "Parent organization not found"));

            if (!parentOrg.getTenantId().value().equals(command.tenantId())) {
                throw new DomainException("TENANT_MISMATCH", "Parent organization belongs to a different tenant");
            }

            if (!parentOrg.isActive()) {
                throw new DomainException("PARENT_INACTIVE", "Cannot create organization under an inactive parent");
            }

            level = parentOrg.getLevel() + 1;
        }

        // Check code uniqueness within tenant (case-insensitive)
        String checkCodeSql = "SELECT COUNT(*) FROM organization WHERE tenant_id = ? AND LOWER(code) = LOWER(?)";
        Long existingCount = jdbcTemplate.queryForObject(checkCodeSql, Long.class, command.tenantId(), command.code());
        if (existingCount != null && existingCount > 0) {
            throw new DomainException("DUPLICATE_CODE", "An organization with this code already exists in this tenant");
        }

        // Create the organization aggregate
        OrganizationId newId = OrganizationId.generate();
        Code code = Code.of(command.code());
        Organization organization =
                Organization.create(newId, TenantId.of(command.tenantId()), parentOrgId, code, command.name(), level);

        // Assign patterns if provided
        if (command.fiscalYearPatternId() != null || command.monthlyPeriodPatternId() != null) {
            organization.assignPatterns(command.fiscalYearPatternId(), command.monthlyPeriodPatternId());
        }

        organizationRepository.save(organization);

        log.info(
                "Created organization {} (code: {}, level: {}) for tenant {}",
                newId.value(),
                command.code(),
                level,
                command.tenantId());

        return newId.value();
    }

    /**
     * Updates an organization's name.
     */
    public void updateOrganization(UUID orgId, UUID tenantId, String name) {
        Organization organization = organizationRepository
                .findById(OrganizationId.of(orgId))
                .orElseThrow(() -> new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found"));

        if (!organization.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify organization from another tenant");
        }

        organization.update(name);
        organizationRepository.save(organization);

        log.info("Updated organization {} name to: {}", orgId, name);
    }

    /**
     * Deactivates an organization.
     * Checks for active child organizations and returns warnings.
     */
    public List<String> deactivateOrganization(UUID orgId, UUID tenantId) {
        Organization organization = organizationRepository
                .findById(OrganizationId.of(orgId))
                .orElseThrow(() -> new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found"));

        if (!organization.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify organization from another tenant");
        }

        // Count active children
        String countChildrenSql = "SELECT COUNT(*) FROM organization WHERE parent_id = ? AND status = 'ACTIVE'";
        Long activeChildrenCount = jdbcTemplate.queryForObject(countChildrenSql, Long.class, orgId);
        long childrenCount = activeChildrenCount != null ? activeChildrenCount : 0;

        List<String> warnings = new ArrayList<>();
        if (childrenCount > 0) {
            warnings.add(String.format(
                    "This organization has %d active child organization%s that will remain active.",
                    childrenCount, childrenCount == 1 ? "" : "s"));
        }

        organization.deactivate();
        organizationRepository.save(organization);

        log.info("Deactivated organization {} (had {} active children)", orgId, childrenCount);

        return warnings;
    }

    /**
     * Activates a previously deactivated organization.
     */
    public void activateOrganization(UUID orgId, UUID tenantId) {
        Organization organization = organizationRepository
                .findById(OrganizationId.of(orgId))
                .orElseThrow(() -> new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found"));

        if (!organization.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify organization from another tenant");
        }

        organization.activate();
        organizationRepository.save(organization);

        log.info("Activated organization {}", orgId);
    }

    // --- Member management operations (US2: Supervisor Assignment) ---

    /**
     * Lists members belonging to a specific organization with pagination.
     * Includes manager name and manager active status for FR-013 (inactive manager flagging).
     *
     * @param orgId    the organization ID
     * @param tenantId the tenant ID (for tenant scoping)
     * @param page     page number (0-based)
     * @param size     page size
     * @param isActive optional filter: null = all, true = active only, false = inactive only
     * @return paginated list of organization members
     */
    @Transactional(readOnly = true)
    public OrganizationMemberPage listMembersByOrganization(
            UUID orgId, UUID tenantId, int page, int size, Boolean isActive) {
        // Validate organization belongs to tenant
        String checkOrgSql = "SELECT COUNT(*) FROM organization WHERE id = ? AND tenant_id = ?";
        Long orgCount = jdbcTemplate.queryForObject(checkOrgSql, Long.class, orgId, tenantId);
        if (orgCount == null || orgCount == 0) {
            throw new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found in this tenant");
        }

        var sb = new StringBuilder("""
            SELECT m.id, m.email, m.display_name, m.manager_id, m.is_active,
                   mgr.display_name AS manager_name,
                   mgr.is_active AS manager_is_active
            FROM members m
            LEFT JOIN members mgr ON mgr.id = m.manager_id
            WHERE m.organization_id = ? AND m.tenant_id = ?
            """);

        var countSb =
                new StringBuilder("SELECT COUNT(*) FROM members m WHERE m.organization_id = ? AND m.tenant_id = ?");
        var params = new ArrayList<Object>();
        params.add(orgId);
        params.add(tenantId);
        var countParams = new ArrayList<Object>();
        countParams.add(orgId);
        countParams.add(tenantId);

        if (isActive != null) {
            String clause = " AND m.is_active = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(isActive);
            countParams.add(isActive);
        }

        Long totalElements = jdbcTemplate.queryForObject(countSb.toString(), Long.class, countParams.toArray());
        long total = totalElements != null ? totalElements : 0;

        sb.append(" ORDER BY m.display_name LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<OrganizationMemberRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> {
                    UUID managerId = (UUID) rs.getObject("manager_id");
                    // manager_is_active is only meaningful when manager_id is not null
                    Boolean managerIsActive = managerId != null ? rs.getBoolean("manager_is_active") : null;
                    return new OrganizationMemberRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("display_name"),
                            managerId,
                            rs.getString("manager_name"),
                            managerIsActive,
                            rs.getBoolean("is_active"));
                },
                params.toArray());

        int totalPages = (int) Math.ceil((double) total / size);
        return new OrganizationMemberPage(content, total, totalPages, page);
    }

    /**
     * Assigns a manager (supervisor) to a member.
     * Validates that both members are active, prevents self-assignment,
     * and checks for circular references in the manager chain.
     *
     * @param command  the assign manager command
     * @param tenantId the tenant ID (for tenant scoping)
     */
    public void assignManager(AssignManagerCommand command, UUID tenantId) {
        MemberId memberId = MemberId.of(command.memberId());
        MemberId managerId = MemberId.of(command.managerId());

        // Validate member exists and belongs to tenant
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));
        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }
        if (!member.isActive()) {
            throw new DomainException("MEMBER_INACTIVE", "Cannot assign a manager to an inactive member");
        }

        // Validate manager exists and is active
        Member manager = memberRepository
                .findById(managerId)
                .orElseThrow(() -> new DomainException("MANAGER_NOT_FOUND", "Manager not found"));
        if (!manager.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Manager belongs to a different tenant");
        }
        if (!manager.isActive()) {
            throw new DomainException("MANAGER_INACTIVE", "Cannot assign an inactive member as manager");
        }

        // Check for circular reference
        if (memberRepository.wouldCreateCircularReference(memberId, managerId)) {
            throw new DomainException(
                    "CIRCULAR_REFERENCE",
                    "Assigning this manager would create a circular reference in the reporting chain");
        }

        member.assignManager(managerId);
        memberRepository.save(member);

        log.info("Assigned manager {} to member {} in tenant {}", command.managerId(), command.memberId(), tenantId);
    }

    /**
     * Removes the manager assignment from a member.
     *
     * @param memberId the member to remove the manager from
     * @param tenantId the tenant ID (for tenant scoping)
     */
    public void removeManager(UUID memberId, UUID tenantId) {
        Member member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }

        member.removeManager();
        memberRepository.save(member);

        log.info("Removed manager from member {} in tenant {}", memberId, tenantId);
    }

    /**
     * Transfers a member to a different organization.
     * Validates that the target organization is active and different from the current one.
     * The member's manager is removed during transfer since managers are organization-specific.
     *
     * @param command  the transfer member command
     * @param tenantId the tenant ID (for tenant scoping)
     */
    public void transferMember(TransferMemberCommand command, UUID tenantId) {
        MemberId memberId = MemberId.of(command.memberId());

        // Validate member exists and belongs to tenant
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));
        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }
        if (!member.isActive()) {
            throw new DomainException("MEMBER_INACTIVE", "Cannot transfer an inactive member");
        }

        OrganizationId targetOrgId = OrganizationId.of(command.targetOrgId());

        // Check that target is different from current
        if (member.hasOrganization() && member.getOrganizationId().equals(targetOrgId)) {
            throw new DomainException("SAME_ORGANIZATION", "Member already belongs to this organization");
        }

        // Validate target organization exists, belongs to same tenant, and is active
        Organization targetOrg = organizationRepository
                .findById(targetOrgId)
                .orElseThrow(() -> new DomainException("ORGANIZATION_NOT_FOUND", "Target organization not found"));
        if (!targetOrg.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Target organization belongs to a different tenant");
        }
        if (!targetOrg.isActive()) {
            throw new DomainException("ORGANIZATION_INACTIVE", "Cannot transfer member to an inactive organization");
        }

        member.changeOrganization(targetOrgId);
        memberRepository.save(member);

        log.info(
                "Transferred member {} to organization {} in tenant {}",
                command.memberId(),
                command.targetOrgId(),
                tenantId);
    }

    /**
     * Assigns fiscal year and monthly period patterns to an organization.
     * Validates tenant ownership and that the organization is active.
     *
     * @param orgId                  the organization ID
     * @param tenantId               the tenant ID (for tenant scoping)
     * @param fiscalYearPatternId    the fiscal year pattern ID to assign
     * @param monthlyPeriodPatternId the monthly period pattern ID to assign
     */
    public void assignPatterns(UUID orgId, UUID tenantId, UUID fiscalYearPatternId, UUID monthlyPeriodPatternId) {
        Organization organization = organizationRepository
                .findById(OrganizationId.of(orgId))
                .orElseThrow(() -> new DomainException("ORGANIZATION_NOT_FOUND", "Organization not found"));

        if (!organization.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify organization from another tenant");
        }

        if (!organization.isActive()) {
            throw new DomainException("ORGANIZATION_INACTIVE", "Cannot assign patterns to an inactive organization");
        }

        // Validate that pattern IDs exist and belong to the same tenant
        if (fiscalYearPatternId != null) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fiscal_year_pattern WHERE id = ? AND tenant_id = ?",
                    Long.class,
                    fiscalYearPatternId,
                    tenantId);
            if (count == null || count == 0) {
                throw new DomainException("PATTERN_NOT_FOUND", "Fiscal year pattern not found in this tenant");
            }
        }
        if (monthlyPeriodPatternId != null) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM monthly_period_pattern WHERE id = ? AND tenant_id = ?",
                    Long.class,
                    monthlyPeriodPatternId,
                    tenantId);
            if (count == null || count == 0) {
                throw new DomainException("PATTERN_NOT_FOUND", "Monthly period pattern not found in this tenant");
            }
        }

        organization.assignPatterns(fiscalYearPatternId, monthlyPeriodPatternId);
        organizationRepository.save(organization);

        log.info(
                "Assigned patterns to organization {} (fiscalYear: {}, monthlyPeriod: {})",
                orgId,
                fiscalYearPatternId,
                monthlyPeriodPatternId);
    }

    private static OrganizationTreeNode makeUnmodifiable(OrganizationTreeNode node) {
        List<OrganizationTreeNode> unmodifiableChildren = node.children().stream()
                .map(AdminOrganizationService::makeUnmodifiable)
                .toList();
        return new OrganizationTreeNode(
                node.id(),
                node.code(),
                node.name(),
                node.level(),
                node.status(),
                node.memberCount(),
                Collections.unmodifiableList(unmodifiableChildren));
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // DTOs

    public record OrganizationRow(
            UUID id,
            UUID tenantId,
            UUID parentId,
            String parentName,
            String code,
            String name,
            int level,
            String status,
            long memberCount,
            UUID fiscalYearPatternId,
            UUID monthlyPeriodPatternId,
            Instant createdAt,
            Instant updatedAt) {}

    public record OrganizationPage(List<OrganizationRow> content, long totalElements, int totalPages, int number) {}

    public record OrganizationMemberRow(
            UUID id,
            String email,
            String displayName,
            UUID managerId,
            String managerName,
            Boolean managerIsActive,
            boolean isActive) {}

    public record OrganizationMemberPage(
            List<OrganizationMemberRow> content, long totalElements, int totalPages, int number) {}

    public record OrganizationTreeNode(
            UUID id,
            String code,
            String name,
            int level,
            String status,
            long memberCount,
            List<OrganizationTreeNode> children) {}
}
