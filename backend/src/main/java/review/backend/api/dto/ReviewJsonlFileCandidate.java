package review.backend.api.dto;

import java.time.Instant;
import java.util.List;

public record ReviewJsonlFileCandidate(
        String path,
        String fileName,
        String productCode,
        String productName,
        long sizeBytes,
        Instant lastModifiedAt,
        List<CrawlReviewSample> sampleReviews
) {
}
