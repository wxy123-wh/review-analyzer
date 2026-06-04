package review.backend.api.dto;

public record CompareItem(
        String aspect,
        String uxPrimaryLabel,
        String uxSecondaryLabel,
        double ourScore,
        double competitorScore,
        double gap,
        int ourMentionCount,
        int competitorMentionCount,
        double ourNegativeRate,
        double competitorNegativeRate,
        double negativeRateGap
) {
}
