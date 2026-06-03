package review.backend.api.dto;

import java.util.List;

public record TaxonomyResponse(
        long taxonomyId,
        String name,
        String productCategory,
        int version,
        boolean active,
        List<UxPrimaryLabelResponse> primaryLabels
) {
}
