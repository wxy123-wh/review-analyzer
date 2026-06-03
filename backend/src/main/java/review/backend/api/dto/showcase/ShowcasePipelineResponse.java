package review.backend.api.dto.showcase;

import java.util.List;

public record ShowcasePipelineResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseStage> stages
) {
}
