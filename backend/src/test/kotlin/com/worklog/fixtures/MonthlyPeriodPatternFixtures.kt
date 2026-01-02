package com.worklog.fixtures

import com.worklog.domain.monthlyperiod.MonthlyPeriodPattern
import com.worklog.domain.tenant.TenantId
import java.util.UUID

/**
 * Test fixtures for MonthlyPeriodPattern-related entities.
 */
object MonthlyPeriodPatternFixtures {
    
    /**
     * Kotlin-friendly wrapper for creating MonthlyPeriodPattern with named arguments.
     */
    fun createPattern(
        tenantId: TenantId,
        name: String,
        startDay: Int
    ): MonthlyPeriodPattern {
        return MonthlyPeriodPattern.create(tenantId, name, startDay)
    }

    /**
     * Creates a valid monthly period pattern name.
     */
    fun validName(suffix: String = ""): String {
        return "Monthly Period Pattern${if (suffix.isNotEmpty()) " $suffix" else ""}"
    }

    /**
     * Creates a new random pattern ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Creates a monthly period pattern request with 21st day start (21日締め).
     */
    fun create21stDayStartRequest(
        name: String = "21日締め"
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startDay" to 21
    )

    /**
     * Creates a monthly period pattern request with 1st day start (1日締め - month-end close).
     */
    fun create1stDayStartRequest(
        name: String = "1日締め"
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startDay" to 1
    )

    /**
     * Creates a monthly period pattern request with 15th day start (15日締め).
     */
    fun create15thDayStartRequest(
        name: String = "15日締め"
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startDay" to 15
    )

    /**
     * Creates a monthly period pattern creation request with custom start day.
     */
    fun createPatternRequest(
        name: String = validName(),
        startDay: Int = 21
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startDay" to startDay
    )

    /**
     * Invalid start days for validation testing (valid range: 1-28).
     */
    val invalidStartDays = listOf(0, -1, 29, 30, 31, 100)

    /**
     * Test cases for monthly period calculation.
     * Each entry: (patternStartDay, testDate, expectedPeriodStart, expectedPeriodEnd, expectedDisplayMonth, expectedDisplayYear)
     */
    val monthlyPeriodCalculationTestCases = listOf(
        // 21日締め pattern (startDay = 21)
        // Date within period: 2025-01-15 → period: 2024-12-21 to 2025-01-20, display: 2025-01
        Tuple6(21, "2025-01-15", "2024-12-21", "2025-01-20", 1, 2025),
        // Date at period start: 2025-01-21 → period: 2025-01-21 to 2025-02-20, display: 2025-02
        Tuple6(21, "2025-01-21", "2025-01-21", "2025-02-20", 2, 2025),
        // Date after period start: 2025-01-25 → period: 2025-01-21 to 2025-02-20, display: 2025-02
        Tuple6(21, "2025-01-25", "2025-01-21", "2025-02-20", 2, 2025),
        // Year boundary crossing: 2025-12-25 → period: 2025-12-21 to 2026-01-20, display: 2026-01
        Tuple6(21, "2025-12-25", "2025-12-21", "2026-01-20", 1, 2026),
        // February handling: 2025-02-15 → period: 2025-01-21 to 2025-02-20, display: 2025-02
        Tuple6(21, "2025-02-15", "2025-01-21", "2025-02-20", 2, 2025),

        // 1日締め pattern (startDay = 1) - month-end close
        // Date at month start: 2025-01-01 → period: 2025-01-01 to 2025-01-31, display: 2025-01
        Tuple6(1, "2025-01-01", "2025-01-01", "2025-01-31", 1, 2025),
        // Date in middle of month: 2025-01-15 → period: 2025-01-01 to 2025-01-31, display: 2025-01
        Tuple6(1, "2025-01-15", "2025-01-01", "2025-01-31", 1, 2025),
        // Date at month end: 2025-01-31 → period: 2025-01-01 to 2025-01-31, display: 2025-01
        Tuple6(1, "2025-01-31", "2025-01-01", "2025-01-31", 1, 2025),
        // February (28 days): 2025-02-15 → period: 2025-02-01 to 2025-02-28, display: 2025-02
        Tuple6(1, "2025-02-15", "2025-02-01", "2025-02-28", 2, 2025),
        // February (leap year, 29 days): 2024-02-15 → period: 2024-02-01 to 2024-02-29, display: 2024-02
        Tuple6(1, "2024-02-15", "2024-02-01", "2024-02-29", 2, 2024),

        // 15日締め pattern (startDay = 15)
        // Before period start: 2025-01-10 → period: 2024-12-15 to 2025-01-14, display: 2025-01
        Tuple6(15, "2025-01-10", "2024-12-15", "2025-01-14", 1, 2025),
        // At period start: 2025-01-15 → period: 2025-01-15 to 2025-02-14, display: 2025-02
        Tuple6(15, "2025-01-15", "2025-01-15", "2025-02-14", 2, 2025),
        // After period start: 2025-01-20 → period: 2025-01-15 to 2025-02-14, display: 2025-02
        Tuple6(15, "2025-01-20", "2025-01-15", "2025-02-14", 2, 2025),
    )

    /**
     * Invalid pattern names for validation testing.
     */
    val invalidNames = listOf(
        "",                             // Empty
        "   ",                          // Whitespace only
        "a".repeat(101),                // Too long (max 100)
    )

    /**
     * Helper data class for test cases with 6 values.
     */
    data class Tuple6<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F
    )
}
