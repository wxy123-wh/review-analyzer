package com.wh.review.backend.dto.showcase;

import java.util.List;

public record ShowcaseAgentArenaResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseAgentItem> agents
) {
}
