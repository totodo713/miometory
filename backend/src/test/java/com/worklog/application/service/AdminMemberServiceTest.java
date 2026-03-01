package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.domain.member.Member;
import com.worklog.domain.role.Role;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.user.User;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.RoleRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("AdminMemberService")
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminMemberService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID INVITED_BY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new AdminMemberService(memberRepository, userRepository, roleRepository, jdbcTemplate, passwordEncoder);
    }

    @Nested
    @DisplayName("inviteMember")
    class InviteMember {

        @Test
        @DisplayName("should create user and member for new email")
        void newUser() {
            var command = new InviteMemberCommand("new@example.com", "New User", ORG_ID, null, INVITED_BY);
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());
            when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
            var role = mock(Role.class);
            when(role.getId()).thenReturn(RoleId.of(UUID.randomUUID()));
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(any())).thenReturn("hashed");

            var result = service.inviteMember(command, TENANT_ID);

            assertNotNull(result.memberId());
            assertNotNull(result.temporaryPassword());
            assertFalse(result.isExistingUser());
            verify(userRepository).save(any(User.class));
            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("should skip user creation for existing email and create member only")
        void existingUser() {
            var command = new InviteMemberCommand("existing@example.com", "Existing", ORG_ID, null, INVITED_BY);
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of());
            var existingUser = mock(User.class);
            when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

            var result = service.inviteMember(command, TENANT_ID);

            assertNotNull(result.memberId());
            assertNull(result.temporaryPassword());
            assertTrue(result.isExistingUser());
            verify(userRepository, never()).save(any(User.class));
            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("should reject when member already exists in same tenant")
        void duplicateMemberInSameTenant() {
            var command = new InviteMemberCommand("dup@example.com", "Dup", ORG_ID, null, INVITED_BY);
            when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
                    .thenReturn(Set.of("dup@example.com"));

            var ex = assertThrows(
                    com.worklog.domain.shared.DomainException.class, () -> service.inviteMember(command, TENANT_ID));
            assertEquals("DUPLICATE_MEMBER", ex.getErrorCode());
            verify(userRepository, never()).findByEmail(any());
            verify(memberRepository, never()).save(any());
        }
    }
}
