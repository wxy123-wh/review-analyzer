package review.backend.api;

import review.backend.api.dto.ProductTaxonomyBindingRequest;
import review.backend.api.dto.ProductTaxonomyBindingResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.api.dto.TaxonomyUpsertRequest;
import review.backend.application.TaxonomyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/taxonomies")
    public List<TaxonomyResponse> list() {
        return taxonomyService.listTaxonomies();
    }

    @GetMapping("/taxonomies/{id}")
    public ResponseEntity<?> detail(@PathVariable("id") long id) {
        try {
            return ResponseEntity.ok(taxonomyService.findById(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "taxonomy not found", "taxonomyId", id));
        }
    }

    @PostMapping("/taxonomies")
    public ResponseEntity<TaxonomyResponse> create(@Valid @RequestBody TaxonomyUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxonomyService.create(request));
    }

    @PutMapping("/taxonomies/{id}")
    public ResponseEntity<?> createNextVersion(
            @PathVariable("id") long id,
            @Valid @RequestBody TaxonomyUpsertRequest request
    ) {
        try {
            return ResponseEntity.ok(taxonomyService.createNextVersion(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "taxonomy not found", "taxonomyId", id));
        }
    }

    @GetMapping("/products/{productCode}/taxonomy")
    public ProductTaxonomyBindingResponse productBinding(@PathVariable("productCode") String productCode) {
        return taxonomyService.findProductBinding(productCode);
    }

    @PutMapping("/products/{productCode}/taxonomy")
    public ProductTaxonomyBindingResponse bindProduct(
            @PathVariable("productCode") String productCode,
            @Valid @RequestBody ProductTaxonomyBindingRequest request
    ) {
        return taxonomyService.bindProduct(productCode, request.taxonomyId());
    }
}
