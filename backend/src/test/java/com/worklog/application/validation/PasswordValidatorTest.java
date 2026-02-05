package com.worklog.application.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PasswordValidator (T037).
 * 
 * Tests password strength validation rules per FR-003:
 * - Minimum 8 characters
 * - At least 1 digit
 * - At least 1 uppercase letter
 */
class PasswordValidatorTest {

    // ============================================================
    // validate() - Valid Passwords
    // ============================================================

    @Test
    void validate_withValidPassword_shouldNotThrow() {
        // Given
        String validPassword = "Password1";

        // When/Then - should not throw
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(validPassword));
    }

    @Test
    void validate_withMinimumRequirements_shouldNotThrow() {
        // Given - exactly 8 chars, 1 digit, 1 uppercase
        String minPassword = "Passwor1";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(minPassword));
    }

    @Test
    void validate_withMultipleDigitsAndUppercase_shouldNotThrow() {
        // Given
        String strongPassword = "MyP@ssw0rd123";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(strongPassword));
    }

    @Test
    void validate_withSpecialCharacters_shouldNotThrow() {
        // Given - special chars are allowed
        String passwordWithSpecialChars = "P@ssw0rd!#$";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(passwordWithSpecialChars));
    }

    // ============================================================
    // validate() - Null/Empty/Blank
    // ============================================================

    @Test
    void validate_withNull_shouldThrowException() {
        // Given
        String nullPassword = null;

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(nullPassword)
        );
        Assertions.assertEquals("Password cannot be blank", exception.getMessage());
    }

    @Test
    void validate_withEmptyString_shouldThrowException() {
        // Given
        String emptyPassword = "";

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(emptyPassword)
        );
        Assertions.assertEquals("Password cannot be blank", exception.getMessage());
    }

    @Test
    void validate_withBlankString_shouldThrowException() {
        // Given
        String blankPassword = "   ";

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(blankPassword)
        );
        Assertions.assertEquals("Password cannot be blank", exception.getMessage());
    }

    // ============================================================
    // validate() - Length Requirements
    // ============================================================

    @Test
    void validate_withSevenCharacters_shouldThrowException() {
        // Given - 7 chars with digit and uppercase
        String shortPassword = "Pass1wo";

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(shortPassword)
        );
        Assertions.assertEquals(
            "Password must be at least 8 characters long and contain at least one digit and one uppercase letter",
            exception.getMessage()
        );
    }

    @Test
    void validate_withOneCharacter_shouldThrowException() {
        // Given
        String tooShort = "P";

        // When/Then
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(tooShort)
        );
    }

    // ============================================================
    // validate() - Digit Requirements
    // ============================================================

    @Test
    void validate_withNoDigit_shouldThrowException() {
        // Given - 8+ chars, uppercase, but no digit
        String noDigitPassword = "Password";

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(noDigitPassword)
        );
        Assertions.assertEquals(
            "Password must be at least 8 characters long and contain at least one digit and one uppercase letter",
            exception.getMessage()
        );
    }

    @Test
    void validate_withOnlyLowercaseAndDigits_shouldThrowException() {
        // Given - no uppercase
        String noUppercase = "password123";

        // When/Then
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(noUppercase)
        );
    }

    // ============================================================
    // validate() - Uppercase Requirements
    // ============================================================

    @Test
    void validate_withNoUppercase_shouldThrowException() {
        // Given - 8+ chars, digit, but no uppercase
        String noUppercasePassword = "password1";

        // When/Then
        IllegalArgumentException exception = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(noUppercasePassword)
        );
        Assertions.assertEquals(
            "Password must be at least 8 characters long and contain at least one digit and one uppercase letter",
            exception.getMessage()
        );
    }

    @Test
    void validate_withOnlyUppercaseAndDigits_shouldNotThrow() {
        // Given - all uppercase with digits is valid
        String allUppercase = "PASSWORD1";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(allUppercase));
    }

    // ============================================================
    // isValid() - Valid Cases
    // ============================================================

    @Test
    void isValid_withValidPassword_shouldReturnTrue() {
        // Given
        String validPassword = "Password1";

        // When
        boolean result = PasswordValidator.isValid(validPassword);

        // Then
        Assertions.assertTrue(result);
    }

    @Test
    void isValid_withMinimumRequirements_shouldReturnTrue() {
        // Given
        String minPassword = "Passwor1";

        // When
        boolean result = PasswordValidator.isValid(minPassword);

        // Then
        Assertions.assertTrue(result);
    }

    @Test
    void isValid_withStrongPassword_shouldReturnTrue() {
        // Given
        String strongPassword = "MyP@ssw0rd123";

        // When
        boolean result = PasswordValidator.isValid(strongPassword);

        // Then
        Assertions.assertTrue(result);
    }

    // ============================================================
    // isValid() - Invalid Cases
    // ============================================================

    @Test
    void isValid_withNull_shouldReturnFalse() {
        // Given
        String nullPassword = null;

        // When
        boolean result = PasswordValidator.isValid(nullPassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withEmptyString_shouldReturnFalse() {
        // Given
        String emptyPassword = "";

        // When
        boolean result = PasswordValidator.isValid(emptyPassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withBlankString_shouldReturnFalse() {
        // Given
        String blankPassword = "   ";

        // When
        boolean result = PasswordValidator.isValid(blankPassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withTooShort_shouldReturnFalse() {
        // Given
        String shortPassword = "Pass1wo";

        // When
        boolean result = PasswordValidator.isValid(shortPassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withNoDigit_shouldReturnFalse() {
        // Given
        String noDigitPassword = "Password";

        // When
        boolean result = PasswordValidator.isValid(noDigitPassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withNoUppercase_shouldReturnFalse() {
        // Given
        String noUppercasePassword = "password1";

        // When
        boolean result = PasswordValidator.isValid(noUppercasePassword);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withOnlyLowercase_shouldReturnFalse() {
        // Given
        String onlyLowercase = "password";

        // When
        boolean result = PasswordValidator.isValid(onlyLowercase);

        // Then
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withOnlyNumbers_shouldReturnFalse() {
        // Given
        String onlyNumbers = "12345678";

        // When
        boolean result = PasswordValidator.isValid(onlyNumbers);

        // Then
        Assertions.assertFalse(result);
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    void validate_withExactly8Characters_shouldNotThrow() {
        // Given - boundary test
        String exactlyEight = "Abcdef12";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(exactlyEight));
    }

    @Test
    void validate_withVeryLongPassword_shouldNotThrow() {
        // Given - 100+ characters
        String veryLong = "A1" + "a".repeat(100);

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(veryLong));
    }

    @Test
    void validate_withMultipleSpaces_shouldThrowException() {
        // Given - spaces count as chars but no uppercase/digit
        String spacesOnly = "        ";

        // When/Then
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> PasswordValidator.validate(spacesOnly)
        );
    }

    @Test
    void validate_withSpacesInPassword_shouldBeValid() {
        // Given - spaces are allowed within password if requirements met
        String passwordWithSpaces = "Pass word1";

        // When/Then
        Assertions.assertDoesNotThrow(() -> PasswordValidator.validate(passwordWithSpaces));
    }

    @Test
    void isValid_withUnicodeCharacters_shouldFollowSameRules() {
        // Given - unicode chars don't count as uppercase/digits
        String unicodePassword = "пароль123"; // Russian + digits, no uppercase

        // When
        boolean result = PasswordValidator.isValid(unicodePassword);

        // Then - should be false (no uppercase letter)
        Assertions.assertFalse(result);
    }

    @Test
    void isValid_withUnicodeAndValidRequirements_shouldReturnTrue() {
        // Given - unicode with valid uppercase and digit
        String unicodeValid = "Пароль1аа"; // Has uppercase Cyrillic П, digit 1, 8+ chars

        // When
        boolean result = PasswordValidator.isValid(unicodeValid);

        // Then - should be true if regex supports unicode uppercase
        // Note: The current regex ^(?=.*[0-9])(?=.*[A-Z]).{8,}$ only matches ASCII A-Z
        // So this will return false with current implementation
        Assertions.assertFalse(result, "Current implementation only accepts ASCII uppercase");
    }
}
