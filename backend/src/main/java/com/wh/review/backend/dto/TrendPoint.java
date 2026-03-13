package com.wh.review.backend.dto;

public record TrendPoint(
        String period,
        double negativeRate,
        int mentionVolume
) {
}
