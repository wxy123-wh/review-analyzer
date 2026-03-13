package com.wh.review.backend.dto;

public record IssueItem(
        String issueId,
        String title,
        String aspect,
        double priorityScore,
        String evidenceSummary
) {
}
