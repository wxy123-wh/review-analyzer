package review.backend.api.dto;

import java.util.List;

public record UxChangeComparisonHistoryResponse(
        String productCode,
        String productName,
        String state,
        String notice,
        List<UxChangeComparisonRecord> items
) {
}
