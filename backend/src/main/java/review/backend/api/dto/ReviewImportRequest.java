package review.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record ReviewImportRequest(
        String provider,
        String platform,
        @NotBlank(message = "productCode must not be blank")
        String productCode,
        String productName,
        @Valid
        @NotEmpty(message = "reviews must not be empty")
        List<ReviewImportItem> reviews,
        Map<String, Object> cleaningSummary
) {
}
