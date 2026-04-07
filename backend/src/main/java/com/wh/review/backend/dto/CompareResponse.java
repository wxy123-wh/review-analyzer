package com.wh.review.backend.dto;

import java.util.List;

public record CompareResponse(
        String productCode,
        String comparisonProductCode,
        String state,
        String notice,
        List<CompareItem> items
) {
}
