package com.worklog.api;

import com.worklog.api.dto.AssignedProjectsResponse;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.AssignedProjectInfo;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemberController.
 * 
 * Tests the member-related REST endpoints with mocked repositories.
 * These are pure unit tests with no Spring context or HTTP layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberController (Unit)")
class MemberControllerUnitTest {

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcMemberProjectAssignmentRepository memberProjectAssignmentRepository;

    private MemberController controller;

    @BeforeEach
    void setUp() {
        controller = new MemberController(memberRepository, memberProjectAssignmentRepository);
    }

    @Nested
    @DisplayName("GET /api/v1/members/{id}/projects")
    class GetAssignedProjects {

        private UUID memberId;
        private Member testMember;

        @BeforeEach
        void setUp() {
            memberId = UUID.randomUUID();
            testMember = createTestMember(memberId, "test@example.com", "Test User");
        }

        @Test
        @DisplayName("should return assigned projects for existing member")
        void shouldReturnAssignedProjectsForExistingMember() {
            List<AssignedProjectInfo> projectInfos = List.of(
                new AssignedProjectInfo(UUID.randomUUID(), "PROJ1", "Project One"),
                new AssignedProjectInfo(UUID.randomUUID(), "PROJ2", "Project Two")
            );

            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(testMember));
            when(memberProjectAssignmentRepository.findActiveProjectsForMember(any(), any()))
                .thenReturn(projectInfos);

            ResponseEntity<AssignedProjectsResponse> response = controller.getAssignedProjects(memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().count());
            assertEquals(2, response.getBody().projects().size());
            assertEquals("PROJ1", response.getBody().projects().get(0).code());
            assertEquals("Project One", response.getBody().projects().get(0).name());
        }

        @Test
        @DisplayName("should return empty list when member has no projects")
        void shouldReturnEmptyListWhenNoProjects() {
            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(testMember));
            when(memberProjectAssignmentRepository.findActiveProjectsForMember(any(), any()))
                .thenReturn(Collections.emptyList());

            ResponseEntity<AssignedProjectsResponse> response = controller.getAssignedProjects(memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(0, response.getBody().count());
            assertTrue(response.getBody().projects().isEmpty());
        }

        @Test
        @DisplayName("should throw exception for non-existent member")
        void shouldThrowExceptionForNonExistentMember() {
            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.empty());

            DomainException exception = assertThrows(
                DomainException.class,
                () -> controller.getAssignedProjects(memberId)
            );

            assertEquals("MEMBER_NOT_FOUND", exception.getErrorCode());
            assertTrue(exception.getMessage().contains(memberId.toString()));
        }

        @Test
        @DisplayName("should call repository with correct member ID")
        void shouldCallRepositoryWithCorrectMemberId() {
            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(testMember));
            when(memberProjectAssignmentRepository.findActiveProjectsForMember(any(), any()))
                .thenReturn(Collections.emptyList());

            controller.getAssignedProjects(memberId);

            verify(memberProjectAssignmentRepository).findActiveProjectsForMember(
                eq(MemberId.of(memberId)),
                any(LocalDate.class)
            );
        }

        @Test
        @DisplayName("should use today's date for validity check")
        void shouldUseTodaysDateForValidityCheck() {
            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(testMember));
            when(memberProjectAssignmentRepository.findActiveProjectsForMember(any(), any()))
                .thenReturn(Collections.emptyList());

            LocalDate today = LocalDate.now();
            
            controller.getAssignedProjects(memberId);

            verify(memberProjectAssignmentRepository).findActiveProjectsForMember(
                any(),
                eq(today)
            );
        }

        @Test
        @DisplayName("should not call assignment repository if member not found")
        void shouldNotCallAssignmentRepositoryIfMemberNotFound() {
            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.empty());

            assertThrows(DomainException.class, () -> controller.getAssignedProjects(memberId));

            verify(memberProjectAssignmentRepository, never())
                .findActiveProjectsForMember(any(), any());
        }

        @Test
        @DisplayName("should map project info to response correctly")
        void shouldMapProjectInfoToResponseCorrectly() {
            UUID projectId = UUID.randomUUID();
            List<AssignedProjectInfo> projectInfos = List.of(
                new AssignedProjectInfo(projectId, "ABC", "Test Project")
            );

            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(testMember));
            when(memberProjectAssignmentRepository.findActiveProjectsForMember(any(), any()))
                .thenReturn(projectInfos);

            ResponseEntity<AssignedProjectsResponse> response = controller.getAssignedProjects(memberId);

            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().projects().size());
            
            var project = response.getBody().projects().get(0);
            assertEquals(projectId.toString(), project.id());
            assertEquals("ABC", project.code());
            assertEquals("Test Project", project.name());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/{id}")
    class GetMember {

        @Test
        @DisplayName("should return member when found")
        void shouldReturnMemberWhenFound() {
            UUID memberId = UUID.randomUUID();
            Member member = createTestMember(memberId, "user@example.com", "Test User");

            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.of(member));

            var response = controller.getMember(memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(memberId, response.getBody().id());
            assertEquals("user@example.com", response.getBody().email());
            assertEquals("Test User", response.getBody().displayName());
        }

        @Test
        @DisplayName("should throw exception when member not found")
        void shouldThrowExceptionWhenMemberNotFound() {
            UUID memberId = UUID.randomUUID();

            when(memberRepository.findById(MemberId.of(memberId)))
                .thenReturn(Optional.empty());

            DomainException exception = assertThrows(
                DomainException.class,
                () -> controller.getMember(memberId)
            );

            assertEquals("MEMBER_NOT_FOUND", exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/{managerId}/can-proxy/{memberId}")
    class CanProxy {

        @Test
        @DisplayName("should return true for self-entry")
        void shouldReturnTrueForSelfEntry() {
            UUID memberId = UUID.randomUUID();

            var response = controller.canProxy(memberId, memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().canProxy());
            assertTrue(response.getBody().reason().contains("Self-entry"));
        }

        @Test
        @DisplayName("should return true when member is subordinate")
        void shouldReturnTrueWhenMemberIsSubordinate() {
            UUID managerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            when(memberRepository.isSubordinateOf(MemberId.of(managerId), MemberId.of(memberId)))
                .thenReturn(true);

            var response = controller.canProxy(managerId, memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().canProxy());
        }

        @Test
        @DisplayName("should return false when member is not subordinate")
        void shouldReturnFalseWhenMemberIsNotSubordinate() {
            UUID managerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            when(memberRepository.isSubordinateOf(MemberId.of(managerId), MemberId.of(memberId)))
                .thenReturn(false);

            var response = controller.canProxy(managerId, memberId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().canProxy());
        }
    }

    /**
     * Helper method to create a test Member.
     * Uses the rehydration constructor from the domain.
     */
    private Member createTestMember(UUID id, String email, String displayName) {
        return new Member(
            MemberId.of(id),
            TenantId.of(UUID.randomUUID()),
            OrganizationId.of(UUID.randomUUID()),
            email,
            displayName,
            null,  // managerId
            true,  // isActive
            Instant.now(),
            Instant.now()
        );
    }
}
