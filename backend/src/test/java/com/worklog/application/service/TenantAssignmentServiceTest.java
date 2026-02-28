package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.member.Member;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantAssignmentService")
class TenantAssignmentServiceTest {

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private JdbcMemberRepository memberRepository;

    @InjectMocks
    private TenantAssignmentService service;

    @Nested
    @DisplayName("searchUsersForAssignment")
    class SearchUsers {

        @Test
        @DisplayName("returns users with isAlreadyInTenant=true when in tenant")
        void searchWithFlag() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hashedpw1234567890ab", RoleId.generate());
            when(userRepository.searchByEmailPartial("test")).thenReturn(List.of(user));
            when(memberRepository.findExistingEmailsInTenant(eq(TenantId.of(tenantId)), any()))
                    .thenReturn(Set.of("test@example.com"));

            var result = service.searchUsersForAssignment("test", tenantId);

            assertEquals(1, result.users().size());
            assertTrue(result.users().get(0).isAlreadyInTenant());
        }

        @Test
        @DisplayName("returns isAlreadyInTenant=false when not in tenant")
        void searchNotInTenant() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("new@example.com", "New", "$2a$10$hashedpw1234567890ab", RoleId.generate());
            when(userRepository.searchByEmailPartial("new")).thenReturn(List.of(user));
            when(memberRepository.findExistingEmailsInTenant(eq(TenantId.of(tenantId)), any()))
                    .thenReturn(Set.of());

            var result = service.searchUsersForAssignment("new", tenantId);

            assertEquals(1, result.users().size());
            assertFalse(result.users().get(0).isAlreadyInTenant());
        }
    }

    @Nested
    @DisplayName("assignUserToTenant")
    class AssignUser {

        @Test
        @DisplayName("creates member when not already in tenant")
        void assignSuccess() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hashedpw1234567890ab", RoleId.generate());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.assignUserToTenant(user.getId().value(), tenantId, "Display Name"));

            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("throws DUPLICATE_TENANT_ASSIGNMENT when already in tenant")
        void assignDuplicate() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hashedpw1234567890ab", RoleId.generate());
            var member = Member.createForTenant(TenantId.of(tenantId), "test@example.com", "Test");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.of(member));

            var ex = assertThrows(
                    DomainException.class,
                    () -> service.assignUserToTenant(user.getId().value(), tenantId, "Test"));
            assertEquals("DUPLICATE_TENANT_ASSIGNMENT", ex.getErrorCode());
        }

        @Test
        @DisplayName("throws DUPLICATE_TENANT_ASSIGNMENT on race condition (DataIntegrityViolation)")
        void assignRaceConditionDuplicate() {
            UUID tenantId = UUID.randomUUID();
            var user = User.create("test@example.com", "Test", "$2a$10$hashedpw1234567890ab", RoleId.generate());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(memberRepository.findByEmail(TenantId.of(tenantId), "test@example.com"))
                    .thenReturn(Optional.empty());
            doThrow(new DataIntegrityViolationException("duplicate key"))
                    .when(memberRepository).save(any(Member.class));

            var ex = assertThrows(
                    DomainException.class,
                    () -> service.assignUserToTenant(user.getId().value(), tenantId, "Test"));
            assertEquals("DUPLICATE_TENANT_ASSIGNMENT", ex.getErrorCode());
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user doesn't exist")
        void assignUserNotFound() {
            UUID userId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.empty());

            var ex = assertThrows(DomainException.class, () -> service.assignUserToTenant(userId, tenantId, "Test"));
            assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        }
    }
}
