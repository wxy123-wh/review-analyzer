package review.backend.api.dto;

public record ValidationItem(
        String actionId,
        double beforeNegativeRate,
        double afterNegativeRate,
        double improvementRate,
        String summary
) {
}
