package com.worklog.application.service

import com.worklog.domain.member.Member
import com.worklog.domain.member.MemberId
import com.worklog.domain.organization.Organization
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.tenant.Tenant
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.repository.JdbcMemberRepository
import com.worklog.infrastructure.repository.OrganizationRepository
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository
import com.worklog.infrastructure.repository.TenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class StandardWorkingHoursServiceTest {
    @Mock private lateinit var memberRepository: JdbcMemberRepository

    @Mock private lateinit var organizationRepository: OrganizationRepository

    @Mock private lateinit var tenantRepository: TenantRepository

    @Mock private lateinit var systemDefaultSettingsRepository: SystemDefaultSettingsRepository

    private lateinit var service: StandardWorkingHoursService

    private val memberId = UUID.randomUUID()
    private val tenantId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val parentOrgId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service =
            StandardWorkingHoursService(
                memberRepository,
                organizationRepository,
                tenantRepository,
                systemDefaultSettingsRepository,
            )
    }

    @Test
    fun `should return member standard daily hours when set`() {
        val member = createMember(standardDailyHours = BigDecimal("7.50"))
        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("7.50"), result.hours())
        assertEquals("member", result.source())
    }

    @Test
    fun `should walk organization hierarchy when member has no setting`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = parentOrgId, standardDailyHours = null)
        val parentOrg =
            createOrganization(parentOrgId, parentId = null, standardDailyHours = BigDecimal("7.00"))

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(organizationRepository.findById(OrganizationId.of(parentOrgId)))
            .thenReturn(Optional.of(parentOrg))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("7.00"), result.hours())
        assertEquals("organization:$parentOrgId", result.source())
    }

    @Test
    fun `should return tenant default when organization chain has no setting`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = null, standardDailyHours = null)
        val tenant = createTenant(standardDailyHours = BigDecimal("6.00"))

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("6.00"), result.hours())
        assertEquals("tenant", result.source())
    }

    @Test
    fun `should return system default 8h when all levels are null`() {
        val member = createMember(standardDailyHours = null)
        val org = createOrganization(orgId, parentId = null, standardDailyHours = null)
        val tenant = createTenant(standardDailyHours = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.getDefaultStandardDailyHours())
            .thenReturn(BigDecimal("8.0"))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("8.0"), result.hours())
        assertEquals("system", result.source())
    }

    @Test
    fun `should return system default when member has no organization`() {
        val member = createMember(standardDailyHours = null, orgId = null)
        val tenant = createTenant(standardDailyHours = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.getDefaultStandardDailyHours())
            .thenReturn(BigDecimal("8.0"))

        val result = service.resolveStandardDailyHours(memberId)

        assertEquals(BigDecimal("8.0"), result.hours())
        assertEquals("system", result.source())
    }

    // --- Helper methods ---

    private fun createMember(standardDailyHours: BigDecimal?, orgId: UUID? = this.orgId): Member {
        val member = mock(Member::class.java)
        lenient().`when`(member.id).thenReturn(MemberId.of(memberId))
        lenient().`when`(member.tenantId).thenReturn(TenantId.of(tenantId))
        lenient().`when`(member.organizationId).thenReturn(orgId?.let { OrganizationId.of(it) })
        lenient().`when`(member.standardDailyHours).thenReturn(standardDailyHours)
        return member
    }

    private fun createOrganization(id: UUID, parentId: UUID?, standardDailyHours: BigDecimal?): Organization {
        val org = mock(Organization::class.java)
        lenient().`when`(org.id).thenReturn(OrganizationId.of(id))
        lenient().`when`(org.tenantId).thenReturn(TenantId.of(tenantId))
        lenient().`when`(org.parentId).thenReturn(parentId?.let { OrganizationId.of(it) })
        lenient().`when`(org.standardDailyHours).thenReturn(standardDailyHours)
        return org
    }

    private fun createTenant(standardDailyHours: BigDecimal?): Tenant {
        val tenant = mock(Tenant::class.java)
        lenient().`when`(tenant.standardDailyHours).thenReturn(standardDailyHours)
        return tenant
    }
}
