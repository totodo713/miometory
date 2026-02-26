package com.worklog.application.service;

import com.worklog.domain.settings.SystemDefaultFiscalYearPattern;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodPattern;
import com.worklog.infrastructure.repository.SystemDefaultSettingsRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for managing system-wide default settings.
 */
@Service
public class SystemSettingsService {

    private final SystemDefaultSettingsRepository repository;

    public SystemSettingsService(SystemDefaultSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SystemDefaultPatterns getDefaultPatterns() {
        SystemDefaultFiscalYearPattern fy = repository.getDefaultFiscalYearPattern();
        SystemDefaultMonthlyPeriodPattern mp = repository.getDefaultMonthlyPeriodPattern();
        return new SystemDefaultPatterns(fy.startMonth(), fy.startDay(), mp.startDay());
    }

    @Transactional
    public void updateDefaultPatterns(int fyStartMonth, int fyStartDay, int mpStartDay, UUID updatedBy) {
        SystemDefaultFiscalYearPattern fyPattern = new SystemDefaultFiscalYearPattern(fyStartMonth, fyStartDay);
        SystemDefaultMonthlyPeriodPattern mpPattern = new SystemDefaultMonthlyPeriodPattern(mpStartDay);

        repository.updateDefaultFiscalYearPattern(fyPattern, updatedBy);
        repository.updateDefaultMonthlyPeriodPattern(mpPattern, updatedBy);
    }

    public record SystemDefaultPatterns(int fiscalYearStartMonth, int fiscalYearStartDay, int monthlyPeriodStartDay) {}
}
