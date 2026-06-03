package review.backend.api.dto;

import java.time.Instant;

public record ActionResponse(
        String actionId,
        String productCode,
        String issueId,
        String actionName,
        String actionDesc,
        String status,
        Instant createdAt
) {
}
