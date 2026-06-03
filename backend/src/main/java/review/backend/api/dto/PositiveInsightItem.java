package review.backend.api.dto;

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
