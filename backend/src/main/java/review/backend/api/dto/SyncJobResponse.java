package review.backend.api.dto;

import java.time.Instant;

public record SyncJobResponse(
        String jobId,
        String provider,
        String platform,
        String targetProductCode,
        String status,
        Instant startedAt,
        int fetchedCount,
        String errorMessage,
        String analysisHandoffStatus,
        String analysisHandoffNote
) {
}
