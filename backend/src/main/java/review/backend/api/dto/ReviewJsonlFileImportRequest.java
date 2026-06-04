package review.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewJsonlFileImportRequest(
        @NotBlank(message = "productCode must not be blank")
        String productCode,
        String productName,
        @NotBlank(message = "inputPath must not be blank")
        String inputPath,
        String platform,
        Boolean replaceExisting
) {
}
