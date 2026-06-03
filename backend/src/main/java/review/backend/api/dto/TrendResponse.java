package review.backend.api.dto;

import java.util.List;

public record TrendResponse(
        String productCode,
        String aspect,
        String uxSecondaryLabel,
        List<TrendPoint> points,
        String state,
        String notice
) {
    public TrendResponse(String productCode, String aspect, List<TrendPoint> points, String state) {
        this(productCode, aspect, aspect, points, state, null);
    }
}
