package review.backend.api.dto;

public record UxChangeComparisonItem(
        String aspect,
        String uxPrimaryLabel,
        String uxSecondaryLabel,
        int beforeMentionCount,
        int beforeNegativeCount,
        double beforeNegativeRate,
        int afterMentionCount,
        int afterNegativeCount,
        double afterNegativeRate,
        double improvementRate,
        double negativeRateChange,
        int mentionCountChange,
        String changeDirection,
        String sampleState,
        String summary
) {
}
