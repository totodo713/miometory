package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DateInfo (Record Validation)")
class DateInfoTest {

    private static final LocalDate DATE = LocalDate.of(2026, 2, 15);
    private static final LocalDate FY_START = LocalDate.of(2025, 4, 1);
    private static final LocalDate FY_END = LocalDate.of(2026, 3, 31);
    private static final LocalDate MP_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate MP_END = LocalDate.of(2026, 2, 28);
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID FY_PATTERN_ID = UUID.randomUUID();
    private static final UUID MP_PATTERN_ID = UUID.randomUUID();

    private DateInfo validDateInfo() {
        return new DateInfo(
                DATE,
                2025,
                FY_START,
                FY_END,
                MP_START,
                MP_END,
                FY_PATTERN_ID,
                MP_PATTERN_ID,
                ORG_ID,
                "organization:" + ORG_ID,
                "organization:" + ORG_ID);
    }

    @Test
    @DisplayName("should create valid DateInfo with all fields")
    void shouldCreateValidDateInfo() {
        DateInfo info = validDateInfo();
        assertEquals(DATE, info.date());
        assertEquals(2025, info.fiscalYear());
        assertEquals(FY_PATTERN_ID, info.fiscalYearPatternId());
        assertEquals(MP_PATTERN_ID, info.monthlyPeriodPatternId());
        assertEquals("organization:" + ORG_ID, info.fiscalYearSource());
    }

    @Test
    @DisplayName("should allow null pattern IDs for system default")
    void shouldAllowNullPatternIds() {
        DateInfo info =
                new DateInfo(DATE, 2025, FY_START, FY_END, MP_START, MP_END, null, null, ORG_ID, "system", "system");
        assertNull(info.fiscalYearPatternId());
        assertNull(info.monthlyPeriodPatternId());
    }

    @Test
    @DisplayName("should reject null date")
    void shouldRejectNullDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        null,
                        2025,
                        FY_START,
                        FY_END,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null fiscalYearStart")
    void shouldRejectNullFiscalYearStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        null,
                        FY_END,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null fiscalYearEnd")
    void shouldRejectNullFiscalYearEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        null,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null monthlyPeriodStart")
    void shouldRejectNullMonthlyPeriodStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        FY_END,
                        null,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null monthlyPeriodEnd")
    void shouldRejectNullMonthlyPeriodEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        FY_END,
                        MP_START,
                        null,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null organizationId")
    void shouldRejectNullOrganizationId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        FY_END,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        null,
                        "system",
                        "system"));
    }

    @Test
    @DisplayName("should reject null fiscalYearSource")
    void shouldRejectNullFiscalYearSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        FY_END,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        null,
                        "system"));
    }

    @Test
    @DisplayName("should reject null monthlyPeriodSource")
    void shouldRejectNullMonthlyPeriodSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DateInfo(
                        DATE,
                        2025,
                        FY_START,
                        FY_END,
                        MP_START,
                        MP_END,
                        FY_PATTERN_ID,
                        MP_PATTERN_ID,
                        ORG_ID,
                        "system",
                        null));
    }
}
