package com.wh.review.backend.dto;

import java.util.List;

public record WordCloudResponse(
        String productCode,
        String aspect,
        List<WordCloudItem> items,
        String notice
) {
    public WordCloudResponse(String productCode, String aspect, List<WordCloudItem> items) {
        this(productCode, aspect, items, null);
    }
}
