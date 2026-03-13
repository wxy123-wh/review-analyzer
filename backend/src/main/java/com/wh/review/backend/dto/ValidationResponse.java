package com.wh.review.backend.dto;

import java.util.List;

public record ValidationResponse(
        List<ValidationItem> items
) {
}
