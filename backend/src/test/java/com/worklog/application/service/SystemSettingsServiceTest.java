package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.settings.SystemDefaultFiscalYearPattern;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodPattern;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingsService")
class SystemSettingsServiceTest {

    @Mock
    private SystemDefaultSettingsRepository repository;

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingsService(repository);
    }

    @Nested
    @DisplayName("getDefaultPatterns")
    class GetDefaultPatterns {

        @Test
        @DisplayName("should return combined fiscal year and monthly period defaults")
        void shouldReturnCombinedDefaults() {
            when(repository.getDefaultFiscalYearPattern()).thenReturn(new SystemDefaultFiscalYearPattern(4, 1));
            when(repository.getDefaultMonthlyPeriodPattern()).thenReturn(new SystemDefaultMonthlyPeriodPattern(1));

            SystemSettingsService.SystemDefaultPatterns result = service.getDefaultPatterns();

            assertEquals(4, result.fiscalYearStartMonth());
            assertEquals(1, result.fiscalYearStartDay());
            assertEquals(1, result.monthlyPeriodStartDay());
        }
    }

    @Nested
    @DisplayName("updateDefaultPatterns")
    class UpdateDefaultPatterns {

        @Test
        @DisplayName("should delegate to repository with correct values")
        void shouldDelegateToRepository() {
            UUID userId = UUID.randomUUID();

            service.updateDefaultPatterns(10, 1, 15, userId);

            verify(repository).updateDefaultFiscalYearPattern(new SystemDefaultFiscalYearPattern(10, 1), userId);
            verify(repository).updateDefaultMonthlyPeriodPattern(new SystemDefaultMonthlyPeriodPattern(15), userId);
        }

        @Test
        @DisplayName("should reject invalid fiscal year start month")
        void shouldRejectInvalidMonth() {
            UUID userId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultPatterns(0, 1, 1, userId));
            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultPatterns(13, 1, 1, userId));
        }

        @Test
        @DisplayName("should reject invalid monthly period start day")
        void shouldRejectInvalidMonthlyPeriodStartDay() {
            UUID userId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultPatterns(4, 1, 0, userId));
            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultPatterns(4, 1, 29, userId));
        }
    }
}
