package com.worklog.application.service

import com.worklog.domain.member.Member
import com.worklog.domain.member.MemberId
import com.worklog.domain.organization.OrganizationId
import com.worklog.domain.shared.DomainException
import com.worklog.domain.tenant.TenantId
import com.worklog.infrastructure.persistence.JdbcUserRepository
import com.worklog.infrastructure.repository.JdbcMemberRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant
import java.util.*

class ProfileServiceTest {
    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val memberRepository = mockk<JdbcMemberRepository>()
    private val userRepository = mockk<JdbcUserRepository>()
    private val userContextService = mockk<UserContextService>()

    private lateinit var profileService: ProfileService

    private val testEmail = "user@example.com"
    private val testMemberId = UUID.randomUUID()
    private val testTenantId = UUID.randomUUID()
    private val testOrgId = UUID.randomUUID()
    private val testManagerId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        clearMocks(jdbcTemplate, memberRepository, userRepository, userContextService)
        profileService = ProfileService(jdbcTemplate, memberRepository, userRepository, userContextService)
    }

    @Nested
    inner class GetProfile {
        @Test
        fun `returns profile with organization and manager names`() {
            every { jdbcTemplate.query(any<String>(), any<RowMapper<ProfileService.ProfileRow>>(), any()) } returns
                listOf(
                    ProfileService.ProfileRow(
                        testMemberId, testEmail, "Test User",
                        "Engineering Org", "Manager Name", true,
                    ),
                )

            val result = profileService.getProfile(testEmail)

            assertNotNull(result)
            assertEquals(testMemberId, result.id())
            assertEquals(testEmail, result.email())
            assertEquals("Test User", result.displayName())
            assertEquals("Engineering Org", result.organizationName())
            assertEquals("Manager Name", result.managerName())
            assertTrue(result.isActive)
        }

        @Test
        fun `throws MEMBER_NOT_FOUND when no member exists`() {
            every { jdbcTemplate.query(any<String>(), any<RowMapper<ProfileService.ProfileRow>>(), any()) } returns
                emptyList()

            val ex = assertThrows(DomainException::class.java) {
                profileService.getProfile(testEmail)
            }
            assertEquals("MEMBER_NOT_FOUND", ex.errorCode)
        }
    }

    @Nested
    inner class UpdateProfile {
        @Test
        fun `updates display name without email change`() {
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "Old Name")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.save(any()) } just Runs

            val result = profileService.updateProfile(testEmail, "New Name", testEmail)

            assertFalse(result.emailChanged())
            verify { memberRepository.save(any()) }
            verify(exactly = 0) { userRepository.save(any()) }
        }

        @Test
        fun `updates email and syncs users table`() {
            val newEmail = "new@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.empty()
            every { userRepository.findByEmail(newEmail) } returns Optional.empty()
            every { memberRepository.save(any()) } just Runs
            val user = mockk<com.worklog.domain.user.User>(relaxed = true)
            every { userRepository.findByEmail(testEmail) } returns Optional.of(user)
            every { userRepository.save(any()) } returns user

            val result = profileService.updateProfile(testEmail, "User", newEmail)

            assertTrue(result.emailChanged())
            verify { memberRepository.save(any()) }
            verify { userRepository.save(any()) }
        }

        @Test
        fun `throws DUPLICATE_EMAIL when email exists in tenant members`() {
            val newEmail = "taken@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            val otherMember = createTestMember(newEmail, "Other")
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.of(otherMember)

            val ex = assertThrows(DomainException::class.java) {
                profileService.updateProfile(testEmail, "User", newEmail)
            }
            assertEquals("DUPLICATE_EMAIL", ex.errorCode)
        }

        @Test
        fun `throws DUPLICATE_EMAIL when email exists in users table`() {
            val newEmail = "taken-global@example.com"
            every { userContextService.resolveUserMemberId(testEmail) } returns testMemberId
            every { userContextService.resolveUserTenantId(testEmail) } returns testTenantId
            val member = createTestMember(testEmail, "User")
            every { memberRepository.findById(MemberId.of(testMemberId)) } returns Optional.of(member)
            every { memberRepository.findByEmail(TenantId.of(testTenantId), newEmail) } returns Optional.empty()
            val existingUser = mockk<com.worklog.domain.user.User>()
            every { userRepository.findByEmail(newEmail) } returns Optional.of(existingUser)

            val ex = assertThrows(DomainException::class.java) {
                profileService.updateProfile(testEmail, "User", newEmail)
            }
            assertEquals("DUPLICATE_EMAIL", ex.errorCode)
        }
    }

    private fun createTestMember(email: String, displayName: String): Member {
        return Member(
            MemberId.of(testMemberId),
            TenantId.of(testTenantId),
            OrganizationId.of(testOrgId),
            email,
            displayName,
            MemberId.of(testManagerId),
            true,
            Instant.now(),
        )
    }
}
