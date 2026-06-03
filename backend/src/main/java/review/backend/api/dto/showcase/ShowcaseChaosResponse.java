package review.backend.api.dto.showcase;

import java.util.List;

public record ShowcaseChaosResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseChaosDrill> drills
) {
}
