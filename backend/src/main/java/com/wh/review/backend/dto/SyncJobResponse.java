package com.wh.review.backend.dto;

import java.time.Instant;

public record SyncJobResponse(
        String jobId,
        String provider,
        String platform,
        String targetProductCode,
        String status,
        Instant startedAt,
        int fetchedCount,
        String errorMessage
) {
}
