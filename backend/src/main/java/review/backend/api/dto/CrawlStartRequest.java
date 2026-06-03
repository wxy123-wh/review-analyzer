package review.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CrawlStartRequest(
        @NotBlank(message = "productUrl must not be blank")
        String productUrl,
        String productCode,
        Long taxonomyId,
        Integer maxPackets
) {
}
