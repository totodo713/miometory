package com.worklog.application.command

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class CommandContractTest {

    @Test
    fun `SubmitDailyEntriesCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val date = LocalDate.of(2026, 2, 1)
        val submittedBy = UUID.randomUUID()

        val cmd1 = SubmitDailyEntriesCommand(memberId, date, submittedBy)
        val cmd2 = SubmitDailyEntriesCommand(memberId, date, submittedBy)
        val cmd3 = SubmitDailyEntriesCommand(UUID.randomUUID(), date, submittedBy)

        assertEquals(cmd1, cmd2)
        assertEquals(cmd1.hashCode(), cmd2.hashCode())
        assertNotEquals(cmd1, cmd3)
        assertNotNull(cmd1.toString())
    }

    @Test
    fun `RejectDailyEntriesCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val date = LocalDate.of(2026, 2, 1)
        val rejectedBy = UUID.randomUUID()

        val cmd = RejectDailyEntriesCommand(memberId, date, rejectedBy, "reason")

        assertEquals(cmd, RejectDailyEntriesCommand(memberId, date, rejectedBy, "reason"))
        assertNotNull(cmd.toString())
    }

    @Test
    fun `RecallDailyEntriesCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val date = LocalDate.of(2026, 2, 1)
        val recalledBy = UUID.randomUUID()

        val cmd = RecallDailyEntriesCommand(memberId, date, recalledBy)

        assertEquals(cmd, RecallDailyEntriesCommand(memberId, date, recalledBy))
        assertNotNull(cmd.toString())
    }

    @Test
    fun `CopyFromPreviousMonthCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val cmd = CopyFromPreviousMonthCommand(memberId, 2026, 2)

        assertEquals(cmd, CopyFromPreviousMonthCommand(memberId, 2026, 2))
        assertNotNull(cmd.toString())
    }

    // Validation tests for uncovered branches

    @Test
    fun `SubmitDailyEntriesCommand should reject null memberId`() {
        assertFailsWith<IllegalArgumentException> {
            SubmitDailyEntriesCommand(null, LocalDate.now(), UUID.randomUUID())
        }
    }

    @Test
    fun `SubmitDailyEntriesCommand should reject null date`() {
        assertFailsWith<IllegalArgumentException> {
            SubmitDailyEntriesCommand(UUID.randomUUID(), null, UUID.randomUUID())
        }
    }

    @Test
    fun `SubmitDailyEntriesCommand should reject null submittedBy`() {
        assertFailsWith<IllegalArgumentException> {
            SubmitDailyEntriesCommand(UUID.randomUUID(), LocalDate.now(), null)
        }
    }

    @Test
    fun `RejectDailyEntriesCommand should reject null memberId`() {
        assertFailsWith<IllegalArgumentException> {
            RejectDailyEntriesCommand(null, LocalDate.now(), UUID.randomUUID(), "reason")
        }
    }

    @Test
    fun `RejectDailyEntriesCommand should reject null date`() {
        assertFailsWith<IllegalArgumentException> {
            RejectDailyEntriesCommand(UUID.randomUUID(), null, UUID.randomUUID(), "reason")
        }
    }

    @Test
    fun `RejectDailyEntriesCommand should reject null rejectedBy`() {
        assertFailsWith<IllegalArgumentException> {
            RejectDailyEntriesCommand(UUID.randomUUID(), LocalDate.now(), null, "reason")
        }
    }

    @Test
    fun `RejectDailyEntriesCommand should reject null reason`() {
        assertFailsWith<IllegalArgumentException> {
            RejectDailyEntriesCommand(UUID.randomUUID(), LocalDate.now(), UUID.randomUUID(), null)
        }
    }

    @Test
    fun `RejectDailyEntriesCommand should reject blank reason`() {
        assertFailsWith<IllegalArgumentException> {
            RejectDailyEntriesCommand(UUID.randomUUID(), LocalDate.now(), UUID.randomUUID(), "  ")
        }
    }

    @Test
    fun `RecallDailyEntriesCommand should reject null memberId`() {
        assertFailsWith<IllegalArgumentException> {
            RecallDailyEntriesCommand(null, LocalDate.now(), UUID.randomUUID())
        }
    }

    @Test
    fun `RecallDailyEntriesCommand should reject null date`() {
        assertFailsWith<IllegalArgumentException> {
            RecallDailyEntriesCommand(UUID.randomUUID(), null, UUID.randomUUID())
        }
    }

    @Test
    fun `RecallDailyEntriesCommand should reject null recalledBy`() {
        assertFailsWith<IllegalArgumentException> {
            RecallDailyEntriesCommand(UUID.randomUUID(), LocalDate.now(), null)
        }
    }

    @Test
    fun `CopyFromPreviousMonthCommand should reject null memberId`() {
        assertFailsWith<IllegalArgumentException> {
            CopyFromPreviousMonthCommand(null, 2026, 2)
        }
    }

    @Test
    fun `CopyFromPreviousMonthCommand should reject invalid month`() {
        assertFailsWith<IllegalArgumentException> {
            CopyFromPreviousMonthCommand(UUID.randomUUID(), 2026, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CopyFromPreviousMonthCommand(UUID.randomUUID(), 2026, 13)
        }
    }

    @Test
    fun `CopyFromPreviousMonthCommand should reject invalid year`() {
        assertFailsWith<IllegalArgumentException> {
            CopyFromPreviousMonthCommand(UUID.randomUUID(), 1999, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            CopyFromPreviousMonthCommand(UUID.randomUUID(), 2101, 1)
        }
    }
}
