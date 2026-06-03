package review.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UxPrimaryLabelRequest(
        @NotBlank(message = "labelName must not be blank")
        String labelName,
        @Valid
        @NotEmpty(message = "secondaryLabels must not be empty")
        List<UxSecondaryLabelRequest> secondaryLabels
) {
}
