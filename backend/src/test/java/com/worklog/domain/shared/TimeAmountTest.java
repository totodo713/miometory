package com.worklog.domain.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for TimeAmount value object.
 *
 * Tests validation rules:
 * - T059: TimeAmount validation (0.25h increments, max 24h, non-negative)
 *
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("TimeAmount value object")
class TimeAmountTest {

    @Nested
    @DisplayName("Creation and validation")
    class CreationAndValidation {

        @Test
        @DisplayName("should create valid TimeAmount with valid hours")
        void shouldCreateValidTimeAmount() {
            TimeAmount amount = TimeAmount.of(8.0);

            assertEquals(BigDecimal.valueOf(8.00).setScale(2), amount.hours());
        }

        @Test
        @DisplayName("should create TimeAmount from BigDecimal")
        void shouldCreateFromBigDecimal() {
            TimeAmount amount = TimeAmount.of(new BigDecimal("4.50"));

            assertEquals(new BigDecimal("4.50"), amount.hours());
        }

        @Test
        @DisplayName("should create zero hours")
        void shouldCreateZeroHours() {
            TimeAmount zero = TimeAmount.zero();

            assertTrue(zero.isZero());
            assertEquals(BigDecimal.ZERO.setScale(2), zero.hours());
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.25, 0.5, 0.75, 1.0, 8.0, 23.75, 24.0})
        @DisplayName("should accept valid hour values in 0.25 increments")
        void shouldAcceptValidIncrements(double hours) {
            assertDoesNotThrow(() -> TimeAmount.of(hours));
        }

        @Test
        @DisplayName("should throw exception for null hours")
        void shouldRejectNullHours() {
            DomainException exception = assertThrows(DomainException.class, () -> TimeAmount.of((BigDecimal) null));

            assertEquals("TIME_AMOUNT_NULL", exception.getErrorCode());
            assertEquals("Hours cannot be null", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(doubles = {-0.25, -1.0, -24.0})
        @DisplayName("should throw exception for negative hours")
        void shouldRejectNegativeHours(double hours) {
            DomainException exception = assertThrows(DomainException.class, () -> TimeAmount.of(hours));

            assertEquals("TIME_AMOUNT_NEGATIVE", exception.getErrorCode());
            assertEquals("Hours cannot be negative", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(doubles = {24.01, 24.25, 25.0, 100.0})
        @DisplayName("should throw exception for hours exceeding 24")
        void shouldRejectHoursExceedingMax(double hours) {
            DomainException exception = assertThrows(DomainException.class, () -> TimeAmount.of(hours));

            assertEquals("TIME_AMOUNT_EXCEEDS_MAX", exception.getErrorCode());
            assertEquals("Hours cannot exceed 24", exception.getMessage());
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.1, 0.3, 0.4, 0.6, 1.1, 8.33, 23.99})
        @DisplayName("should throw exception for invalid increments (not 0.25)")
        void shouldRejectInvalidIncrements(double hours) {
            DomainException exception = assertThrows(DomainException.class, () -> TimeAmount.of(hours));

            assertEquals("TIME_AMOUNT_INVALID_INCREMENT", exception.getErrorCode());
            assertEquals("Hours must be in 0.25 hour increments (15-minute intervals)", exception.getMessage());
        }

        @Test
        @DisplayName("should round to 2 decimal places for consistent comparison")
        void shouldRoundTo2DecimalPlaces() {
            TimeAmount amount = TimeAmount.of(new BigDecimal("8.5000"));

            assertEquals(new BigDecimal("8.50"), amount.hours());
        }
    }

    @Nested
    @DisplayName("Arithmetic operations")
    class ArithmeticOperations {

        @Test
        @DisplayName("should add two TimeAmounts correctly")
        void shouldAddTimeAmounts() {
            TimeAmount amount1 = TimeAmount.of(4.0);
            TimeAmount amount2 = TimeAmount.of(3.5);

            TimeAmount result = amount1.plus(amount2);

            assertEquals(new BigDecimal("7.50"), result.hours());
        }

        @Test
        @DisplayName("should throw exception when addition exceeds 24 hours")
        void shouldRejectAdditionExceedingMax() {
            TimeAmount amount1 = TimeAmount.of(20.0);
            TimeAmount amount2 = TimeAmount.of(5.0);

            assertThrows(DomainException.class, () -> amount1.plus(amount2));
        }

        @Test
        @DisplayName("should subtract two TimeAmounts correctly")
        void shouldSubtractTimeAmounts() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(3.5);

            TimeAmount result = amount1.minus(amount2);

            assertEquals(new BigDecimal("4.50"), result.hours());
        }

        @Test
        @DisplayName("should throw exception when subtraction results in negative")
        void shouldRejectSubtractionResultingInNegative() {
            TimeAmount amount1 = TimeAmount.of(3.0);
            TimeAmount amount2 = TimeAmount.of(5.0);

            assertThrows(DomainException.class, () -> amount1.minus(amount2));
        }

        @Test
        @DisplayName("should allow subtraction resulting in zero")
        void shouldAllowSubtractionToZero() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(8.0);

            TimeAmount result = amount1.minus(amount2);

            assertTrue(result.isZero());
        }
    }

