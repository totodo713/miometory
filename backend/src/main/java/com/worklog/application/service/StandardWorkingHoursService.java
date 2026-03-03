package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.Organization;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.Tenant;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StandardWorkingHoursService {

    private final JdbcMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    public StandardWorkingHoursService(
            JdbcMemberRepository memberRepository,
            OrganizationRepository organizationRepository,
            TenantRepository tenantRepository,
            SystemDefaultSettingsRepository systemDefaultSettingsRepository) {
        this.memberRepository = memberRepository;
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
        this.systemDefaultSettingsRepository = systemDefaultSettingsRepository;
    }

    /**
     * Resolves the effective standard daily hours for a member.
     *
     * Resolution chain: Member -> Organization hierarchy (child->parent->root) -> Tenant -> System default (8.0h)
     */
    public StandardHoursResolution resolveStandardDailyHours(UUID memberId) {
        Member member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + memberId));

        // Step 1: Check member-level setting
        if (member.getStandardDailyHours() != null) {
            return new StandardHoursResolution(member.getStandardDailyHours(), "member");
        }

        // Step 2: Walk organization hierarchy
        if (member.getOrganizationId() != null) {
            Organization current =
                    organizationRepository.findById(member.getOrganizationId()).orElse(null);
            while (current != null) {
                if (current.getStandardDailyHours() != null) {
                    return new StandardHoursResolution(
                            current.getStandardDailyHours(),
                            "organization:" + current.getId().value());
                }
                if (current.getParentId() != null) {
                    current = organizationRepository
                            .findById(current.getParentId())
                            .orElse(null);
                } else {
                    current = null;
                }
            }
        }

        // Step 3: Check tenant default
        Tenant tenant = tenantRepository.findById(member.getTenantId()).orElse(null);
        if (tenant != null && tenant.getStandardDailyHours() != null) {
            return new StandardHoursResolution(tenant.getStandardDailyHours(), "tenant");
        }

        // Step 4: System default
        BigDecimal systemDefault = systemDefaultSettingsRepository.getDefaultStandardDailyHours();
        return new StandardHoursResolution(systemDefault, "system");
    }
}
