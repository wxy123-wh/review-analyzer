package com.wh.review.backend.dto.showcase;

import java.util.List;

public record ShowcaseChaosResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseChaosDrill> drills
) {
}
