package com.worklog.application.command

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
        cmd1.toString()
    }

    @Test
    fun `RejectDailyEntriesCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val date = LocalDate.of(2026, 2, 1)
        val rejectedBy = UUID.randomUUID()

        val cmd = RejectDailyEntriesCommand(memberId, date, rejectedBy, "reason")

        assertEquals(cmd, RejectDailyEntriesCommand(memberId, date, rejectedBy, "reason"))
        cmd.toString()
    }

    @Test
    fun `RecallDailyEntriesCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val date = LocalDate.of(2026, 2, 1)
        val recalledBy = UUID.randomUUID()

        val cmd = RecallDailyEntriesCommand(memberId, date, recalledBy)

        assertEquals(cmd, RecallDailyEntriesCommand(memberId, date, recalledBy))
        cmd.toString()
    }

    @Test
    fun `CopyFromPreviousMonthCommand equality contract`() {
        val memberId = UUID.randomUUID()
        val cmd = CopyFromPreviousMonthCommand(memberId, 2026, 2)

        assertEquals(cmd, CopyFromPreviousMonthCommand(memberId, 2026, 2))
        cmd.toString()
    }
}
