package com.worklog.api;

import com.worklog.api.dto.DailyRejectionResponse;
import com.worklog.infrastructure.repository.JdbcDailyRejectionLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for rejection-related query operations.
 */
@RestController
@RequestMapping("/api/v1/worklog/rejections")
public class RejectionController {

    private final JdbcDailyRejectionLogRepository dailyRejectionLogRepository;

    public RejectionController(JdbcDailyRejectionLogRepository dailyRejectionLogRepository) {
        this.dailyRejectionLogRepository = dailyRejectionLogRepository;
    }

    /**
     * Get daily rejection log entries for a member within a date range.
     *
     * GET /api/v1/worklog/rejections/daily?memberId=...&startDate=...&endDate=...
     *
     * @return 200 OK with list of daily rejections
     */
    @GetMapping("/daily")
    public ResponseEntity<DailyRejectionResponse> getDailyRejections(
            @RequestParam UUID memberId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        List<JdbcDailyRejectionLogRepository.DailyRejectionRecord> records =
                dailyRejectionLogRepository.findByMemberIdAndDateRange(memberId, startDate, endDate);

        List<DailyRejectionResponse.DailyRejectionItem> items = records.stream()
                .map(r -> new DailyRejectionResponse.DailyRejectionItem(
                        r.workDate(),
                        r.rejectionReason(),
                        r.rejectedBy(),
                        null, // TODO: Fetch rejectedByName from member repository
                        r.createdAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new DailyRejectionResponse(items));
    }
}
