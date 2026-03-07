package com.worklog.api.dto

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MonthlyCalendarResponseTest {
    private val memberId = UUID.randomUUID()
    private val memberName = "Test User"
    private val periodStart = LocalDate.of(2023, 12, 21)
    private val periodEnd = LocalDate.of(2024, 1, 20)
    private val entries = emptyList<DailyCalendarEntry>()

    @Test
    fun `canonical constructor should set all fields including monthlyApproval`() {
        val approvalId = UUID.randomUUID()
        val reviewedBy = UUID.randomUUID()
        val reviewedAt = Instant.now()
        val approval = MonthlyCalendarResponse.MonthlyApprovalSummary(
            approvalId,
            "REJECTED",
            "Incorrect hours",
            reviewedBy,
            "Manager",
            reviewedAt,
        )

        val response = MonthlyCalendarResponse(
            memberId,
            memberName,
            periodStart,
            periodEnd,
            entries,
            approval,
        )

        assertEquals(memberId, response.memberId())
        assertEquals(memberName, response.memberName())
        assertEquals(periodStart, response.periodStart())
        assertEquals(periodEnd, response.periodEnd())
        assertEquals(entries, response.entries())
        assertEquals(approval, response.monthlyApproval())
    }

    @Test
    fun `backward-compatible constructor should default monthlyApproval to null`() {
        val response = MonthlyCalendarResponse(
            memberId,
            memberName,
            periodStart,
            periodEnd,
            entries,
        )

        assertEquals(memberId, response.memberId())
        assertEquals(memberName, response.memberName())
        assertEquals(periodStart, response.periodStart())
        assertEquals(periodEnd, response.periodEnd())
        assertEquals(entries, response.entries())
        assertNull(response.monthlyApproval())
    }

    @Test
    fun `MonthlyApprovalSummary should expose all fields`() {
        val approvalId = UUID.randomUUID()
        val reviewedBy = UUID.randomUUID()
        val reviewedAt = Instant.now()

        val summary = MonthlyCalendarResponse.MonthlyApprovalSummary(
            approvalId,
            "APPROVED",
            null,
            reviewedBy,
            "Reviewer",
            reviewedAt,
        )

        assertEquals(approvalId, summary.approvalId())
        assertEquals("APPROVED", summary.status())
        assertNull(summary.rejectionReason())
        assertEquals(reviewedBy, summary.reviewedBy())
        assertEquals("Reviewer", summary.reviewerName())
        assertEquals(reviewedAt, summary.reviewedAt())
    }

    @Test
    fun `records with same values should be equal`() {
        val r1 = MonthlyCalendarResponse(memberId, memberName, periodStart, periodEnd, entries)
        val r2 = MonthlyCalendarResponse(memberId, memberName, periodStart, periodEnd, entries)

        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `records with different values should not be equal`() {
        val r1 = MonthlyCalendarResponse(memberId, memberName, periodStart, periodEnd, entries)
        val r2 = MonthlyCalendarResponse(UUID.randomUUID(), memberName, periodStart, periodEnd, entries)

        assertNotEquals(r1, r2)
    }

    @Test
    fun `toString should contain field values`() {
        val response = MonthlyCalendarResponse(memberId, memberName, periodStart, periodEnd, entries)
        val str = response.toString()

        assert(str.contains(memberId.toString()))
        assert(str.contains(memberName))
    }
}
