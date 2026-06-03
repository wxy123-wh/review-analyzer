package review.backend.api.dto;

public record TrendPoint(
        String period,
        double negativeRate,
        int mentionVolume
) {
}
