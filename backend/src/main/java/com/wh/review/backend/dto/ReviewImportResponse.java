package com.wh.review.backend.dto;

import java.time.Instant;

public record ReviewImportResponse(
        String importJobId,
        String provider,
        String platform,
        String productCode,
        int receivedCount,
        int insertedReviewCount,
        int updatedReviewCount,
        int totalReviewCount,
        String analysisHandoffStatus,
        String analysisHandoffNote,
        Instant importedAt
) {
}
