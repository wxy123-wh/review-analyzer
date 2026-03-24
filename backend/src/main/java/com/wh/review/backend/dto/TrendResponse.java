package com.wh.review.backend.dto;

import java.util.List;

public record TrendResponse(
        String productCode,
        String aspect,
        List<TrendPoint> points,
        String notice
) {
    public TrendResponse(String productCode, String aspect, List<TrendPoint> points) {
        this(productCode, aspect, points, null);
    }
}
