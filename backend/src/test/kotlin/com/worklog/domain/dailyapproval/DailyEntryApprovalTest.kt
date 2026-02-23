package com.worklog.domain.dailyapproval

import com.worklog.domain.member.MemberId
import com.worklog.domain.shared.DomainException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DailyEntryApprovalTest {

    private val memberId = MemberId.of(UUID.randomUUID())
    private val supervisorId = MemberId.of(UUID.randomUUID())
    private val workLogEntryId = UUID.randomUUID()

    @Nested
    @DisplayName("DailyEntryApprovalId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            val id1 = DailyEntryApprovalId.generate()
            val id2 = DailyEntryApprovalId.generate()
            assertNotNull(id1.value())
            assertNotEquals(id1, id2)
        }

        @Test
        fun `of UUID should create id with same value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, DailyEntryApprovalId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, DailyEntryApprovalId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> {
                DailyEntryApprovalId(null)
            }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), DailyEntryApprovalId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create APPROVED entry without comment`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId,
                memberId,
                supervisorId,
                DailyApprovalStatus.APPROVED,
                null,
            )
            assertNotNull(approval.id)
            assertEquals(workLogEntryId, approval.workLogEntryId)
            assertEquals(memberId, approval.memberId)
            assertEquals(supervisorId, approval.supervisorId)
            assertEquals(DailyApprovalStatus.APPROVED, approval.status)
            assertNotNull(approval.createdAt)
            assertNotNull(approval.updatedAt)
        }

        @Test
        fun `should create REJECTED entry with comment`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId,
                memberId,
                supervisorId,
                DailyApprovalStatus.REJECTED,
                "Fix hours",
            )
            assertEquals(DailyApprovalStatus.REJECTED, approval.status)
            assertEquals("Fix hours", approval.comment)
        }

        @Test
        fun `should reject REJECTED without comment`() {
            assertFailsWith<DomainException> {
                DailyEntryApproval.create(
                    workLogEntryId,
                    memberId,
                    supervisorId,
                    DailyApprovalStatus.REJECTED,
                    null,
                )
            }
        }

        @Test
        fun `should reject REJECTED with blank comment`() {
            assertFailsWith<DomainException> {
                DailyEntryApproval.create(
                    workLogEntryId,
                    memberId,
                    supervisorId,
                    DailyApprovalStatus.REJECTED,
                    "  ",
                )
            }
        }
    }

    @Nested
    @DisplayName("reconstitute")
    inner class ReconstituteTests {
        @Test
        fun `should restore all fields`() {
            val id = DailyEntryApprovalId.generate()
            val now = Instant.now()
            val approval = DailyEntryApproval.reconstitute(
                id,
                workLogEntryId,
                memberId,
                supervisorId,
                DailyApprovalStatus.APPROVED,
                "note",
                now,
                now,
            )
            assertEquals(id, approval.id)
            assertEquals(DailyApprovalStatus.APPROVED, approval.status)
            assertEquals("note", approval.comment)
            assertEquals(now, approval.createdAt)
        }
    }

    @Nested
    @DisplayName("recall")
    inner class RecallTests {
        @Test
        fun `should transition APPROVED to RECALLED`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId,
                memberId,
                supervisorId,
                DailyApprovalStatus.APPROVED,
                null,
            )
            approval.recall()
            assertEquals(DailyApprovalStatus.RECALLED, approval.status)
        }

        @Test
        fun `should reject recall of REJECTED entry`() {
            val approval = DailyEntryApproval.create(
                workLogEntryId,
                memberId,
                supervisorId,
                DailyApprovalStatus.REJECTED,
                "reason",
            )
            assertFailsWith<DomainException> { approval.recall() }
        }
    }
}
