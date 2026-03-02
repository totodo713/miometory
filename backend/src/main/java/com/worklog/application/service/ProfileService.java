package com.worklog.application.service;

import com.worklog.api.dto.ProfileResponse;
import com.worklog.api.dto.UpdateProfileResponse;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMemberRepository memberRepository;
    private final JdbcUserRepository userRepository;
    private final UserContextService userContextService;

    public ProfileService(
            JdbcTemplate jdbcTemplate,
            JdbcMemberRepository memberRepository,
            JdbcUserRepository userRepository,
            UserContextService userContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.userContextService = userContextService;
    }

    public ProfileResponse getProfile(String email) {
        String sql = """
                SELECT m.id, m.email, m.display_name, o.name AS organization_name,
                       mgr.display_name AS manager_name, m.is_active
                FROM members m
                LEFT JOIN organizations o ON m.organization_id = o.id
                LEFT JOIN members mgr ON m.manager_id = mgr.id
                WHERE LOWER(m.email) = LOWER(?)
                LIMIT 1
                """;

        List<ProfileRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProfileRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("organization_name"),
                        rs.getString("manager_name"),
                        rs.getBoolean("is_active")),
                email);

        if (rows.isEmpty()) {
            throw new DomainException("MEMBER_NOT_FOUND", "Member not found for email: " + email);
        }

        ProfileRow row = rows.get(0);
        return new ProfileResponse(
                row.id(), row.email(), row.displayName(), row.organizationName(), row.managerName(), row.isActive());
    }

    @Transactional
    public UpdateProfileResponse updateProfile(String currentEmail, String newDisplayName, String newEmail) {
        UUID memberId = userContextService.resolveUserMemberId(currentEmail);
        UUID tenantId = userContextService.resolveUserTenantId(currentEmail);

        var member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found"));

        boolean emailChanged = !currentEmail.equalsIgnoreCase(newEmail);

        if (emailChanged) {
            // Check tenant-scoped uniqueness
            var existingMember = memberRepository.findByEmail(TenantId.of(tenantId), newEmail);
            if (existingMember.isPresent()) {
                throw new DomainException("DUPLICATE_EMAIL", "A member with this email already exists in this tenant");
            }

            // Check global uniqueness
            var existingUser = userRepository.findByEmail(newEmail);
            if (existingUser.isPresent()) {
                throw new DomainException("DUPLICATE_EMAIL", "A user with this email already exists");
            }
        }

        // Update member (keeps existing managerId via member.update)
        member.update(newEmail, newDisplayName, member.getManagerId());
        memberRepository.save(member);

        if (emailChanged) {
            // Sync users table
            var user = userRepository
                    .findByEmail(currentEmail)
                    .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User account not found"));
            user.updateEmail(newEmail);
            userRepository.save(user);
        }

        return new UpdateProfileResponse(emailChanged);
    }

    public record ProfileRow(
            UUID id, String email, String displayName, String organizationName, String managerName, boolean isActive) {}
}
