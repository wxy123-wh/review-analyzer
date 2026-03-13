package com.wh.review.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SyncStartRequest(
        @NotBlank(message = "provider must not be blank")
        String provider,
        String platform,
        @NotBlank(message = "targetProductCode must not be blank")
        String targetProductCode
) {
}
