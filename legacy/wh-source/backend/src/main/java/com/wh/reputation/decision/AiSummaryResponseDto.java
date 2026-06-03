package com.wh.reputation.decision;

import java.util.List;

public record AiSummaryResponseDto(
        String summary,
        List<String> actionItems) {
}
