package review.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviewImportItem(
        String source,
        String sourceReviewId,
        String productCode,
        String category,
        BigDecimal rating,
        @NotBlank(message = "content must not be blank")
        String content,
        Instant reviewTime,
        String skuInfo,
        String anonymizedAuthorId
) {
}
