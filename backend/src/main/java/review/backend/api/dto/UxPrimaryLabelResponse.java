package review.backend.api.dto;

import java.util.List;

public record UxPrimaryLabelResponse(
        long id,
        String labelName,
        int sortOrder,
        List<UxSecondaryLabelResponse> secondaryLabels
) {
}
