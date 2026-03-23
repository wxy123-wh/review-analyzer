package com.wh.review.backend.dto.showcase;

import jakarta.validation.constraints.NotBlank;

public record ShowcaseReportPreviewRequest(
        @NotBlank(message = "module must not be blank")
        String module
) {
}
