package com.wh.review.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisStartRequest(
        @NotBlank(message = "productCode must not be blank")
        String productCode
) {
}
