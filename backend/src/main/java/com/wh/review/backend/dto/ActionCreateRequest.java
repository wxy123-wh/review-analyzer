package com.wh.review.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ActionCreateRequest(
        @NotBlank(message = "productCode must not be blank")
        String productCode,
        @NotBlank(message = "issueId must not be blank")
        String issueId,
        @NotBlank(message = "actionName must not be blank")
        String actionName,
        String actionDesc
) {
}
