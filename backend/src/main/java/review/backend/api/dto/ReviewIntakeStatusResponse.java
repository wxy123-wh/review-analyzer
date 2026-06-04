package review.backend.api.dto;

import java.util.List;
import java.util.Map;

public record ReviewIntakeStatusResponse(
        String productCode,
        String productName,
        String rawOutputPath,
        boolean rawJsonlExists,
        int rawCount,
        String cleanedOutputPath,
        boolean cleanedJsonlExists,
        String removedOutputPath,
        String cleaningSummaryPath,
        Map<String, Object> cleaningSummary,
        boolean taxonomyBound,
        Long taxonomyId,
        Integer taxonomyVersion,
        int importedReviewCount,
        int analyzedReviewCount,
        boolean downstreamReady,
        AnalysisJobResponse latestAnalysisJob,
        List<CrawlReviewSample> recentReviews,
        String stage,
        String notice
) {
}
