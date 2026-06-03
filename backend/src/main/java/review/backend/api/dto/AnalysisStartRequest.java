package review.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisStartRequest(
        @NotBlank(message = "productCode must not be blank")
        String productCode,
        Long taxonomyId
) {
}
