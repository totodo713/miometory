package com.worklog.fixtures

import com.worklog.domain.fiscalyear.FiscalYearPattern
import com.worklog.domain.tenant.TenantId
import java.util.UUID

/**
 * Test fixtures for FiscalYearPattern-related entities.
 * Uses Instancio for generating test data where applicable.
 */
object FiscalYearPatternFixtures {
    /**
     * Kotlin-friendly wrapper for creating FiscalYearPattern with named arguments.
     */
    fun createPattern(tenantId: TenantId, name: String, startMonth: Int, startDay: Int): FiscalYearPattern =
        FiscalYearPattern.create(tenantId, name, startMonth, startDay)

    /**
     * Creates a valid fiscal year pattern name.
     */
    fun validName(suffix: String = ""): String = "Fiscal Year Pattern${if (suffix.isNotEmpty()) " $suffix" else ""}"

    /**
     * Creates a new random pattern ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Creates a fiscal year pattern creation request with April 1st start (Japanese standard).
     */
    fun createAprilStartRequest(name: String = "4月開始"): Map<String, Any> = mapOf(
        "name" to name,
        "startMonth" to 4,
        "startDay" to 1,
    )

    /**
     * Creates a fiscal year pattern creation request with November 1st start (year boundary crossing).
     */
    fun createNovemberStartRequest(name: String = "11月開始"): Map<String, Any> = mapOf(
        "name" to name,
        "startMonth" to 11,
        "startDay" to 1,
    )

    /**
     * Creates a fiscal year pattern creation request with January 1st start (calendar year).
     */
    fun createJanuaryStartRequest(name: String = "1月開始"): Map<String, Any> = mapOf(
        "name" to name,
        "startMonth" to 1,
        "startDay" to 1,
    )

    /**
     * Creates a fiscal year pattern creation request with custom start date.
     */
    fun createPatternRequest(name: String = validName(), startMonth: Int = 4, startDay: Int = 1): Map<String, Any> =
        mapOf(
            "name" to name,
            "startMonth" to startMonth,
            "startDay" to startDay,
        )

    /**
     * Invalid start months for validation testing (valid range: 1-12).
     */
    val invalidStartMonths = listOf(0, -1, 13, 100)

    /**
     * Invalid start days for validation testing (valid range: 1-31).
     */
    val invalidStartDays = listOf(0, -1, 32, 100)

    /**
     * Test cases for fiscal year calculation.
     * Each entry: (pattern, testDate, expectedFiscalYear)
     */
    val fiscalYearCalculationTestCases =
        listOf(
            // April start pattern (4月開始)
            Triple(mapOf("startMonth" to 4, "startDay" to 1), "2025-03-31", 2024), // Before start
            Triple(mapOf("startMonth" to 4, "startDay" to 1), "2025-04-01", 2025), // On start date
            Triple(mapOf("startMonth" to 4, "startDay" to 1), "2025-04-02", 2025), // After start
            Triple(mapOf("startMonth" to 4, "startDay" to 1), "2025-12-31", 2025), // End of year
            // November start pattern (11月開始 - year boundary crossing)
            Triple(mapOf("startMonth" to 11, "startDay" to 1), "2025-10-31", 2024), // Before start
            Triple(mapOf("startMonth" to 11, "startDay" to 1), "2025-11-01", 2025), // On start date
            Triple(mapOf("startMonth" to 11, "startDay" to 1), "2025-11-02", 2025), // After start
            Triple(mapOf("startMonth" to 11, "startDay" to 1), "2026-01-15", 2025), // Next calendar year
            // January start pattern (1月開始 - calendar year)
            Triple(mapOf("startMonth" to 1, "startDay" to 1), "2024-12-31", 2024), // End of fiscal year
            Triple(mapOf("startMonth" to 1, "startDay" to 1), "2025-01-01", 2025), // On start date
            Triple(mapOf("startMonth" to 1, "startDay" to 1), "2025-12-31", 2025), // End of year
        )

    /**
     * Test cases for fiscal year range calculation.
     * Each entry: (pattern, fiscalYear, expectedStart, expectedEnd)
     */
    val fiscalYearRangeTestCases =
        listOf(
            // April start pattern (4月開始)
            Triple(mapOf("startMonth" to 4, "startDay" to 1), 2025, Pair("2025-04-01", "2026-03-31")),
            Triple(mapOf("startMonth" to 4, "startDay" to 1), 2024, Pair("2024-04-01", "2025-03-31")),
            // November start pattern (11月開始)
            Triple(mapOf("startMonth" to 11, "startDay" to 1), 2025, Pair("2025-11-01", "2026-10-31")),
            // January start pattern (1月開始)
            Triple(mapOf("startMonth" to 1, "startDay" to 1), 2025, Pair("2025-01-01", "2025-12-31")),
        )

    /**
     * Invalid pattern names for validation testing.
     */
    val invalidNames =
        listOf(
            "", // Empty
            "   ", // Whitespace only
            "a".repeat(101), // Too long (max 100)
        )
}
