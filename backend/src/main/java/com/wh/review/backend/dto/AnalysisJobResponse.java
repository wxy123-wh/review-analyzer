package com.wh.review.backend.dto;

import java.time.Instant;

public record AnalysisJobResponse(
        String jobId,
        String productCode,
        String status,
        Instant startedAt
) {
}
