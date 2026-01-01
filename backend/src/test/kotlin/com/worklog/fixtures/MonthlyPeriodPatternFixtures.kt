package com.worklog.fixtures

import java.util.UUID

/**
 * Test fixtures for MonthlyPeriodPattern-related entities.
 * Provides common monthly period patterns used in testing.
 */
object MonthlyPeriodPatternFixtures {

    /**
     * Creates a valid monthly period pattern name.
     */
    fun validName(suffix: String = ""): String {
        return "Monthly Period Pattern${if (suffix.isNotEmpty()) " $suffix" else ""}"
    }

    /**
     * Creates a new random monthly period pattern ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Month-End Pattern (1st start).
     * Standard calendar month pattern.
     */
    fun monthEnd(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "Month-End (1st)"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startDay" to 1
    )

    /**
     * 21st Cutoff Pattern.
     * Common salary calculation period (21st to 20th).
     */
    fun twentyFirstCutoff(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "21st Cutoff"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startDay" to 21
    )

    /**
     * 25th Cutoff Pattern.
     * Another common salary calculation period (25th to 24th).
     */
    fun twentyFifthCutoff(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "25th Cutoff"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startDay" to 25
    )

    /**
     * 15th Cutoff Pattern (Bi-monthly).
     * Used for semi-monthly periods.
     */
    fun fifteenthCutoff(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "15th Cutoff"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startDay" to 15
    )

    /**
     * 10th Cutoff Pattern.
     * Early month cutoff period.
     */
    fun tenthCutoff(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "10th Cutoff"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startDay" to 10
    )

    /**
     * Creates a monthly period pattern creation request.
     */
    fun createRequest(
        name: String = validName(),
        startDay: Int = 1
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startDay" to startDay
    )

    /**
     * Invalid start days for validation testing.
     * Note: startDay must be 1-28 to handle February.
     */
    val invalidStartDays = listOf(
        0,      // Too low
        -1,     // Negative
        29,     // Too high (February can't handle)
        30,     // Too high
        31,     // Too high
        100     // Way too high
    )

    /**
     * Valid boundary start days for edge case testing.
     */
    val validBoundaryStartDays = listOf(
        1,      // First day (standard month-end)
        28      // Last valid day (handles February)
    )

    /**
     * Invalid names for validation testing.
     */
    val invalidNames = listOf(
        "",                     // Empty
        "   ",                  // Whitespace only
        "a".repeat(101)        // Too long (max 100)
    )
}
