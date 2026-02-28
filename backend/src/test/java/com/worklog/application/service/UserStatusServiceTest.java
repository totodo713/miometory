package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.member.Member;
import com.worklog.domain.member.TenantAffiliationStatus;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.User;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import com.worklog.infrastructure.repository.OrganizationRepository;
import com.worklog.infrastructure.repository.TenantRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserStatusService")
class UserStatusServiceTest {

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcUserSessionRepository sessionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private UserStatusService userStatusService;

    private static final String TEST_EMAIL = "user@example.com";

    @Nested
    @DisplayName("getUserStatus")
    class GetUserStatus {

        @Test
        @DisplayName("returns UNAFFILIATED when user has no members")
        void unaffiliated() {
            var user = createTestUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(Collections.emptyList());
            when(tenantRepository.findNamesByIds(any())).thenReturn(Map.of());
            when(organizationRepository.findNamesByIds(any())).thenReturn(Map.of());

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.UNAFFILIATED, result.state());
            assertTrue(result.memberships().isEmpty());
        }

        @Test
        @DisplayName("returns AFFILIATED_NO_ORG when member has no organization")
        void affiliatedNoOrg() {
            var user = createTestUser();
            TenantId tenantId = TenantId.generate();
            var member = Member.createForTenant(tenantId, TEST_EMAIL, "Test");
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(List.of(member));
            when(tenantRepository.findNamesByIds(any())).thenReturn(Map.of(tenantId, "Test Tenant"));
            when(organizationRepository.findNamesByIds(any())).thenReturn(Map.of());

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.AFFILIATED_NO_ORG, result.state());
            assertEquals(1, result.memberships().size());
            assertEquals("Test Tenant", result.memberships().get(0).tenantName());
            assertNull(result.memberships().get(0).organizationName());
        }

        @Test
        @DisplayName("returns FULLY_ASSIGNED when member has organization")
        void fullyAssigned() {
            var user = createTestUser();
            TenantId tenantId = TenantId.generate();
            OrganizationId orgId = OrganizationId.generate();
            var member = Member.create(tenantId, orgId, TEST_EMAIL, "Test", null);
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(memberRepository.findAllByEmail(TEST_EMAIL)).thenReturn(List.of(member));
            when(tenantRepository.findNamesByIds(any())).thenReturn(Map.of(tenantId, "Test Tenant"));
            when(organizationRepository.findNamesByIds(any())).thenReturn(Map.of(orgId, "Test Org"));

            var result = userStatusService.getUserStatus(TEST_EMAIL);

            assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result.state());
            assertEquals("Test Tenant", result.memberships().get(0).tenantName());
            assertEquals("Test Org", result.memberships().get(0).organizationName());
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user not found")
        void userNotFound() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(DomainException.class, () -> userStatusService.getUserStatus(TEST_EMAIL));
            assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("selectTenant")
    class SelectTenant {

        @Test
        @DisplayName("updates session selected tenant when membership exists")
        void selectValidTenant() {
            TenantId tenantId = TenantId.generate();
            UUID sessionId = UUID.randomUUID();
            var member = Member.createForTenant(tenantId, TEST_EMAIL, "Test");
            when(memberRepository.findByEmail(tenantId, TEST_EMAIL)).thenReturn(Optional.of(member));

            assertDoesNotThrow(() -> userStatusService.selectTenant(TEST_EMAIL, tenantId.value(), sessionId));

            verify(sessionRepository).updateSelectedTenant(sessionId, tenantId);
        }

        @Test
        @DisplayName("throws INVALID_TENANT_SELECTION when no membership for tenant")
        void invalidTenantSelection() {
            TenantId tenantId = TenantId.generate();
            UUID sessionId = UUID.randomUUID();
            when(memberRepository.findByEmail(tenantId, TEST_EMAIL)).thenReturn(Optional.empty());

            var ex = assertThrows(
                    DomainException.class,
                    () -> userStatusService.selectTenant(TEST_EMAIL, tenantId.value(), sessionId));
            assertEquals("INVALID_TENANT_SELECTION", ex.getErrorCode());
        }
    }

    private User createTestUser() {
        return User.create(TEST_EMAIL, "Test User", "$2a$10$hashedpasswordplaceholder", RoleId.generate());
    }
}
