package review.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TaxonomyUpsertRequest(
        @NotBlank(message = "name must not be blank")
        String name,
        @NotBlank(message = "productCategory must not be blank")
        String productCategory,
        @Valid
        @NotEmpty(message = "primaryLabels must not be empty")
        List<UxPrimaryLabelRequest> primaryLabels
) {
}
