package review.backend.api.dto;

import java.time.LocalDate;
import java.util.List;

public record UxChangeComparisonResponse(
        String checkpointId,
        String productCode,
        String productName,
        LocalDate changeDate,
        String windowPreset,
        UxChangeComparisonWindow beforeWindow,
        UxChangeComparisonWindow afterWindow,
        String state,
        String notice,
        List<UxChangeComparisonItem> items
) {
}
