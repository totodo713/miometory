package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.TenantAffiliationStatus;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserStatusService {

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcUserSessionRepository sessionRepository;

    public UserStatusService(
            JdbcUserRepository userRepository,
            JdbcMemberRepository memberRepository,
            JdbcUserSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
    }

    public UserStatusResponse getUserStatus(String email) {
        var user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found: " + email));

        List<Member> members = memberRepository.findAllByEmail(email);
        TenantAffiliationStatus state = TenantAffiliationStatus.fromMembers(members);

        List<MembershipDto> memberships = members.stream()
                .map(m -> new MembershipDto(
                        m.getId().value().toString(),
                        m.getTenantId().value().toString(),
                        null,
                        m.hasOrganization() ? m.getOrganizationId().value().toString() : null,
                        null))
                .toList();

        return new UserStatusResponse(user.getId().value().toString(), user.getEmail(), state, memberships);
    }

    @Transactional
    public void selectTenant(String email, UUID tenantId, UUID sessionId) {
        TenantId tid = TenantId.of(tenantId);

        memberRepository
                .findByEmail(tid, email)
                .orElseThrow(() ->
                        new DomainException("INVALID_TENANT_SELECTION", "No membership found for tenant: " + tenantId));

        sessionRepository.updateSelectedTenant(sessionId, tid);
    }

    public record UserStatusResponse(
            String userId, String email, TenantAffiliationStatus state, List<MembershipDto> memberships) {}

    public record MembershipDto(
            String memberId, String tenantId, String tenantName, String organizationId, String organizationName) {}
}
