package com.worklog.application.service;

import com.worklog.application.command.CreateWorkLogEntryCommand;
import com.worklog.application.command.DeleteWorkLogEntryCommand;
import com.worklog.application.command.UpdateWorkLogEntryCommand;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.project.ProjectId;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.shared.TimeAmount;
import com.worklog.domain.worklog.WorkLogEntry;
import com.worklog.domain.worklog.WorkLogEntryId;
import com.worklog.infrastructure.repository.JdbcWorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application service for WorkLogEntry operations.
 * 
 * Coordinates work log entry-related use cases and enforces business rules,
 * including the 24-hour daily limit validation across all projects.
 */
@Service
public class WorkLogEntryService {

    private static final BigDecimal MAX_DAILY_HOURS = BigDecimal.valueOf(24);

    private final JdbcWorkLogRepository workLogRepository;

    public WorkLogEntryService(JdbcWorkLogRepository workLogRepository) {
        this.workLogRepository = workLogRepository;
    }

    /**
     * Creates a new work log entry.
     * 
     * Validates that the total hours for the member on the specified date
     * (including this new entry) does not exceed 24 hours.
     * 
     * @param command The creation command
     * @return ID of the newly created work log entry
     * @throws DomainException if 24-hour limit would be exceeded
     */
    @Transactional
    public UUID createEntry(CreateWorkLogEntryCommand command) {
        // Validate hours format (0.25h increments, max 24h)
        TimeAmount hours = TimeAmount.of(command.hours());

        // Check 24-hour daily limit
        validateDailyLimit(
            command.memberId(),
            command.date(),
            hours,
            null // no entry to exclude for creates
        );

        // Create the aggregate
        WorkLogEntry entry = WorkLogEntry.create(
            MemberId.of(command.memberId()),
            ProjectId.of(command.projectId()),
            command.date(),
            hours,
            command.comment(),
            MemberId.of(command.enteredBy())
        );

        // Persist
        workLogRepository.save(entry);

        return entry.getId().value();
    }

    /**
     * Updates an existing work log entry.
     * 
     * Validates that the total hours for the member on the specified date
     * (including this updated entry) does not exceed 24 hours.
     * 
     * @param command The update command
     * @throws DomainException if entry not found, not editable, or 24-hour limit would be exceeded
     */
    @Transactional
    public void updateEntry(UpdateWorkLogEntryCommand command) {
        WorkLogEntry entry = workLogRepository.findById(WorkLogEntryId.of(command.id()))
            .orElseThrow(() -> new DomainException(
                "ENTRY_NOT_FOUND",
                "Work log entry not found: " + command.id()
            ));

        // Check version for optimistic locking
        if (entry.getVersion() != command.version()) {
            throw new DomainException(
                "OPTIMISTIC_LOCK_FAILURE",
                "Entry has been modified by another user. Please refresh and try again."
            );
        }

        // Validate hours format
        TimeAmount hours = TimeAmount.of(command.hours());

        // Check 24-hour daily limit (excluding this entry's current hours)
        validateDailyLimit(
            entry.getMemberId().value(),
            entry.getDate(),
            hours,
            command.id()
        );

        // Update the aggregate
        entry.update(hours, command.comment(), MemberId.of(command.updatedBy()));

        // Persist
        workLogRepository.save(entry);
    }

    /**
     * Deletes a work log entry.
     * 
     * @param command The delete command
     * @throws DomainException if entry not found or not deletable
     */
    @Transactional
    public void deleteEntry(DeleteWorkLogEntryCommand command) {
        WorkLogEntry entry = workLogRepository.findById(WorkLogEntryId.of(command.id()))
            .orElseThrow(() -> new DomainException(
                "ENTRY_NOT_FOUND",
                "Work log entry not found: " + command.id()
            ));

        // Delete the aggregate
        entry.delete(MemberId.of(command.deletedBy()));

        // Persist
        workLogRepository.save(entry);
    }

    /**
     * Validates that the daily total hours for a member on a specific date
     * does not exceed 24 hours.
     * 
     * @param memberId Member ID
     * @param date Date to check
     * @param newHours Hours being added/updated
     * @param excludeEntryId Entry ID to exclude from calculation (for updates)
     * @throws DomainException if 24-hour limit would be exceeded
     */
    private void validateDailyLimit(
        UUID memberId,
        java.time.LocalDate date,
        TimeAmount newHours,
        UUID excludeEntryId
    ) {
        BigDecimal existingTotal = workLogRepository.getTotalHoursForDate(
            memberId,
            date,
            excludeEntryId
        );

        BigDecimal newTotal = existingTotal.add(newHours.hours());

        if (newTotal.compareTo(MAX_DAILY_HOURS) > 0) {
            throw new DomainException(
                "DAILY_LIMIT_EXCEEDED",
                String.format(
                    "Daily limit of 24 hours exceeded. Current total: %s hours, Attempting to add: %s hours, Would result in: %s hours",
                    existingTotal,
                    newHours.hours(),
                    newTotal
                )
            );
        }
    }

    /**
     * Finds a work log entry by ID.
     * 
     * @param entryId ID of the entry to find
     * @return The work log entry, or null if not found
     */
    public WorkLogEntry findById(UUID entryId) {
        return workLogRepository.findById(WorkLogEntryId.of(entryId)).orElse(null);
    }
}
