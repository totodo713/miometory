package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.domain.settings.SystemDefaultFiscalYearRule;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodRule;
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
    @DisplayName("getDefaultRules")
    class GetDefaultRules {

        @Test
        @DisplayName("should return combined fiscal year and monthly period defaults")
        void shouldReturnCombinedDefaults() {
            when(repository.getDefaultFiscalYearRule()).thenReturn(new SystemDefaultFiscalYearRule(4, 1));
            when(repository.getDefaultMonthlyPeriodRule()).thenReturn(new SystemDefaultMonthlyPeriodRule(1));

            SystemSettingsService.SystemDefaultRules result = service.getDefaultRules();

            assertEquals(4, result.fiscalYearStartMonth());
            assertEquals(1, result.fiscalYearStartDay());
            assertEquals(1, result.monthlyPeriodStartDay());
        }
    }

    @Nested
    @DisplayName("updateDefaultRules")
    class UpdateDefaultRules {

        @Test
        @DisplayName("should delegate to repository with correct values")
        void shouldDelegateToRepository() {
            UUID userId = UUID.randomUUID();

            service.updateDefaultRules(10, 1, 15, userId);

            verify(repository).updateDefaultFiscalYearRule(new SystemDefaultFiscalYearRule(10, 1), userId);
            verify(repository).updateDefaultMonthlyPeriodRule(new SystemDefaultMonthlyPeriodRule(15), userId);
        }

        @Test
        @DisplayName("should reject invalid fiscal year start month")
        void shouldRejectInvalidMonth() {
            UUID userId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultRules(0, 1, 1, userId));
            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultRules(13, 1, 1, userId));
        }

        @Test
        @DisplayName("should reject invalid monthly period start day")
        void shouldRejectInvalidMonthlyPeriodStartDay() {
            UUID userId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultRules(4, 1, 0, userId));
            assertThrows(IllegalArgumentException.class, () -> service.updateDefaultRules(4, 1, 29, userId));
        }
    }
}
