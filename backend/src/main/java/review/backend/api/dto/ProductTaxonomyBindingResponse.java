package review.backend.api.dto;

public record ProductTaxonomyBindingResponse(
        String productCode,
        TaxonomyResponse taxonomy
) {
}
