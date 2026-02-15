package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.MemberProjectAssignment;
import com.worklog.domain.project.MemberProjectAssignmentId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.tenant.TenantId;
import com.worklog.infrastructure.projection.AssignedProjectInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for JdbcMemberProjectAssignmentRepository.
 *
 * Tests repository methods with mocked JdbcTemplate.
 * These are pure unit tests with no database dependency.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcMemberProjectAssignmentRepository")
class JdbcMemberProjectAssignmentRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<Object[]> argsCaptor;

    private JdbcMemberProjectAssignmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcMemberProjectAssignmentRepository(jdbcTemplate);
    }

    @Nested
    @DisplayName("findActiveProjectsForMember")
    class FindActiveProjectsForMember {

        @Test
        @DisplayName("should return assigned projects for member")
        void shouldReturnAssignedProjects() {
            MemberId memberId = MemberId.of(UUID.randomUUID());
            LocalDate validOn = LocalDate.now();

            List<AssignedProjectInfo> expectedProjects = List.of(
                    new AssignedProjectInfo(UUID.randomUUID(), "PROJ1", "Project One"),
                    new AssignedProjectInfo(UUID.randomUUID(), "PROJ2", "Project Two"));

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(expectedProjects);

            List<AssignedProjectInfo> result = repository.findActiveProjectsForMember(memberId, validOn);

            assertEquals(2, result.size());
            assertEquals("PROJ1", result.get(0).code());
            assertEquals("Project One", result.get(0).name());
            assertEquals("PROJ2", result.get(1).code());
            assertEquals("Project Two", result.get(1).name());
        }

        @Test
        @DisplayName("should return empty list when no projects assigned")
        void shouldReturnEmptyListWhenNoProjects() {
            MemberId memberId = MemberId.of(UUID.randomUUID());
            LocalDate validOn = LocalDate.now();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<AssignedProjectInfo> result = repository.findActiveProjectsForMember(memberId, validOn);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should pass correct parameters to query")
        void shouldPassCorrectParametersToQuery() {
            MemberId memberId = MemberId.of(UUID.randomUUID());
            LocalDate validOn = LocalDate.of(2024, 6, 15);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            repository.findActiveProjectsForMember(memberId, validOn);

            verify(jdbcTemplate)
                    .query(
                            contains("member_project_assignments"),
                            any(RowMapper.class),
                            eq(memberId.value()),
                            eq(validOn),
                            eq(validOn));
        }

        @Test
        @DisplayName("should use SQL that joins with projects table")
        void shouldUseJoinWithProjectsTable() {
            MemberId memberId = MemberId.of(UUID.randomUUID());
            LocalDate validOn = LocalDate.now();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            repository.findActiveProjectsForMember(memberId, validOn);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), any());

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("INNER JOIN projects"));
            assertTrue(sql.contains("is_active = true"));
            assertTrue(sql.contains("valid_from"));
            assertTrue(sql.contains("valid_until"));
            assertTrue(sql.contains("ORDER BY p.code"));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return assignment when found")
        void shouldReturnAssignmentWhenFound() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();
            MemberProjectAssignment expectedAssignment = new MemberProjectAssignment(
                    id,
                    TenantId.of(UUID.randomUUID()),
                    MemberId.of(UUID.randomUUID()),
                    ProjectId.of(UUID.randomUUID()),
                    Instant.now(),
                    null,
                    true);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of(expectedAssignment));

            Optional<MemberProjectAssignment> result = repository.findById(id);

            assertTrue(result.isPresent());
            assertEquals(id, result.get().getId());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any())).thenReturn(Collections.emptyList());

            Optional<MemberProjectAssignment> result = repository.findById(id);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should pass assignment ID to query")
        void shouldPassIdToQuery() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any())).thenReturn(Collections.emptyList());

            repository.findById(id);

            verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(id.value()));
        }
    }

    @Nested
    @DisplayName("findByMemberAndProject")
    class FindByMemberAndProject {

        @Test
        @DisplayName("should return assignment when found")
        void shouldReturnAssignmentWhenFound() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            MemberId memberId = MemberId.of(UUID.randomUUID());
            ProjectId projectId = ProjectId.of(UUID.randomUUID());

            MemberProjectAssignment expectedAssignment = new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(), tenantId, memberId, projectId, Instant.now(), null, true);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of(expectedAssignment));

            Optional<MemberProjectAssignment> result = repository.findByMemberAndProject(tenantId, memberId, projectId);

            assertTrue(result.isPresent());
            assertEquals(memberId, result.get().getMemberId());
            assertEquals(projectId, result.get().getProjectId());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            MemberId memberId = MemberId.of(UUID.randomUUID());
            ProjectId projectId = ProjectId.of(UUID.randomUUID());

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            Optional<MemberProjectAssignment> result = repository.findByMemberAndProject(tenantId, memberId, projectId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should pass all IDs to query")
        void shouldPassAllIdsToQuery() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            MemberId memberId = MemberId.of(UUID.randomUUID());
            ProjectId projectId = ProjectId.of(UUID.randomUUID());

            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            repository.findByMemberAndProject(tenantId, memberId, projectId);

            verify(jdbcTemplate)
                    .query(
                            anyString(),
                            any(RowMapper.class),
                            eq(tenantId.value()),
                            eq(memberId.value()),
                            eq(projectId.value()));
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should save assignment with all fields")
        void shouldSaveAssignmentWithAllFields() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            MemberId memberId = MemberId.of(UUID.randomUUID());
            ProjectId projectId = ProjectId.of(UUID.randomUUID());
            MemberId assignedBy = MemberId.of(UUID.randomUUID());
            Instant assignedAt = Instant.now();

            MemberProjectAssignment assignment =
                    new MemberProjectAssignment(id, tenantId, memberId, projectId, assignedAt, assignedBy, true);

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            repository.save(assignment);

            verify(jdbcTemplate)
                    .update(
                            contains("INSERT INTO member_project_assignments"),
                            eq(id.value()),
                            eq(tenantId.value()),
                            eq(memberId.value()),
                            eq(projectId.value()),
                            any(), // Timestamp
                            eq(assignedBy.value()),
                            eq(true));
        }

        @Test
        @DisplayName("should save assignment with null assignedBy")
        void shouldSaveAssignmentWithNullAssignedBy() {
            MemberProjectAssignment assignment = new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(),
                    TenantId.of(UUID.randomUUID()),
                    MemberId.of(UUID.randomUUID()),
                    ProjectId.of(UUID.randomUUID()),
                    Instant.now(),
                    null, // No assignedBy
                    true);

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            repository.save(assignment);

            verify(jdbcTemplate)
                    .update(
                            anyString(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            isNull(), // assignedBy should be null
                            any());
        }

        @Test
        @DisplayName("should use upsert SQL")
        void shouldUseUpsertSql() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                    TenantId.of(UUID.randomUUID()),
                    MemberId.of(UUID.randomUUID()),
                    ProjectId.of(UUID.randomUUID()),
                    null);

            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

            repository.save(assignment);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("ON CONFLICT"));
            assertTrue(sql.contains("DO UPDATE SET"));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete assignment by ID")
        void shouldDeleteAssignmentById() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            when(jdbcTemplate.update(anyString(), any(UUID.class))).thenReturn(1);

            repository.delete(id);

            verify(jdbcTemplate).update(contains("DELETE FROM member_project_assignments"), (Object) eq(id.value()));
        }

        @Test
        @DisplayName("should not throw when assignment does not exist")
        void shouldNotThrowWhenNotExists() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            when(jdbcTemplate.update(anyString(), any(UUID.class))).thenReturn(0);

            assertDoesNotThrow(() -> repository.delete(id));
        }
    }
}
