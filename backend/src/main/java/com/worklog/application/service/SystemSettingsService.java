package com.worklog.application.service;

import com.worklog.domain.settings.SystemDefaultFiscalYearRule;
import com.worklog.domain.settings.SystemDefaultMonthlyPeriodRule;
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
    public SystemDefaultRules getDefaultRules() {
        SystemDefaultFiscalYearRule fy = repository.getDefaultFiscalYearRule();
        SystemDefaultMonthlyPeriodRule mp = repository.getDefaultMonthlyPeriodRule();
        return new SystemDefaultRules(fy.startMonth(), fy.startDay(), mp.startDay());
    }

    @Transactional
    public void updateDefaultRules(int fyStartMonth, int fyStartDay, int mpStartDay, UUID updatedBy) {
        SystemDefaultFiscalYearRule fyRule = new SystemDefaultFiscalYearRule(fyStartMonth, fyStartDay);
        SystemDefaultMonthlyPeriodRule mpRule = new SystemDefaultMonthlyPeriodRule(mpStartDay);

        repository.updateDefaultFiscalYearRule(fyRule, updatedBy);
        repository.updateDefaultMonthlyPeriodRule(mpRule, updatedBy);
    }

    @Transactional(readOnly = true)
    public SystemDefaultAttendanceTimes getDefaultAttendanceTimes() {
        java.time.LocalTime[] times = repository.getDefaultAttendanceTimes();
        return new SystemDefaultAttendanceTimes(times[0].toString(), times[1].toString());
    }

    @Transactional
    public void updateDefaultAttendanceTimes(
            java.time.LocalTime startTime, java.time.LocalTime endTime, UUID updatedBy) {
        repository.updateDefaultAttendanceTimes(startTime, endTime, updatedBy);
    }

    public record SystemDefaultRules(int fiscalYearStartMonth, int fiscalYearStartDay, int monthlyPeriodStartDay) {}

    public record SystemDefaultAttendanceTimes(String startTime, String endTime) {}
}
