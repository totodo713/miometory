package com.worklog.infrastructure.projection

import com.worklog.application.service.StandardHoursResolution
import com.worklog.application.service.StandardWorkingHoursService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.contains
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MonthlySummaryProjectionTest {
    @Mock
    private lateinit var jdbcTemplate: JdbcTemplate

    @Mock
    private lateinit var standardWorkingHoursService: StandardWorkingHoursService

    private lateinit var projection: MonthlySummaryProjection

    private val memberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        `when`(standardWorkingHoursService.resolveStandardDailyHours(any()))
            .thenReturn(StandardHoursResolution(BigDecimal("8.0"), "system"))
        projection = MonthlySummaryProjection(jdbcTemplate, standardWorkingHoursService)
    }

    @Test
    fun `getMonthlySummary should return empty summary when no data exists`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals(2025, result.year())
        assertEquals(1, result.month())
        assertEquals(BigDecimal.ZERO, result.totalWorkHours())
        assertEquals(BigDecimal.ZERO, result.totalAbsenceHours())
        assertTrue(result.projects().isEmpty())
        assertNull(result.approvalStatus())
    }

    @Test
    fun `getMonthlySummary should calculate business days correctly`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        // January 2025 has 23 business days (excludes weekends)
        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals(23, result.totalBusinessDays())
    }

    @Test
    fun `getMonthlySummary should return project summaries with hours and percentages`() {
        val projectId = UUID.randomUUID()
        val projectResults =
            listOf(
                mapOf(
                    "project_id" to projectId,
                    "project_name" to "Test Project",
                    "total_hours" to BigDecimal("40.00"),
                    "percentage" to BigDecimal("100.00"),
                ),
            )

        // First call returns project summaries
        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(projectResults)
        // Daily work totals for overtime calculation
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        // Second call returns absence hours
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        // Third call returns approval status
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals(1, result.projects().size)
        val project = result.projects()[0]
        assertEquals(projectId.toString(), project.projectId())
        assertEquals("Test Project", project.projectName())
        assertEquals(BigDecimal("40.00"), project.totalHours())
        assertEquals(BigDecimal("100.00"), project.percentage())
    }

    @Test
    fun `getMonthlySummary should handle null project name as Unknown Project`() {
        val projectId = UUID.randomUUID()
        val projectResults =
            listOf(
                mapOf(
                    "project_id" to projectId,
                    "project_name" to null,
                    "total_hours" to BigDecimal("8.00"),
                    "percentage" to BigDecimal("100.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(projectResults)
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals("Unknown Project", result.projects()[0].projectName())
    }

    @Test
    fun `getMonthlySummary should calculate total work hours from projects`() {
        val projectResults =
            listOf(
                mapOf(
                    "project_id" to UUID.randomUUID(),
                    "project_name" to "Project A",
                    "total_hours" to BigDecimal("20.00"),
                    "percentage" to BigDecimal("50.00"),
                ),
                mapOf(
                    "project_id" to UUID.randomUUID(),
                    "project_name" to "Project B",
                    "total_hours" to BigDecimal("20.00"),
                    "percentage" to BigDecimal("50.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(projectResults)
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals(BigDecimal("40.00"), result.totalWorkHours())
        assertEquals(2, result.projects().size)
    }

    @Test
    fun `getMonthlySummary should calculate absence hours correctly`() {
        val absenceResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2025, 1, 10)),
                    "end_date" to Date.valueOf(LocalDate.of(2025, 1, 12)),
                    "hours_per_day" to BigDecimal("8.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(absenceResults)
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        // 3 days * 8 hours = 24 hours
        assertEquals(0, BigDecimal("24").compareTo(result.totalAbsenceHours()))
    }

    @Test
    fun `getMonthlySummary should clip absence to month boundaries`() {
        val absenceResults =
            listOf(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "start_date" to Date.valueOf(LocalDate.of(2024, 12, 25)), // before January
                    "end_date" to Date.valueOf(LocalDate.of(2025, 2, 5)), // after January
                    "hours_per_day" to BigDecimal("8.00"),
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(absenceResults)
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        // Only January 1-31 should count = 31 days * 8 hours = 248 hours
        assertEquals(0, BigDecimal("248").compareTo(result.totalAbsenceHours()))
    }

    @Test
    fun `getMonthlySummary should return approval status SUBMITTED`() {
        val approvalResults =
            listOf(
                mapOf(
                    "event_type" to "MonthSubmittedForApproval",
                    "rejection_reason" to null,
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(approvalResults)

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals("SUBMITTED", result.approvalStatus())
        assertNull(result.rejectionReason())
    }

    @Test
    fun `getMonthlySummary should return approval status APPROVED`() {
        val approvalResults =
            listOf(
                mapOf(
                    "event_type" to "MonthApproved",
                    "rejection_reason" to null,
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(approvalResults)

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals("APPROVED", result.approvalStatus())
    }

    @Test
    fun `getMonthlySummary should return approval status REJECTED with reason`() {
        val approvalResults =
            listOf(
                mapOf(
                    "event_type" to "MonthRejected",
                    "rejection_reason" to "Missing project allocation",
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(approvalResults)

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals("REJECTED", result.approvalStatus())
        assertEquals("Missing project allocation", result.rejectionReason())
    }

    @Test
    fun `getMonthlySummary should return approval status PENDING`() {
        val approvalResults =
            listOf(
                mapOf(
                    "event_type" to "MonthlyApprovalCreated",
                    "rejection_reason" to null,
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(approvalResults)

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals("PENDING", result.approvalStatus())
    }

    @Test
    fun `getMonthlySummary should handle February in leap year`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        // 2024 is a leap year - February has 29 days
        val result = projection.getMonthlySummary(memberId, 2024, 2)

        // February 2024 has 21 business days (29 days - 8 weekend days)
        assertEquals(21, result.totalBusinessDays())
    }

    @Test
    fun `getMonthlySummary should handle February in non-leap year`() {
        `when`(jdbcTemplate.queryForList(anyString(), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        // 2025 is not a leap year - February has 28 days
        val result = projection.getMonthlySummary(memberId, 2025, 2)

        // February 2025 has 20 business days (28 days - 8 weekend days)
        assertEquals(20, result.totalBusinessDays())
    }

    @Test
    fun `getMonthlySummary should calculate overtime hours correctly`() {
        // Configure daily work totals: 2 days with overtime
        val dailyWorkTotals =
            listOf(
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 6)),
                    "total_hours" to BigDecimal("10.00"), // 2h overtime
                ),
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 7)),
                    "total_hours" to BigDecimal("9.50"), // 1.5h overtime
                ),
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 8)),
                    "total_hours" to BigDecimal("7.00"), // no overtime (under 8h)
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(dailyWorkTotals)
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        // Overtime: (10 - 8) + (9.5 - 8) + max(0, 7 - 8) = 2.0 + 1.5 + 0 = 3.5
        assertEquals(0, BigDecimal("3.50").compareTo(result.overtimeHours()))
        assertEquals(BigDecimal("8.0"), result.standardDailyHours())
        // 23 business days * 8h = 184h
        assertEquals(0, BigDecimal("184.0").compareTo(result.standardMonthlyHours()))
        assertEquals("system", result.standardHoursSource())
    }

    @Test
    fun `getMonthlySummary should return zero overtime when no work exceeds standard hours`() {
        val dailyWorkTotals =
            listOf(
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 6)),
                    "total_hours" to BigDecimal("7.50"),
                ),
                mapOf(
                    "work_date" to Date.valueOf(LocalDate.of(2025, 1, 7)),
                    "total_hours" to BigDecimal("8.00"), // exactly standard, no overtime
                ),
            )

        `when`(jdbcTemplate.queryForList(contains("WITH project_hours"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("GROUP BY work_date"), any(), any(), any()))
            .thenReturn(dailyWorkTotals)
        `when`(jdbcTemplate.queryForList(contains("hours_per_day"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())
        `when`(jdbcTemplate.queryForList(contains("MonthlyApproval"), any(), any(), any()))
            .thenReturn(emptyList<Map<String, Any>>())

        val result = projection.getMonthlySummary(memberId, 2025, 1)

        assertEquals(0, BigDecimal.ZERO.compareTo(result.overtimeHours()))
    }
}
