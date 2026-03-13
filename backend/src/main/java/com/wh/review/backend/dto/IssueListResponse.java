package com.wh.review.backend.dto;

import java.util.List;

public record IssueListResponse(
        List<IssueItem> items
) {
}
