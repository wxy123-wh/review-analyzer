package review.backend.api.dto;

import java.util.List;

public record UxSecondaryLabelResponse(
        long id,
        String labelName,
        List<String> synonyms,
        String description,
        int sortOrder,
        boolean enabled
) {
}
