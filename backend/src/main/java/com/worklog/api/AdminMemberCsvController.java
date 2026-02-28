package com.worklog.api;

import com.worklog.application.service.AdminMemberCsvService;
import com.worklog.application.service.UserContextService;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/members/csv")
public class AdminMemberCsvController {

    private final AdminMemberCsvService csvService;
    private final UserContextService userContextService;

    public AdminMemberCsvController(AdminMemberCsvService csvService, UserContextService userContextService) {
        this.csvService = csvService;
        this.userContextService = userContextService;
    }

    @GetMapping("/template")
    @PreAuthorize("hasPermission(null, 'member.view')")
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = new ClassPathResource("csv-templates/member-import-template.csv");

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"member-import-template.csv\"")
                .body(resource);
    }

    @PostMapping("/dry-run")
    @PreAuthorize("hasPermission(null, 'member.create')")
    public ResponseEntity<AdminMemberCsvService.DryRunResult> dryRun(
            @RequestParam("file") MultipartFile file,
            @RequestParam("organizationId") UUID organizationId,
            Authentication authentication) {
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        UUID memberId = userContextService.resolveUserMemberId(authentication.getName());
        var result = csvService.dryRun(file, organizationId, tenantId, memberId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/import/{sessionId}")
    @PreAuthorize("hasPermission(null, 'member.create')")
    public ResponseEntity<byte[]> executeImport(@PathVariable String sessionId, Authentication authentication) {
        // Validate UUID format to reject arbitrary strings early
        try {
            UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
        byte[] resultCsv = csvService.executeImport(sessionId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-result.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resultCsv);
    }
}
