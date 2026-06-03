package review.backend.api.dto.showcase;

import java.util.List;

public record ShowcaseReportPreviewResponse(
        String status,
        boolean implemented,
        String note,
        List<String> previewSections
) {
}
