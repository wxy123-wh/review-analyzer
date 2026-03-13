package com.wh.review.backend.dto;

public record CompareItem(
        String aspect,
        double ourScore,
        double competitorScore,
        double gap
) {
}
