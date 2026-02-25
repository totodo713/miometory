package com.worklog.application.service;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.application.command.UpdateMemberCommand;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.User;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.RoleRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.shared.AdminRole;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for tenant-scoped member management by Tenant Admins.
 */
@Service
@Transactional
public class AdminMemberService {

    private static final Logger log = LoggerFactory.getLogger(AdminMemberService.class);

    private final JdbcMemberRepository memberRepository;
    private final JdbcUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminMemberService(
            JdbcMemberRepository memberRepository,
            JdbcUserRepository userRepository,
            RoleRepository roleRepository,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lists members within a tenant with optional filters.
     */
    @Transactional(readOnly = true)
    public MemberPage listMembers(
            UUID tenantId, String search, UUID organizationId, Boolean isActive, int page, int size) {
        var sb = new StringBuilder("""
            SELECT m.id, m.email, m.display_name, m.organization_id, m.manager_id, m.is_active,
                   mgr.display_name AS manager_name
            FROM members m
            LEFT JOIN members mgr ON mgr.id = m.manager_id
            WHERE m.tenant_id = ?
            """);

        var countSb = new StringBuilder("SELECT COUNT(*) FROM members m WHERE m.tenant_id = ?");
        var params = new java.util.ArrayList<Object>();
        params.add(tenantId);
        var countParams = new java.util.ArrayList<Object>();
        countParams.add(tenantId);

        if (search != null && !search.isBlank()) {
            String clause = " AND (LOWER(m.email) LIKE ? ESCAPE '\\' OR LOWER(m.display_name) LIKE ? ESCAPE '\\')";
            sb.append(clause);
            countSb.append(clause);
            String pattern = "%" + escapeLike(search).toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            countParams.add(pattern);
            countParams.add(pattern);
        }
        if (organizationId != null) {
            String clause = " AND m.organization_id = ?";
            sb.append(clause);
            countSb.append(clause);
            params.add(organizationId);
            countParams.add(organizationId);
        }
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

        List<MemberRow> content = jdbcTemplate.query(
                sb.toString(),
                (rs, rowNum) -> new MemberRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getObject("organization_id", UUID.class) != null
                                ? rs.getObject("organization_id", UUID.class).toString()
                                : null,
                        rs.getObject("manager_id", UUID.class) != null
                                ? rs.getObject("manager_id", UUID.class).toString()
                                : null,
                        rs.getString("manager_name"),
                        rs.getBoolean("is_active")),
                params.toArray());

        int totalPages = (int) Math.ceil((double) total / size);
        return new MemberPage(content, total, totalPages, page);
    }

    /**
     * Invites a new member: creates both User (UNVERIFIED) and Member records.
     * Returns both the member ID and a temporary password for the invited user.
     */
    public InviteMemberResult inviteMember(InviteMemberCommand command, UUID tenantId) {
        if (userRepository.existsByEmail(command.email())) {
            throw new DomainException("DUPLICATE_EMAIL", "A user with this email already exists");
        }

        var userRole = roleRepository
                .findByName("USER")
                .orElseThrow(() -> new DomainException("ROLE_NOT_FOUND", "USER role not found"));

        // Generate a real temporary password and encode it
        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        String hashedPassword = passwordEncoder.encode(tempPassword);

        var user = User.create(command.email(), command.displayName(), hashedPassword, userRole.getId());
        // Admin-provisioned users are pre-verified (no email confirmation needed)
        user.verifyEmail();
        userRepository.save(user);

        var member = Member.create(
                TenantId.of(tenantId),
                OrganizationId.of(command.organizationId()),
                command.email(),
                command.displayName(),
                command.managerId() != null ? MemberId.of(command.managerId()) : null);
        memberRepository.save(member);

        return new InviteMemberResult(member.getId().value(), tempPassword);
    }

    public record InviteMemberResult(UUID memberId, String temporaryPassword) {}

    /**
     * Updates an existing member's details.
     * Note: organizationId changes are not yet supported. The field is accepted in the command
     * for forward compatibility but is currently ignored.
     */
    public void updateMember(UpdateMemberCommand command, UUID tenantId) {
        var member = memberRepository
                .findById(MemberId.of(command.memberId()))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }

        member.update(
                command.email(),
                command.displayName(),
                command.managerId() != null ? MemberId.of(command.managerId()) : null);
        memberRepository.save(member);
    }

    /**
     * Deactivates a member with supervisor reassignment logic (T015a).
     */
    public void deactivateMember(UUID memberId, UUID tenantId) {
        var member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }

        // T015a: Handle direct reports when deactivating a supervisor
        List<Member> directReports = memberRepository.findDirectSubordinates(member.getId());
        if (!directReports.isEmpty()) {
            MemberId parentManager = member.getManagerId();
            for (Member report : directReports) {
                report.assignManager(parentManager);
                memberRepository.save(report);
            }

            if (parentManager == null) {
                log.warn(
                        "Deactivated member {} had {} direct reports but no parent manager. "
                                + "Reports now have no manager — manual reassignment needed.",
                        memberId,
                        directReports.size());
            }
        }

        member.deactivate();
        memberRepository.save(member);
    }

    /**
     * Activates a previously deactivated member.
     */
    public void activateMember(UUID memberId, UUID tenantId) {
        var member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot modify member from another tenant");
        }

        member.activate();
        memberRepository.save(member);
    }

    /**
     * Assigns the TENANT_ADMIN role to a member (validates same tenant).
     */
    public void assignTenantAdmin(UUID memberId, UUID tenantId) {
        var member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        if (!member.getTenantId().value().equals(tenantId)) {
            throw new DomainException("TENANT_MISMATCH", "Cannot assign role to member from another tenant");
        }

        var user = userRepository
                .findByEmail(member.getEmail())
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User account not found for member"));

        var tenantAdminRole = roleRepository
                .findByName(AdminRole.TENANT_ADMIN)
                .orElseThrow(() -> new DomainException("ROLE_NOT_FOUND", "TENANT_ADMIN role not found"));

        user.changeRole(tenantAdminRole.getId());
        userRepository.save(user);
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // DTOs

    public record MemberRow(
            String id,
            String email,
            String displayName,
            String organizationId,
            String managerId,
            String managerName,
            boolean isActive) {}

    public record MemberPage(List<MemberRow> content, long totalElements, int totalPages, int number) {}
}
