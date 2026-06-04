package review.backend.api.dto;

import java.time.Instant;
import java.util.List;

public record CrawlJobResponse(
        String jobId,
        String productUrl,
        String productCode,
        Long taxonomyId,
        String status,
        Instant startedAt,
        int fetchedCount,
        int capturedPackets,
        String outputPath,
        String progressPath,
        String cleanCommand,
        String importCommand,
        List<CrawlReviewSample> sampleReviews,
        String errorMessage,
        String analysisHandoffStatus,
        String analysisHandoffNote
) {
}
