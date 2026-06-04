package review.backend.api.dto;

import java.util.List;

public record CompareResponse(
        String productCode,
        String productName,
        String comparisonProductCode,
        String comparisonProductName,
        String state,
        String notice,
        List<CompareItem> items
) {
}
