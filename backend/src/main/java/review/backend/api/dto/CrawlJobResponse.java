package review.backend.api.dto;

import java.time.Instant;

public record CrawlJobResponse(
        String jobId,
        String productUrl,
        String productCode,
        Long taxonomyId,
        String status,
        Instant startedAt,
        int fetchedCount,
        String errorMessage,
        String analysisHandoffStatus,
        String analysisHandoffNote
) {
}
