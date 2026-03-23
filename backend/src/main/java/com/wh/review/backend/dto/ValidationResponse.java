package com.wh.review.backend.dto;

import java.util.List;

public record ValidationResponse(
        List<ValidationItem> items,
        String notice
) {
    public ValidationResponse(List<ValidationItem> items) {
        this(items, null);
    }
}
