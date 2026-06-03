package review.backend.api.dto;

import java.util.List;

public record PositiveInsightResponse(
        String state,
        String notice,
        List<PositiveInsightItem> items
) {
    public PositiveInsightResponse(String state, List<PositiveInsightItem> items) {
        this(state, null, items);
    }
}
