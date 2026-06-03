package review.backend.api.dto.showcase;

import java.util.List;

public record ShowcaseExplainabilityResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseFeatureContribution> featureContributions
) {
}
