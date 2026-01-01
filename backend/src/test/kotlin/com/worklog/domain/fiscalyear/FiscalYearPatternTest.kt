package com.worklog.domain.fiscalyear

import com.worklog.domain.shared.DomainException
import com.worklog.fixtures.FiscalYearPatternFixtures
import com.worklog.fixtures.TenantFixtures
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for FiscalYearPattern entity.
 * Tests domain logic for fiscal year calculation without any infrastructure dependencies.
 */
class FiscalYearPatternTest {

    @Test
    fun `create should generate valid fiscal year pattern`() {
        val tenantId = TenantFixtures.randomId()
        val pattern = FiscalYearPattern.create(
            tenantId,
            "Japan Standard",
            4,
            1
        )

        assertNotNull(pattern.id)
        assertEquals(tenantId, pattern.tenantId)
        assertEquals("Japan Standard", pattern.name)
        assertEquals(4, pattern.startMonth)
        assertEquals(1, pattern.startDay)
    }

    @Test
    fun `create should trim whitespace from name`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "  Test Pattern  ",
            4,
            1
        )

        assertEquals("Test Pattern", pattern.name)
    }

    @Test
    fun `create should fail with empty name`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "",
                4,
                1
            )
        }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with blank name`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "   ",
                4,
                1
            )
        }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with name too long`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "a".repeat(101),
                4,
                1
            )
        }
        assertEquals("NAME_TOO_LONG", exception.errorCode)
    }

    @Test
    fun `create should fail with startMonth less than 1`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                0,
                1
            )
        }
        assertEquals("INVALID_START_MONTH", exception.errorCode)
    }

    @Test
    fun `create should fail with startMonth greater than 12`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                13,
                1
            )
        }
        assertEquals("INVALID_START_MONTH", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay less than 1`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                4,
                0
            )
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay greater than 31`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                4,
                32
            )
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with invalid date combination - Feb 30`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                2,
                30
            )
        }
        assertEquals("INVALID_DATE", exception.errorCode)
    }

    @Test
    fun `create should fail with invalid date combination - April 31`() {
        val exception = assertFailsWith<DomainException> {
            FiscalYearPattern.create(
                TenantFixtures.randomId(),
                "Test",
                4,
                31
            )
        }
        assertEquals("INVALID_DATE", exception.errorCode)
    }

    @Test
    fun `getFiscalYear should return correct year for April 1 start - same year`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "Japan Standard",
            4,
            1
        )

        // On April 1, 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 4, 1)))
        
        // After April 1, 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 5, 15)))
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 12, 31)))
    }

    @Test
    fun `getFiscalYear should return correct year for April 1 start - previous year`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "Japan Standard",
            4,
            1
        )

        // Before April 1, 2024 -> FY 2023
        assertEquals(2023, pattern.getFiscalYear(LocalDate.of(2024, 3, 31)))
        assertEquals(2023, pattern.getFiscalYear(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun `getFiscalYear should return correct year for calendar year - January 1 start`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "Calendar Year",
            1,
            1
        )

        // January 1, 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 1, 1)))
        
        // Any date in 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 6, 15)))
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 12, 31)))
    }

    @Test
    fun `getFiscalYear should return correct year for October 1 start - US federal`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "US Federal",
            10,
            1
        )

        // October 1, 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 10, 1)))
        
        // After October 1, 2024 -> FY 2024
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 11, 15)))
        assertEquals(2024, pattern.getFiscalYear(LocalDate.of(2024, 12, 31)))
        
        // Before October 1, 2024 -> FY 2023
        assertEquals(2023, pattern.getFiscalYear(LocalDate.of(2024, 9, 30)))
        assertEquals(2023, pattern.getFiscalYear(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun `getFiscalYearRange should return correct start and end dates for April 1 start`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "Japan Standard",
            4,
            1
        )

        val (start, end) = pattern.getFiscalYearRange(2024)
        
        assertEquals(LocalDate.of(2024, 4, 1), start)
        assertEquals(LocalDate.of(2025, 3, 31), end)
    }

    @Test
    fun `getFiscalYearRange should return correct start and end dates for calendar year`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "Calendar Year",
            1,
            1
        )

        val (start, end) = pattern.getFiscalYearRange(2024)
        
        assertEquals(LocalDate.of(2024, 1, 1), start)
        assertEquals(LocalDate.of(2024, 12, 31), end)
    }

    @Test
    fun `getFiscalYearRange should return correct start and end dates for October 1 start`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "US Federal",
            10,
            1
        )

        val (start, end) = pattern.getFiscalYearRange(2024)
        
        assertEquals(LocalDate.of(2024, 10, 1), start)
        assertEquals(LocalDate.of(2025, 9, 30), end)
    }

    @Test
    fun `getFiscalYearRange should handle leap year correctly for Feb 29`() {
        val pattern = FiscalYearPattern.create(
            TenantFixtures.randomId(),
            "March 1 Start",
            3,
            1
        )

        // FY 2020 (leap year) should end on Feb 29, 2021
        val (start, end) = pattern.getFiscalYearRange(2020)
        
        assertEquals(LocalDate.of(2020, 3, 1), start)
        assertEquals(LocalDate.of(2021, 2, 28), end)  // 2021 is not a leap year
    }
}
