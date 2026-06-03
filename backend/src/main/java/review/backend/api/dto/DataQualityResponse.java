package review.backend.api.dto;

import java.time.Instant;

public record DataQualityResponse(
        String productCode,
        String state,
        String notice,
        int rawCount,
        int cleanedCount,
        int removedCount,
        int htmlCleanedCount,
        int exactDuplicateCount,
        int emptyContentCount,
        int invalidJsonCount,
        Instant importedAt
) {
}
