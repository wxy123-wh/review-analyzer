package review.backend.api.dto;

import java.time.Instant;

public record AnalysisJobResponse(
        String jobId,
        String productCode,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {
}
