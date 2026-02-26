package com.worklog.api;

import com.worklog.application.service.AdminMasterDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for system-wide master data preset management.
 * Accessible only to SYSTEM_ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/master-data")
public class AdminMasterDataController {

    private final AdminMasterDataService adminMasterDataService;

    public AdminMasterDataController(AdminMasterDataService adminMasterDataService) {
        this.adminMasterDataService = adminMasterDataService;
    }

    // ========================================================================
    // Fiscal Year Pattern Presets
    // ========================================================================

    @GetMapping("/fiscal-year-patterns")
    @PreAuthorize("hasPermission(null, 'master_data.view')")
    public AdminMasterDataService.PresetPage<AdminMasterDataService.FiscalYearPresetRow> listFiscalYearPresets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.min(size, 100);
        return adminMasterDataService.listFiscalYearPresets(search, isActive, page, effectiveSize);
    }

    @PostMapping("/fiscal-year-patterns")
    @PreAuthorize("hasPermission(null, 'master_data.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse createFiscalYearPreset(@RequestBody @Valid CreateFiscalYearPresetRequest request) {
        UUID id = adminMasterDataService.createFiscalYearPreset(
                request.name(), request.description(), request.startMonth(), request.startDay());
        return new CreateResponse(id.toString());
    }

    @PutMapping("/fiscal-year-patterns/{id}")
    @PreAuthorize("hasPermission(null, 'master_data.update')")
    public ResponseEntity<Void> updateFiscalYearPreset(
            @PathVariable UUID id, @RequestBody @Valid CreateFiscalYearPresetRequest request) {
        adminMasterDataService.updateFiscalYearPreset(
                id, request.name(), request.description(), request.startMonth(), request.startDay());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/fiscal-year-patterns/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> deactivateFiscalYearPreset(@PathVariable UUID id) {
        adminMasterDataService.deactivateFiscalYearPreset(id);
        return ResponseEntity.ok().build();
    }

    // Intentionally reuses *.deactivate permission for both activate/deactivate
    @PatchMapping("/fiscal-year-patterns/{id}/activate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> activateFiscalYearPreset(@PathVariable UUID id) {
        adminMasterDataService.activateFiscalYearPreset(id);
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // Monthly Period Pattern Presets
    // ========================================================================

    @GetMapping("/monthly-period-patterns")
    @PreAuthorize("hasPermission(null, 'master_data.view')")
    public AdminMasterDataService.PresetPage<AdminMasterDataService.MonthlyPeriodPresetRow> listMonthlyPeriodPresets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.min(size, 100);
        return adminMasterDataService.listMonthlyPeriodPresets(search, isActive, page, effectiveSize);
    }

    @PostMapping("/monthly-period-patterns")
    @PreAuthorize("hasPermission(null, 'master_data.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse createMonthlyPeriodPreset(@RequestBody @Valid CreateMonthlyPeriodPresetRequest request) {
        UUID id = adminMasterDataService.createMonthlyPeriodPreset(
                request.name(), request.description(), request.startDay());
        return new CreateResponse(id.toString());
    }

    @PutMapping("/monthly-period-patterns/{id}")
    @PreAuthorize("hasPermission(null, 'master_data.update')")
    public ResponseEntity<Void> updateMonthlyPeriodPreset(
            @PathVariable UUID id, @RequestBody @Valid CreateMonthlyPeriodPresetRequest request) {
        adminMasterDataService.updateMonthlyPeriodPreset(id, request.name(), request.description(), request.startDay());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/monthly-period-patterns/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> deactivateMonthlyPeriodPreset(@PathVariable UUID id) {
        adminMasterDataService.deactivateMonthlyPeriodPreset(id);
        return ResponseEntity.ok().build();
    }

    // Intentionally reuses *.deactivate permission for both activate/deactivate
    @PatchMapping("/monthly-period-patterns/{id}/activate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> activateMonthlyPeriodPreset(@PathVariable UUID id) {
        adminMasterDataService.activateMonthlyPeriodPreset(id);
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // Holiday Calendar Presets
    // ========================================================================

    @GetMapping("/holiday-calendars")
    @PreAuthorize("hasPermission(null, 'master_data.view')")
    public AdminMasterDataService.PresetPage<AdminMasterDataService.HolidayCalendarPresetRow>
            listHolidayCalendarPresets(
                    @RequestParam(required = false) String search,
                    @RequestParam(required = false) Boolean isActive,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.min(size, 100);
        return adminMasterDataService.listHolidayCalendarPresets(search, isActive, page, effectiveSize);
    }

    @PostMapping("/holiday-calendars")
    @PreAuthorize("hasPermission(null, 'master_data.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse createHolidayCalendar(@RequestBody @Valid CreateHolidayCalendarRequest request) {
        UUID id = adminMasterDataService.createHolidayCalendarPreset(
                request.name(), request.description(), request.country());
        return new CreateResponse(id.toString());
    }

    @PutMapping("/holiday-calendars/{id}")
    @PreAuthorize("hasPermission(null, 'master_data.update')")
    public ResponseEntity<Void> updateHolidayCalendar(
            @PathVariable UUID id, @RequestBody @Valid CreateHolidayCalendarRequest request) {
        adminMasterDataService.updateHolidayCalendarPreset(
                id, request.name(), request.description(), request.country());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/holiday-calendars/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> deactivateHolidayCalendar(@PathVariable UUID id) {
        adminMasterDataService.deactivateHolidayCalendarPreset(id);
        return ResponseEntity.ok().build();
    }

    // Intentionally reuses *.deactivate permission for both activate/deactivate
    @PatchMapping("/holiday-calendars/{id}/activate")
    @PreAuthorize("hasPermission(null, 'master_data.deactivate')")
    public ResponseEntity<Void> activateHolidayCalendar(@PathVariable UUID id) {
        adminMasterDataService.activateHolidayCalendarPreset(id);
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // Holiday Calendar Entries
    // ========================================================================

    @GetMapping("/holiday-calendars/{id}/entries")
    @PreAuthorize("hasPermission(null, 'master_data.view')")
    public List<AdminMasterDataService.HolidayEntryRow> listHolidayEntries(@PathVariable UUID id) {
        return adminMasterDataService.listHolidayEntries(id);
    }

    @PostMapping("/holiday-calendars/{id}/entries")
    @PreAuthorize("hasPermission(null, 'master_data.create')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse addHolidayEntry(
            @PathVariable UUID id, @RequestBody @Valid CreateHolidayEntryRequest request) {
        UUID entryId = adminMasterDataService.addHolidayEntry(
                id,
                request.name(),
                request.entryType(),
                request.month(),
                request.day(),
                request.nthOccurrence(),
                request.dayOfWeek(),
                request.specificYear());
        return new CreateResponse(entryId.toString());
    }

    @PutMapping("/holiday-calendars/{id}/entries/{entryId}")
    @PreAuthorize("hasPermission(null, 'master_data.update')")
    public ResponseEntity<Void> updateHolidayEntry(
            @PathVariable UUID id, @PathVariable UUID entryId, @RequestBody @Valid CreateHolidayEntryRequest request) {
        adminMasterDataService.updateHolidayEntry(
                id,
                entryId,
                request.name(),
                request.entryType(),
                request.month(),
                request.day(),
                request.nthOccurrence(),
                request.dayOfWeek(),
                request.specificYear());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/holiday-calendars/{id}/entries/{entryId}")
    @PreAuthorize("hasPermission(null, 'master_data.update')")
    public ResponseEntity<Void> removeHolidayEntry(@PathVariable UUID id, @PathVariable UUID entryId) {
        adminMasterDataService.removeHolidayEntry(id, entryId);
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // Request/Response DTOs
    // ========================================================================

    public record CreateFiscalYearPresetRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @Min(1) @Max(12) int startMonth,
            @Min(1) @Max(31) int startDay) {}

    public record CreateMonthlyPeriodPresetRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @Min(1) @Max(28) int startDay) {}

    public record CreateHolidayCalendarRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @Size(max = 2) String country) {}

    public record CreateHolidayEntryRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String entryType,
            @Min(1) @Max(12) int month,
            Integer day,
            Integer nthOccurrence,
            Integer dayOfWeek,
            Integer specificYear) {}

    public record CreateResponse(String id) {}
}
