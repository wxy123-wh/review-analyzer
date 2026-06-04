package review.backend.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record UxChangeComparisonRecord(
        String checkpointId,
        String productCode,
        String productName,
        LocalDate changeDate,
        String windowPreset,
        UxChangeComparisonWindow beforeWindow,
        UxChangeComparisonWindow afterWindow,
        Instant createdAt
) {
}
