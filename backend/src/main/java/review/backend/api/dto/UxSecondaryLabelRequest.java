package review.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UxSecondaryLabelRequest(
        @NotBlank(message = "labelName must not be blank")
        String labelName,
        List<String> synonyms,
        String description,
        Boolean enabled
) {
}
