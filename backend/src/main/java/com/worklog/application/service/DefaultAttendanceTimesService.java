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
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAttendanceTimesService {

    private final JdbcMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final SystemDefaultSettingsRepository systemDefaultSettingsRepository;

    public DefaultAttendanceTimesService(
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
     * Resolves the effective default attendance times for a member.
     *
     * Resolution chain: Member -> Organization hierarchy (child->parent->root) -> Tenant -> System (09:00/18:00)
     */
    @Transactional(readOnly = true)
    public AttendanceTimesResolution resolveDefaultAttendanceTimes(UUID memberId) {
        Member member = memberRepository
                .findById(MemberId.of(memberId))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + memberId));

        // Step 1: Check member-level setting
        if (member.getDefaultStartTime() != null || member.getDefaultEndTime() != null) {
            return new AttendanceTimesResolution(member.getDefaultStartTime(), member.getDefaultEndTime(), "member");
        }

        // Step 2: Walk organization hierarchy
        if (member.getOrganizationId() != null) {
            Organization current =
                    organizationRepository.findById(member.getOrganizationId()).orElse(null);
            while (current != null) {
                if (current.getDefaultStartTime() != null || current.getDefaultEndTime() != null) {
                    return new AttendanceTimesResolution(
                            current.getDefaultStartTime(),
                            current.getDefaultEndTime(),
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
        if (tenant != null && (tenant.getDefaultStartTime() != null || tenant.getDefaultEndTime() != null)) {
            return new AttendanceTimesResolution(tenant.getDefaultStartTime(), tenant.getDefaultEndTime(), "tenant");
        }

        // Step 4: System default
        LocalTime[] systemDefaults = systemDefaultSettingsRepository.getDefaultAttendanceTimes();
        return new AttendanceTimesResolution(systemDefaults[0], systemDefaults[1], "system");
    }
}
