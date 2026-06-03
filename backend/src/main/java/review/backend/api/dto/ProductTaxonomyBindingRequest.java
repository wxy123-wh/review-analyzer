package review.backend.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProductTaxonomyBindingRequest(
        @NotNull(message = "taxonomyId must not be null")
        Long taxonomyId
) {
}
