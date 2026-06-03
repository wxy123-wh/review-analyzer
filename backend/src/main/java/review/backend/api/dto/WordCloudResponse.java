package review.backend.api.dto;

import java.util.List;

public record WordCloudResponse(
        String productCode,
        String aspect,
        String uxSecondaryLabel,
        List<WordCloudItem> items,
        String state,
        String notice
) {
    public WordCloudResponse(String productCode, String aspect, List<WordCloudItem> items, String state) {
        this(productCode, aspect, aspect, items, state, null);
    }
}
