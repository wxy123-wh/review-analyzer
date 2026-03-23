package com.wh.review.backend.dto.showcase;

public record ShowcaseAgentItem(
        String agentName,
        String role,
        String state,
        double confidence
) {
}
