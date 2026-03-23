package com.wh.review.backend.dto;

public record WordCloudItem(
        String keyword,
        int frequency,
        double weight,
        String sentimentTag
) {
}
