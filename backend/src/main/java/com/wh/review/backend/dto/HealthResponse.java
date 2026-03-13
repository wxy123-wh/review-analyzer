package com.wh.review.backend.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        Instant timestamp
) {
}
