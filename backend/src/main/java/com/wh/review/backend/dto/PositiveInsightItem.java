package com.wh.review.backend.dto;

import java.util.List;

public record PositiveInsightItem(
        String sellingPointId,
        String aspect,
        String uxPrimaryLabel,
        String uxSecondaryLabel,
        String sellingPoint,
        int mentionCount,
        double positiveRate,
        double score,
        List<String> evidence
) {
}
