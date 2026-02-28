package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.TenantAffiliationStatus;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserStatusService {

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;
    private final JdbcUserSessionRepository sessionRepository;
    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;

    public UserStatusService(
            JdbcUserRepository userRepository,
            JdbcMemberRepository memberRepository,
            JdbcUserSessionRepository sessionRepository,
            TenantRepository tenantRepository,
            OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
    }

    public UserStatusResponse getUserStatus(String email) {
        var user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found: " + email));

        List<Member> members = memberRepository.findAllByEmail(email);
        TenantAffiliationStatus state = TenantAffiliationStatus.fromMembers(members);

        // Batch lookup tenant and organization names from projection tables
        Set<TenantId> tenantIds = members.stream().map(Member::getTenantId).collect(Collectors.toSet());
        Map<TenantId, String> tenantNames = tenantRepository.findNamesByIds(tenantIds);

        Set<OrganizationId> orgIds = members.stream()
                .filter(Member::hasOrganization)
                .map(Member::getOrganizationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<OrganizationId, String> orgNames = organizationRepository.findNamesByIds(orgIds);

        List<MembershipDto> memberships = members.stream()
                .map(m -> new MembershipDto(
                        m.getId().value().toString(),
                        m.getTenantId().value().toString(),
                        tenantNames.get(m.getTenantId()),
                        m.hasOrganization() ? m.getOrganizationId().value().toString() : null,
                        m.hasOrganization() ? orgNames.get(m.getOrganizationId()) : null))
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
