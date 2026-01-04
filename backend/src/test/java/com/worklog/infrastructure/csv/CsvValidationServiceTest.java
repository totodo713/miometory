package com.worklog.infrastructure.csv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvValidationService.
 * 
 * Tests all validation rules for CSV import:
 * - Date format and constraints
 * - Project code requirements
 * - Hours format and range
 * - Notes length limits
 */
class CsvValidationServiceTest {

    private CsvValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new CsvValidationService();
    }

    // ============ Valid Cases ============

    @Test
    @DisplayName("Valid row with all fields should pass validation")
    void testValidRowAllFields() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", "Test notes");
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Valid row with empty notes should pass validation")
    void testValidRowEmptyNotes() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", "");
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @ParameterizedTest
    @DisplayName("Valid hours in 0.25 increments should pass validation")
    @ValueSource(strings = {"0.25", "0.50", "0.75", "1.00", "4.50", "8.00", "12.75", "24.00"})
    void testValidHoursIncrements(String hours) {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", hours, "Test");
        
        assertTrue(result.isValid(), "Hours " + hours + " should be valid");
    }

    // ============ Date Validation Tests ============

    @Test
    @DisplayName("Empty date should fail validation")
    void testEmptyDate() {
        var result = validationService.validateRow(1, "", "PRJ-001", "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Date is required"));
    }

    @Test
    @DisplayName("Null date should fail validation")
    void testNullDate() {
        var result = validationService.validateRow(1, null, "PRJ-001", "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Date is required"));
    }

    @ParameterizedTest
    @DisplayName("Invalid date formats should fail validation")
    @ValueSource(strings = {"2026/01/02", "01-02-2026", "2026-1-2", "invalid", "2026-13-01", "2026-01-32"})
    void testInvalidDateFormats(String invalidDate) {
        var result = validationService.validateRow(1, invalidDate, "PRJ-001", "8.00", "Test");
        
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    // ============ Project Code Validation Tests ============

    @Test
    @DisplayName("Empty project code should fail validation")
    void testEmptyProjectCode() {
        var result = validationService.validateRow(1, "2026-01-02", "", "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Project Code is required"));
    }

    @Test
    @DisplayName("Null project code should fail validation")
    void testNullProjectCode() {
        var result = validationService.validateRow(1, "2026-01-02", null, "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Project Code is required"));
    }

    @Test
    @DisplayName("Project code exceeding 50 characters should fail validation")
    void testProjectCodeTooLong() {
        String longCode = "A".repeat(51);
        var result = validationService.validateRow(1, "2026-01-02", longCode, "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Project Code must not exceed 50 characters"));
    }

    @Test
    @DisplayName("Project code with exactly 50 characters should pass validation")
    void testProjectCodeMaxLength() {
        String maxCode = "A".repeat(50);
        var result = validationService.validateRow(1, "2026-01-02", maxCode, "8.00", "Test");
        
        assertTrue(result.isValid());
    }

    // ============ Hours Validation Tests ============

    @Test
    @DisplayName("Empty hours should fail validation")
    void testEmptyHours() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours is required"));
    }

    @Test
    @DisplayName("Null hours should fail validation")
    void testNullHours() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", null, "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours is required"));
    }

    @ParameterizedTest
    @DisplayName("Invalid hours formats should fail validation")
    @ValueSource(strings = {"abc", "8:00", "8,00", "eight"})
    void testInvalidHoursFormats(String invalidHours) {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", invalidHours, "Test");
        
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @ParameterizedTest
    @DisplayName("Hours not in 0.25 increments should fail validation")
    @ValueSource(strings = {"0.10", "0.33", "1.23", "8.01", "12.99"})
    void testInvalidHoursIncrements(String invalidHours) {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", invalidHours, "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours must be in 0.25 increments (e.g., 1.00, 1.25, 1.50, 1.75)"));
    }

    @Test
    @DisplayName("Negative hours should fail validation")
    void testNegativeHours() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "-1.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours must be 0 or greater"));
    }

    @Test
    @DisplayName("Hours exceeding 24 should fail validation")
    void testHoursExceedingMax() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "24.25", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours must not exceed 24"));
    }

    @Test
    @DisplayName("Zero hours should pass validation")
    void testZeroHours() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "0.00", "Test");
        
        assertTrue(result.isValid());
    }

    // ============ Notes Validation Tests ============

    @Test
    @DisplayName("Notes exceeding 500 characters should fail validation")
    void testNotesTooLong() {
        String longNotes = "A".repeat(501);
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", longNotes);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Notes must not exceed 500 characters"));
    }

    @Test
    @DisplayName("Notes with exactly 500 characters should pass validation")
    void testNotesMaxLength() {
        String maxNotes = "A".repeat(500);
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", maxNotes);
        
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Null notes should pass validation")
    void testNullNotes() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", null);
        
        assertTrue(result.isValid());
    }

    // ============ Multiple Errors Tests ============

    @Test
    @DisplayName("Row with multiple errors should collect all errors")
    void testMultipleErrors() {
        var result = validationService.validateRow(
            1, 
            "invalid-date", 
            "", 
            "abc", 
            "A".repeat(501)
        );
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() >= 3, "Should have at least 3 errors");
    }

    // ============ Row Number Tests ============

    @Test
    @DisplayName("ValidationResult should include row number")
    void testRowNumber() {
        var result = validationService.validateRow(42, "2026-01-02", "PRJ-001", "8.00", "Test");
        
        assertEquals(42, result.getRowNumber());
    }

    // ============ Error Message Tests ============

    @Test
    @DisplayName("getErrorMessage should format errors with row number")
    void testGetErrorMessage() {
        var result = validationService.validateRow(5, "", "", "", "");
        
        String errorMessage = result.getErrorMessage();
        assertTrue(errorMessage.contains("Row 5"));
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("getErrorMessage for valid row should return empty string")
    void testGetErrorMessageForValidRow() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "8.00", "Test");
        
        String errorMessage = result.getErrorMessage();
        assertTrue(errorMessage.isEmpty());
    }

    // ============ Edge Cases ============

    @ParameterizedTest
    @DisplayName("Date boundaries should be validated correctly")
    @CsvSource({
        "2025-12-31, true",
        "2026-01-01, true", 
        "2026-12-31, true",
        "2027-01-01, true"
    })
    void testDateBoundaries(String date, boolean expectedValid) {
        var result = validationService.validateRow(1, date, "PRJ-001", "8.00", "Test");
        
        assertEquals(expectedValid, result.isValid() || !result.getErrors().stream()
            .anyMatch(e -> e.contains("Date cannot be in the future")));
    }

    @Test
    @DisplayName("Whitespace-only project code should fail validation")
    void testWhitespaceProjectCode() {
        var result = validationService.validateRow(1, "2026-01-02", "   ", "8.00", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Project Code is required"));
    }

    @Test
    @DisplayName("Whitespace-only hours should fail validation")
    void testWhitespaceHours() {
        var result = validationService.validateRow(1, "2026-01-02", "PRJ-001", "   ", "Test");
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Hours is required"));
    }
}