    @Nested
    @DisplayName("Comparison operations")
    class ComparisonOperations {

        @Test
        @DisplayName("should correctly identify greater than")
        void shouldIdentifyGreaterThan() {
            TimeAmount larger = TimeAmount.of(8.0);
            TimeAmount smaller = TimeAmount.of(4.0);

            assertTrue(larger.isGreaterThan(smaller));
            assertFalse(smaller.isGreaterThan(larger));
        }

        @Test
        @DisplayName("should correctly identify less than")
        void shouldIdentifyLessThan() {
            TimeAmount smaller = TimeAmount.of(4.0);
            TimeAmount larger = TimeAmount.of(8.0);

            assertTrue(smaller.isLessThan(larger));
            assertFalse(larger.isLessThan(smaller));
        }

        @Test
        @DisplayName("should correctly identify zero")
        void shouldIdentifyZero() {
            TimeAmount zero1 = TimeAmount.zero();
            TimeAmount zero2 = TimeAmount.of(0.0);
            TimeAmount nonZero = TimeAmount.of(1.0);

            assertTrue(zero1.isZero());
            assertTrue(zero2.isZero());
            assertFalse(nonZero.isZero());
        }

        @Test
        @DisplayName("should return false for isGreaterThan when equal")
        void shouldReturnFalseForGreaterThanWhenEqual() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(8.0);

            assertFalse(amount1.isGreaterThan(amount2));
        }

        @Test
        @DisplayName("should return false for isLessThan when equal")
        void shouldReturnFalseForLessThanWhenEqual() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(8.0);

            assertFalse(amount1.isLessThan(amount2));
        }
    }

    @Nested
    @DisplayName("Equality and toString")
    class EqualityAndToString {

        @Test
        @DisplayName("should be equal when hours are equal")
        void shouldBeEqualWhenHoursEqual() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(8.0);

            assertEquals(amount1, amount2);
        }

        @Test
        @DisplayName("should not be equal when hours differ")
        void shouldNotBeEqualWhenHoursDiffer() {
            TimeAmount amount1 = TimeAmount.of(8.0);
            TimeAmount amount2 = TimeAmount.of(7.5);

            assertNotEquals(amount1, amount2);
        }

        @Test
        @DisplayName("should format toString correctly")
        void shouldFormatToString() {
            TimeAmount amount = TimeAmount.of(8.5);

            // Should contain hours value and 'h' suffix
            String result = amount.toString();
            assertTrue(result.endsWith("h"), "Should end with 'h'");
            assertTrue(result.contains("8.5") || result.contains("8.50"), "Should contain 8.5 or 8.50");
        }

        @Test
        @DisplayName("should format zero hours correctly")
        void shouldFormatZeroHours() {
            TimeAmount zero = TimeAmount.zero();

            // Should contain zero value and 'h' suffix
            String result = zero.toString();
            assertTrue(result.endsWith("h"), "Should end with 'h'");
            assertTrue(result.startsWith("0"), "Should start with 0");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle exactly 24 hours")
        void shouldHandleExactly24Hours() {
            assertDoesNotThrow(() -> TimeAmount.of(24.0));

            TimeAmount maxHours = TimeAmount.of(24.0);
            assertEquals(new BigDecimal("24.00"), maxHours.hours());
        }

        @Test
        @DisplayName("should handle very small valid increment (0.25)")
        void shouldHandleSmallestIncrement() {
            TimeAmount smallest = TimeAmount.of(0.25);

            assertEquals(new BigDecimal("0.25"), smallest.hours());
            assertFalse(smallest.isZero());
        }

        @Test
        @DisplayName("should handle complex arithmetic staying within bounds")
        void shouldHandleComplexArithmetic() {
            TimeAmount a = TimeAmount.of(10.0);
            TimeAmount b = TimeAmount.of(5.5);
            TimeAmount c = TimeAmount.of(2.25);

            // 10 + 5.5 - 2.25 = 13.25
            TimeAmount result = a.plus(b).minus(c);

            assertEquals(new BigDecimal("13.25"), result.hours());
        }

        @Test
        @DisplayName("should maintain precision across operations")
        void shouldMaintainPrecision() {
            TimeAmount a = TimeAmount.of(0.25);
            TimeAmount b = TimeAmount.of(0.25);
            TimeAmount c = TimeAmount.of(0.25);
            TimeAmount d = TimeAmount.of(0.25);

            // 0.25 + 0.25 + 0.25 + 0.25 = 1.0
            TimeAmount result = a.plus(b).plus(c).plus(d);

            assertEquals(new BigDecimal("1.00"), result.hours());
        }
    }
}
