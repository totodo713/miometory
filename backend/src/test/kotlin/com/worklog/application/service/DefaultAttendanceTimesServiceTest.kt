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
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DefaultAttendanceTimesServiceTest {
    @Mock private lateinit var memberRepository: JdbcMemberRepository

    @Mock private lateinit var organizationRepository: OrganizationRepository

    @Mock private lateinit var tenantRepository: TenantRepository

    @Mock private lateinit var systemDefaultSettingsRepository: SystemDefaultSettingsRepository

    private lateinit var service: DefaultAttendanceTimesService

    private val memberId = UUID.randomUUID()
    private val tenantId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val parentOrgId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service =
            DefaultAttendanceTimesService(
                memberRepository,
                organizationRepository,
                tenantRepository,
                systemDefaultSettingsRepository,
            )
    }

    @Test
    fun `should return member default times when set`() {
        val member = createMember(
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(17, 30),
        )
        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))

        val result = service.resolveDefaultAttendanceTimes(memberId)

        assertEquals(LocalTime.of(8, 30), result.startTime())
        assertEquals(LocalTime.of(17, 30), result.endTime())
        assertEquals("member", result.source())
    }

    @Test
    fun `should walk organization hierarchy when member has no setting`() {
        val member = createMember(startTime = null, endTime = null)
        val org = createOrganization(orgId, parentId = parentOrgId, startTime = null, endTime = null)
        val parentOrg = createOrganization(
            parentOrgId,
            parentId = null,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 0),
        )

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(organizationRepository.findById(OrganizationId.of(parentOrgId)))
            .thenReturn(Optional.of(parentOrg))

        val result = service.resolveDefaultAttendanceTimes(memberId)

        assertEquals(LocalTime.of(8, 0), result.startTime())
        assertEquals(LocalTime.of(17, 0), result.endTime())
        assertEquals("organization:$parentOrgId", result.source())
    }

    @Test
    fun `should return tenant default when organization chain has no setting`() {
        val member = createMember(startTime = null, endTime = null)
        val org = createOrganization(orgId, parentId = null, startTime = null, endTime = null)
        val tenant = createTenant(
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(19, 0),
        )

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))

        val result = service.resolveDefaultAttendanceTimes(memberId)

        assertEquals(LocalTime.of(10, 0), result.startTime())
        assertEquals(LocalTime.of(19, 0), result.endTime())
        assertEquals("tenant", result.source())
    }

    @Test
    fun `should return system default when all levels are null`() {
        val member = createMember(startTime = null, endTime = null)
        val org = createOrganization(orgId, parentId = null, startTime = null, endTime = null)
        val tenant = createTenant(startTime = null, endTime = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(organizationRepository.findById(OrganizationId.of(orgId)))
            .thenReturn(Optional.of(org))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.defaultAttendanceTimes)
            .thenReturn(arrayOf(LocalTime.of(9, 0), LocalTime.of(18, 0)))

        val result = service.resolveDefaultAttendanceTimes(memberId)

        assertEquals(LocalTime.of(9, 0), result.startTime())
        assertEquals(LocalTime.of(18, 0), result.endTime())
        assertEquals("system", result.source())
    }

    @Test
    fun `should return system default when member has no organization`() {
        val member = createMember(startTime = null, endTime = null, orgId = null)
        val tenant = createTenant(startTime = null, endTime = null)

        `when`(memberRepository.findById(MemberId.of(memberId))).thenReturn(Optional.of(member))
        `when`(tenantRepository.findById(TenantId.of(tenantId))).thenReturn(Optional.of(tenant))
        `when`(systemDefaultSettingsRepository.defaultAttendanceTimes)
            .thenReturn(arrayOf(LocalTime.of(9, 0), LocalTime.of(18, 0)))

        val result = service.resolveDefaultAttendanceTimes(memberId)

        assertEquals(LocalTime.of(9, 0), result.startTime())
        assertEquals(LocalTime.of(18, 0), result.endTime())
        assertEquals("system", result.source())
    }

    // --- Helper methods ---

    private fun createMember(startTime: LocalTime?, endTime: LocalTime?, orgId: UUID? = this.orgId): Member {
        val member = mock(Member::class.java)
        lenient().`when`(member.id).thenReturn(MemberId.of(memberId))
        lenient().`when`(member.tenantId).thenReturn(TenantId.of(tenantId))
        lenient().`when`(member.organizationId).thenReturn(orgId?.let { OrganizationId.of(it) })
        lenient().`when`(member.defaultStartTime).thenReturn(startTime)
        lenient().`when`(member.defaultEndTime).thenReturn(endTime)
        return member
    }

    private fun createOrganization(
        id: UUID,
        parentId: UUID?,
        startTime: LocalTime?,
        endTime: LocalTime?,
    ): Organization {
        val org = mock(Organization::class.java)
        lenient().`when`(org.id).thenReturn(OrganizationId.of(id))
        lenient().`when`(org.tenantId).thenReturn(TenantId.of(tenantId))
        lenient().`when`(org.parentId).thenReturn(parentId?.let { OrganizationId.of(it) })
        lenient().`when`(org.defaultStartTime).thenReturn(startTime)
        lenient().`when`(org.defaultEndTime).thenReturn(endTime)
        return org
    }

    private fun createTenant(startTime: LocalTime?, endTime: LocalTime?): Tenant {
        val tenant = mock(Tenant::class.java)
        lenient().`when`(tenant.defaultStartTime).thenReturn(startTime)
        lenient().`when`(tenant.defaultEndTime).thenReturn(endTime)
        return tenant
    }
}
