package com.worklog.infrastructure.projection

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MonthlyCalendarProjectionTest {
    @Mock
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var projection: MonthlyCalendarProjection

    private val memberId = UUID.randomUUID()
    private val startDate = LocalDate.of(2025, 1, 21)
    private val endDate = LocalDate.of(2025, 2, 20)

    @BeforeEach
    fun setUp() {
        projection = MonthlyCalendarProjection(jdbcTemplate)
    }

    @Test
    fun `getDailyTotals should return empty map when no entries exist`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getDailyTotals(memberId, startDate, endDate)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDailyTotals should aggregate hours by date`() {
        val mockResults =
            listOf(
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "total_hours" to BigDecimal("8.00"),
                ),
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 26)),
                    "total_hours" to BigDecimal("4.50"),
                ),
            )

        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(mockResults)

        val result = projection.getDailyTotals(memberId, startDate, endDate)

        assertEquals(2, result.size)
        assertEquals(BigDecimal("8.00"), result[LocalDate.of(2025, 1, 25)])
        assertEquals(BigDecimal("4.50"), result[LocalDate.of(2025, 1, 26)])
    }

    @Test
    fun `getAbsenceTotals should return empty map when no absences exist`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getAbsenceTotals(memberId, startDate, endDate)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAbsenceTotals should expand absence date ranges`() {
        val mockResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "end_date" to Date.valueOf(LocalDate.of(2025, 1, 27)),
                    "hours_per_day" to BigDecimal("8.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(mockResults)

        val result = projection.getAbsenceTotals(memberId, startDate, endDate)

        assertEquals(3, result.size)
        assertEquals(BigDecimal("8.00"), result[LocalDate.of(2025, 1, 25)])
        assertEquals(BigDecimal("8.00"), result[LocalDate.of(2025, 1, 26)])
        assertEquals(BigDecimal("8.00"), result[LocalDate.of(2025, 1, 27)])
    }

    @Test
    fun `getAbsenceTotals should clip absence to requested date range`() {
        // Absence spans before and after the requested range
        val mockResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 19)), // before startDate
                    "end_date" to Date.valueOf(LocalDate.of(2025, 2, 25)), // after endDate
                    "hours_per_day" to BigDecimal("4.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(mockResults)

        val result = projection.getAbsenceTotals(memberId, startDate, endDate)

        // Should only include dates within the requested range (21 Jan - 20 Feb)
        assertTrue(result.containsKey(startDate))
        assertTrue(result.containsKey(endDate))
        assertFalse(result.containsKey(LocalDate.of(2025, 1, 19)))
        assertFalse(result.containsKey(LocalDate.of(2025, 2, 21)))
    }

    @Test
    fun `getAbsenceTotals should merge hours for overlapping absences`() {
        val mockResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "end_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "hours_per_day" to BigDecimal("4.00"),
                ),
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "end_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "hours_per_day" to BigDecimal("2.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(mockResults)

        val result = projection.getAbsenceTotals(memberId, startDate, endDate)

        // Should merge hours for the same date
        assertEquals(BigDecimal("6.00"), result[LocalDate.of(2025, 1, 25)])
    }

    @Test
    fun `getProxyEntryDates should return empty set when no proxy entries exist`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getProxyEntryDates(memberId, startDate, endDate)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProxyEntryDates should return set of dates with proxy entries`() {
        val mockResults =
            listOf(
                mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 25))),
                mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 28))),
            )

        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(mockResults)

        val result = projection.getProxyEntryDates(memberId, startDate, endDate)

        assertEquals(2, result.size)
        assertTrue(result.contains(LocalDate.of(2025, 1, 25)))
        assertTrue(result.contains(LocalDate.of(2025, 1, 28)))
    }

    @Test
    fun `getDailyEntries should return entries for all days in range`() {
        // Mock all the underlying calls
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getDailyEntries(memberId, startDate, endDate)

        // Should return an entry for each day from Jan 21 to Feb 20 (31 days)
        assertEquals(31, result.size)
        assertEquals(startDate, result.first().date())
        assertEquals(endDate, result.last().date())
    }

    @Test
    fun `getDailyEntries should mark weekends correctly`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getDailyEntries(memberId, startDate, endDate)

        // Jan 25, 2025 is a Saturday
        val saturday = result.find { it.date() == LocalDate.of(2025, 1, 25) }
        assertTrue(saturday?.isWeekend ?: false)

        // Jan 26, 2025 is a Sunday
        val sunday = result.find { it.date() == LocalDate.of(2025, 1, 26) }
        assertTrue(sunday?.isWeekend ?: false)

        // Jan 27, 2025 is a Monday
        val monday = result.find { it.date() == LocalDate.of(2025, 1, 27) }
        assertFalse(monday?.isWeekend ?: true)
    }

    @Test
    fun `getDailyEntries should populate hours from totals`() {
        // Mock getDailyTotals
        val workResults =
            listOf(
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "total_hours" to BigDecimal("8.00"),
                ),
            )

        // Mock getAbsenceTotals - different query
        val absenceResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 26)),
                    "end_date" to Date.valueOf(LocalDate.of(2025, 1, 26)),
                    "hours_per_day" to BigDecimal("4.00"),
                ),
            )

        // Mock proxy dates
        val proxyResults =
            listOf(
                mapOf("work_date" to Date.valueOf(LocalDate.of(2025, 1, 25))),
            )

        // Mock status
        val statusResults =
            listOf(
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 25)),
                    "status" to "SUBMITTED",
                ),
            )

        // Configure mock to return different results for different SQL patterns
        `when`(jdbcTemplate.queryForList(contains("SUM(hours)"), any(), any(), any()))
            .thenReturn(workResults)
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(absenceResults)
        `when`(jdbcTemplate.queryForList(contains("entered_by"), any(), any(), any()))
            .thenReturn(proxyResults)
        `when`(jdbcTemplate.queryForList(contains("DISTINCT status"), any(), any(), any()))
            .thenReturn(statusResults)

        val result = projection.getDailyEntries(memberId, startDate, endDate)

        val jan25 = result.find { it.date() == LocalDate.of(2025, 1, 25) }
        assertEquals(BigDecimal("8.00"), jan25?.totalWorkHours())
        assertEquals("SUBMITTED", jan25?.status())
        assertTrue(jan25?.hasProxyEntries() ?: false)

        val jan26 = result.find { it.date() == LocalDate.of(2025, 1, 26) }
        assertEquals(BigDecimal("4.00"), jan26?.totalAbsenceHours())
    }

    @Test
    fun `evictMemberCache should not throw exception`() {
        // This method is intentionally empty (cache eviction via annotations)
        assertDoesNotThrow {
            projection.evictMemberCache(memberId)
        }
    }

    @Test
    fun `evictAllCaches should not throw exception`() {
        // This method is intentionally empty (cache eviction via annotations)
        assertDoesNotThrow {
            projection.evictAllCaches()
        }
    }
}
