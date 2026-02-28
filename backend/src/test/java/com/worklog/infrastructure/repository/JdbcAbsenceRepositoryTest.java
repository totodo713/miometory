package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.absence.Absence;
import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.AbsenceStatus;
import com.worklog.domain.absence.AbsenceType;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.TimeAmount;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JdbcAbsenceRepository")
class JdbcAbsenceRepositoryTest extends IntegrationTestBase {

    @Autowired
    private JdbcAbsenceRepository absenceRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 15);

    @Test
    @DisplayName("save and findById should round-trip an absence")
    void save_andFindById_roundTrips() {
        UUID memberId = UUID.randomUUID();
        createTestMember(memberId, "absence-rt-" + memberId + "@example.com");

        Absence absence = Absence.record(
                MemberId.of(memberId),
                TEST_DATE,
                TimeAmount.of(4.0),
                AbsenceType.PAID_LEAVE,
                "Round-trip test",
                MemberId.of(memberId));

        absenceRepository.save(absence);

        var found = absenceRepository.findById(absence.getId());
        assertTrue(found.isPresent());
        assertEquals(AbsenceType.PAID_LEAVE, found.get().getAbsenceType());
        assertEquals(AbsenceStatus.DRAFT, found.get().getStatus());
    }

    @Test
    @DisplayName("save with no uncommitted events should be a no-op")
    void save_noEvents_noop() {
        UUID memberId = UUID.randomUUID();
        createTestMember(memberId, "absence-noop-" + memberId + "@example.com");

        Absence absence = Absence.record(
                MemberId.of(memberId),
                TEST_DATE,
                TimeAmount.of(2.0),
                AbsenceType.SICK_LEAVE,
                "No-op test",
                MemberId.of(memberId));

        absenceRepository.save(absence);

        // Events cleared after first save, second save should return early
        absenceRepository.save(absence);

        var found = absenceRepository.findById(absence.getId());
        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("save should update projection for status change")
    void save_statusChange_updatesProjection() {
        UUID memberId = UUID.randomUUID();
        createTestMember(memberId, "absence-sc-" + memberId + "@example.com");

        Absence absence = Absence.record(
                MemberId.of(memberId),
                TEST_DATE,
                TimeAmount.of(4.0),
                AbsenceType.PAID_LEAVE,
                "Status change test",
                MemberId.of(memberId));

        absenceRepository.save(absence);

        // Reload from event store to get correct version
        var reloaded = absenceRepository.findById(absence.getId()).orElseThrow();

        // Change status: DRAFT → SUBMITTED
        reloaded.changeStatus(AbsenceStatus.SUBMITTED, MemberId.of(memberId));
        absenceRepository.save(reloaded);

        // Verify aggregate state via event replay
        var found = absenceRepository.findById(absence.getId());
        assertTrue(found.isPresent());
        assertEquals(AbsenceStatus.SUBMITTED, found.get().getStatus());

        // Verify projection was updated
        String status = baseJdbcTemplate.queryForObject(
                "SELECT status FROM absences_projection WHERE id = ?",
                String.class,
                absence.getId().value());
        assertEquals("SUBMITTED", status);
    }

    @Test
    @DisplayName("existsById should return true for existing absence")
    void existsById_existing_returnsTrue() {
        UUID memberId = UUID.randomUUID();
        createTestMember(memberId, "absence-ex-" + memberId + "@example.com");

        Absence absence = Absence.record(
                MemberId.of(memberId),
                TEST_DATE,
                TimeAmount.of(4.0),
                AbsenceType.OTHER,
                "Exists test",
                MemberId.of(memberId));

        absenceRepository.save(absence);

        assertTrue(absenceRepository.existsById(absence.getId()));
    }

    @Test
    @DisplayName("existsById should return false for non-existent absence")
    void existsById_nonExistent_returnsFalse() {
        assertFalse(absenceRepository.existsById(AbsenceId.of(UUID.randomUUID())));
    }

    @Test
    @DisplayName("save should throw when member has no organization assigned")
    void save_memberWithNoOrg_throwsIllegalState() {
        UUID memberId = UUID.randomUUID();
        baseJdbcTemplate.update("""
                INSERT INTO members (id, tenant_id, organization_id, email, display_name,
                                     manager_id, is_active, version, created_at, updated_at)
                VALUES (?, '550e8400-e29b-41d4-a716-446655440001'::UUID, NULL, ?, ?,
                        NULL, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""", memberId, "no-org-abs-" + memberId + "@example.com", "No Org User");

        Absence absence = Absence.record(
                MemberId.of(memberId),
                TEST_DATE,
                TimeAmount.of(4.0),
                AbsenceType.PAID_LEAVE,
                "No org test",
                MemberId.of(memberId));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> absenceRepository.save(absence));
        assertTrue(ex.getMessage().contains("has no organization assigned"));
    }

    @Test
    @DisplayName("save should throw when member not found")
    void save_memberNotFound_throwsIllegalState() {
        UUID nonExistentId = UUID.randomUUID();

        Absence absence = Absence.record(
                MemberId.of(nonExistentId),
                TEST_DATE,
                TimeAmount.of(4.0),
                AbsenceType.PAID_LEAVE,
                "Not found test",
                MemberId.of(nonExistentId));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> absenceRepository.save(absence));
        assertTrue(ex.getMessage().contains("not found in members table"));
    }
}
