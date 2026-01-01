package com.worklog.fixtures

import java.util.UUID

/**
 * Test fixtures for FiscalYearPattern-related entities.
 * Provides common fiscal year patterns used in testing.
 */
object FiscalYearPatternFixtures {

    /**
     * Creates a valid fiscal year pattern name.
     */
    fun validName(suffix: String = ""): String {
        return "Fiscal Year Pattern${if (suffix.isNotEmpty()) " $suffix" else ""}"
    }

    /**
     * Creates a new random fiscal year pattern ID.
     */
    fun randomId(): UUID = UUID.randomUUID()

    /**
     * Japan Standard Fiscal Year (April 1 start).
     * This is the most common fiscal year pattern in Japan.
     */
    fun japanStandard(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "Japan Standard (April 1)"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startMonth" to 4,
        "startDay" to 1
    )

    /**
     * Calendar Year (January 1 start).
     * Standard calendar year fiscal pattern.
     */
    fun calendarYear(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "Calendar Year (January 1)"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startMonth" to 1,
        "startDay" to 1
    )

    /**
     * US Federal Fiscal Year (October 1 start).
     * Used by US federal government.
     */
    fun usFederal(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "US Federal (October 1)"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startMonth" to 10,
        "startDay" to 1
    )

    /**
     * UK Fiscal Year (April 6 start).
     * UK tax year pattern.
     */
    fun ukFiscal(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "UK Fiscal (April 6)"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startMonth" to 4,
        "startDay" to 6
    )

    /**
     * July 1 Fiscal Year.
     * Common in Australia and other countries.
     */
    fun julyFirst(
        id: UUID = randomId(),
        tenantId: UUID = TenantFixtures.randomId(),
        name: String = "July 1 Fiscal Year"
    ): Map<String, Any> = mapOf(
        "id" to id,
        "tenantId" to tenantId,
        "name" to name,
        "startMonth" to 7,
        "startDay" to 1
    )

    /**
     * Creates a fiscal year pattern creation request.
     */
    fun createRequest(
        name: String = validName(),
        startMonth: Int = 4,
        startDay: Int = 1
    ): Map<String, Any> = mapOf(
        "name" to name,
        "startMonth" to startMonth,
        "startDay" to startDay
    )

    /**
     * Invalid start months for validation testing.
     */
    val invalidStartMonths = listOf(
        0,      // Too low
        -1,     // Negative
        13,     // Too high
        100     // Way too high
    )

    /**
     * Invalid start days for validation testing.
     */
    val invalidStartDays = listOf(
        0,      // Too low
        -1,     // Negative
        32,     // Too high
        100     // Way too high
    )

    /**
     * Invalid date combinations (valid ranges but invalid dates).
     */
    val invalidDateCombinations = listOf(
        Pair(2, 30),    // Feb 30 doesn't exist
        Pair(2, 31),    // Feb 31 doesn't exist
        Pair(4, 31),    // April 31 doesn't exist
        Pair(6, 31),    // June 31 doesn't exist
        Pair(9, 31),    // September 31 doesn't exist
        Pair(11, 31)    // November 31 doesn't exist
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
