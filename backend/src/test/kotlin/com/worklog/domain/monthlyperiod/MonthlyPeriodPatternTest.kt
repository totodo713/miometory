package com.worklog.domain.monthlyperiod

import com.worklog.domain.shared.DomainException
import com.worklog.fixtures.MonthlyPeriodPatternFixtures
import com.worklog.fixtures.TenantFixtures
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for MonthlyPeriodPattern entity.
 * Tests domain logic for monthly period calculation without any infrastructure dependencies.
 */
class MonthlyPeriodPatternTest {

    @Test
    fun `create should generate valid monthly period pattern`() {
        val tenantId = TenantFixtures.randomId()
        val pattern = MonthlyPeriodPattern.create(
            tenantId,
            "Month-End",
            1
        )

        assertNotNull(pattern.id)
        assertEquals(tenantId, pattern.tenantId)
        assertEquals("Month-End", pattern.name)
        assertEquals(1, pattern.startDay)
    }

    @Test
    fun `create should trim whitespace from name`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "  Test Pattern  ",
            1
        )

        assertEquals("Test Pattern", pattern.name)
    }

    @Test
    fun `create should fail with empty name`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "",
                1
            )
        }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with blank name`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "   ",
                1
            )
        }
        assertEquals("NAME_REQUIRED", exception.errorCode)
    }

    @Test
    fun `create should fail with name too long`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "a".repeat(101),
                1
            )
        }
        assertEquals("NAME_TOO_LONG", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay less than 1`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "Test",
                0
            )
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay equal to 29`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "Test",
                29
            )
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should fail with startDay greater than 28`() {
        val exception = assertFailsWith<DomainException> {
            MonthlyPeriodPattern.create(
                TenantFixtures.randomId(),
                "Test",
                30
            )
        }
        assertEquals("INVALID_START_DAY", exception.errorCode)
    }

    @Test
    fun `create should succeed with startDay 28 (boundary)`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "Test",
            28
        )

        assertEquals(28, pattern.startDay)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 1st start - same month`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "Month-End",
            1
        )

        // January 15, 2024 -> Period starts Jan 1
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 15))
        
        assertEquals(LocalDate.of(2024, 1, 1), period.start)
        assertEquals(LocalDate.of(2024, 1, 31), period.end)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 1st start - exactly on start`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "Month-End",
            1
        )

        // January 1, 2024 -> Period starts Jan 1
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 1))
        
        assertEquals(LocalDate.of(2024, 1, 1), period.start)
        assertEquals(LocalDate.of(2024, 1, 31), period.end)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 21st cutoff - after cutoff`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "21st Cutoff",
            21
        )

        // January 25, 2024 -> Period starts Jan 21
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 25))
        
        assertEquals(LocalDate.of(2024, 1, 21), period.start)
        assertEquals(LocalDate.of(2024, 2, 20), period.end)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 21st cutoff - before cutoff`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "21st Cutoff",
            21
        )

        // January 15, 2024 -> Period starts Dec 21
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 15))
        
        assertEquals(LocalDate.of(2023, 12, 21), period.start)
        assertEquals(LocalDate.of(2024, 1, 20), period.end)
    }

    @Test
    fun `getMonthlyPeriod should return correct period for 21st cutoff - exactly on cutoff`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "21st Cutoff",
            21
        )

        // January 21, 2024 -> Period starts Jan 21
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 21))
        
        assertEquals(LocalDate.of(2024, 1, 21), period.start)
        assertEquals(LocalDate.of(2024, 2, 20), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle February correctly for 1st start`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "Month-End",
            1
        )

        // February 15, 2024 (leap year) -> Period ends Feb 29
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 2, 15))
        
        assertEquals(LocalDate.of(2024, 2, 1), period.start)
        assertEquals(LocalDate.of(2024, 2, 29), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle February correctly for non-leap year`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "Month-End",
            1
        )

        // February 15, 2023 (non-leap year) -> Period ends Feb 28
        val period = pattern.getMonthlyPeriod(LocalDate.of(2023, 2, 15))
        
        assertEquals(LocalDate.of(2023, 2, 1), period.start)
        assertEquals(LocalDate.of(2023, 2, 28), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle year boundary for 25th cutoff`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "25th Cutoff",
            25
        )

        // December 30, 2023 -> Period starts Dec 25
        val period = pattern.getMonthlyPeriod(LocalDate.of(2023, 12, 30))
        
        assertEquals(LocalDate.of(2023, 12, 25), period.start)
        assertEquals(LocalDate.of(2024, 1, 24), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle year boundary for 25th cutoff - early January`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "25th Cutoff",
            25
        )

        // January 10, 2024 -> Period starts Dec 25, 2023
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 1, 10))
        
        assertEquals(LocalDate.of(2023, 12, 25), period.start)
        assertEquals(LocalDate.of(2024, 1, 24), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle 28th cutoff in February`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "28th Cutoff",
            28
        )

        // February 28, 2024 (leap year) -> Period starts Feb 28
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 2, 28))
        
        assertEquals(LocalDate.of(2024, 2, 28), period.start)
        assertEquals(LocalDate.of(2024, 3, 27), period.end)
    }

    @Test
    fun `getMonthlyPeriod should handle month with 30 days for day 15 cutoff`() {
        val pattern = MonthlyPeriodPattern.create(
            TenantFixtures.randomId(),
            "15th Cutoff",
            15
        )

        // April 20, 2024 (April has 30 days) -> Period starts April 15
        val period = pattern.getMonthlyPeriod(LocalDate.of(2024, 4, 20))
        
        assertEquals(LocalDate.of(2024, 4, 15), period.start)
        assertEquals(LocalDate.of(2024, 5, 14), period.end)
    }
}
