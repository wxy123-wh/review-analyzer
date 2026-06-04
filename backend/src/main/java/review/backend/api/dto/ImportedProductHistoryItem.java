package review.backend.api.dto;

import java.time.Instant;

public record ImportedProductHistoryItem(
        String productCode,
        String productName,
        int importedReviewCount,
        int analyzedReviewCount,
        boolean downstreamReady,
        boolean taxonomyBound,
        String latestAnalysisStatus,
        Instant latestImportedAt,
        Instant createdAt
) {
}
