package review.backend.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CrawlImportResponse(
        String jobId,
        String importJobId,
        String productCode,
        String productName,
        String provider,
        String platform,
        String rawOutputPath,
        String cleanedOutputPath,
        String removedOutputPath,
        String cleaningSummaryPath,
        int receivedCount,
        int insertedReviewCount,
        int updatedReviewCount,
        int totalReviewCount,
        Map<String, Object> cleaningSummary,
        List<CrawlReviewSample> sampleReviews,
        String analysisHandoffStatus,
        String analysisHandoffNote,
        Instant importedAt
) {
}
