package com.wh.review.backend.dto;

import java.util.List;

public record IssueListResponse(
        List<IssueItem> items,
        String state,
        String notice
) {
    public IssueListResponse(List<IssueItem> items, String state) {
        this(items, state, null);
    }
}
