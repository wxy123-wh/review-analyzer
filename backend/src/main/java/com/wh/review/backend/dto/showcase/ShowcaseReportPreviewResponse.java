package com.wh.review.backend.dto.showcase;

import java.util.List;

public record ShowcaseReportPreviewResponse(
        String status,
        boolean implemented,
        String note,
        List<String> previewSections
) {
}
