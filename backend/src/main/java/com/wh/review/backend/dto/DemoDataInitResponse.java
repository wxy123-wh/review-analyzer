package com.wh.review.backend.dto;

import java.time.Instant;

public record DemoDataInitResponse(
        String seedKey,
        String productCode,
        String dataVersion,
        int targetReviewCount,
        int insertedReviewCount,
        int updatedReviewCount,
        int totalReviewCount,
        long durationMs,
        Instant initializedAt
) {
}
