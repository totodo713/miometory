package com.worklog.application.service;

import com.worklog.application.command.CreateAbsenceCommand;
import com.worklog.application.command.DeleteAbsenceCommand;
import com.worklog.application.command.UpdateAbsenceCommand;
import com.worklog.domain.absence.Absence;
import com.worklog.domain.absence.AbsenceId;
import com.worklog.domain.absence.AbsenceType;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.infrastructure.repository.JdbcAbsenceRepository;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application service for Absence operations.
 * 
 * Coordinates absence-related use cases and enforces business rules,
 * including the 24-hour daily limit validation for combined work + absence hours.
 */
@Service
public class AbsenceService {

    private static final BigDecimal MAX_DAILY_HOURS = BigDecimal.valueOf(24);

    private final JdbcAbsenceRepository absenceRepository;
    private final JdbcWorkLogRepository workLogRepository;

    public AbsenceService(
        JdbcAbsenceRepository absenceRepository,
        JdbcWorkLogRepository workLogRepository
    ) {
        this.absenceRepository = absenceRepository;
        this.workLogRepository = workLogRepository;
    }

    /**
     * Records a new absence.
     * 
     * Validates that the total hours for the member on the specified date
     * (work hours + absence hours) does not exceed 24 hours.
     * 
     * @param command The creation command
     * @return ID of the newly recorded absence
     * @throws DomainException if 24-hour limit would be exceeded
     */
    @Transactional
    public UUID recordAbsence(CreateAbsenceCommand command) {
        // Validate hours format (0.25h increments, > 0 and <= 24h)
        TimeAmount hours = TimeAmount.of(command.hours());

        // Parse absence type
        AbsenceType absenceType;
        try {
            absenceType = AbsenceType.valueOf(command.absenceType());
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                "INVALID_ABSENCE_TYPE",
                "Invalid absence type: " + command.absenceType() + 
                ". Valid types: PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER"
            );
        }

        // Check 24-hour daily limit (work + absence)
        validateCombinedDailyLimit(
            command.memberId(),
            command.date(),
            hours,
            null // no absence to exclude for creates
        );

        // Create the aggregate
        Absence absence = Absence.record(
            MemberId.of(command.memberId()),
            command.date(),
            hours,
            absenceType,
            command.reason(),
            MemberId.of(command.recordedBy())
        );

        // Persist
        absenceRepository.save(absence);

        return absence.getId().value();
    }

    /**
     * Updates an existing absence.
     * 
     * Validates that the total hours for the member on the specified date
     * (work hours + absence hours) does not exceed 24 hours.
     * 
     * @param command The update command
     * @throws DomainException if absence not found, not editable, or 24-hour limit would be exceeded
     */
    @Transactional
    public void updateAbsence(UpdateAbsenceCommand command) {
        Absence absence = absenceRepository.findById(AbsenceId.of(command.id()))
            .orElseThrow(() -> new DomainException(
                "ABSENCE_NOT_FOUND",
                "Absence not found: " + command.id()
            ));

        // Check version for optimistic locking
        if (absence.getVersion() != command.version()) {
            throw new DomainException(
                "OPTIMISTIC_LOCK_FAILURE",
                "Absence has been modified by another user. Please refresh and try again."
            );
        }

        // Validate hours format
        TimeAmount hours = TimeAmount.of(command.hours());

        // Parse absence type
        AbsenceType absenceType;
        try {
            absenceType = AbsenceType.valueOf(command.absenceType());
        } catch (IllegalArgumentException e) {
            throw new DomainException(
                "INVALID_ABSENCE_TYPE",
                "Invalid absence type: " + command.absenceType() + 
                ". Valid types: PAID_LEAVE, SICK_LEAVE, SPECIAL_LEAVE, OTHER"
            );
        }

        // Check 24-hour daily limit (excluding this absence's current hours)
        validateCombinedDailyLimit(
            absence.getMemberId().value(),
            absence.getDate(),
            hours,
            command.id()
        );

        // Update the aggregate
        absence.update(hours, absenceType, command.reason(), MemberId.of(command.updatedBy()));

        // Persist
        absenceRepository.save(absence);
    }

    /**
     * Deletes an absence.
     * 
     * @param command The delete command
     * @throws DomainException if absence not found or not deletable
     */
    @Transactional
    public void deleteAbsence(DeleteAbsenceCommand command) {
        Absence absence = absenceRepository.findById(AbsenceId.of(command.id()))
            .orElseThrow(() -> new DomainException(
                "ABSENCE_NOT_FOUND",
                "Absence not found: " + command.id()
            ));

        // Delete the aggregate
        absence.delete(MemberId.of(command.deletedBy()));

        // Persist
        absenceRepository.save(absence);
    }

    /**
     * Validates that the combined daily total hours (work + absence) for a member
     * on a specific date does not exceed 24 hours.
     * 
     * This is the critical cross-aggregate validation rule for absence recording.
     * 
     * @param memberId Member ID
     * @param date Date to check
     * @param newAbsenceHours Absence hours being added/updated
     * @param excludeAbsenceId Absence ID to exclude from calculation (for updates)
     * @throws DomainException if 24-hour limit would be exceeded
     */
    private void validateCombinedDailyLimit(
        UUID memberId,
        java.time.LocalDate date,
        TimeAmount newAbsenceHours,
        UUID excludeAbsenceId
    ) {
        // Get existing work hours for this date
        BigDecimal existingWorkHours = workLogRepository.getTotalHoursForDate(
            memberId,
            date,
            null // don't exclude any work entries
        );

        // Get existing absence hours for this date (excluding current if updating)
        BigDecimal existingAbsenceHours = absenceRepository.getTotalHoursForDate(
            memberId,
            date,
            excludeAbsenceId
        );

        // Calculate new combined total
        BigDecimal combinedTotal = existingWorkHours
            .add(existingAbsenceHours)
            .add(newAbsenceHours.hours());

        if (combinedTotal.compareTo(MAX_DAILY_HOURS) > 0) {
            throw new DomainException(
                "DAILY_LIMIT_EXCEEDED",
                String.format(
                    "Combined work and absence hours cannot exceed 24 hours per day. " +
                    "Current work hours: %s, Current absence hours: %s, " +
                    "Attempting to add: %s absence hours, Would result in: %s total hours",
                    existingWorkHours,
                    existingAbsenceHours,
                    newAbsenceHours.hours(),
                    combinedTotal
                )
            );
        }
    }

    /**
     * Finds an absence by ID.
     * 
     * @param absenceId ID of the absence to find
     * @return The absence, or null if not found
     */
    public Absence findById(UUID absenceId) {
        return absenceRepository.findById(AbsenceId.of(absenceId)).orElse(null);
    }
}
