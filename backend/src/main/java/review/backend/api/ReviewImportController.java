package review.backend.api;

import review.backend.api.dto.ImportedProductHistoryItem;
import review.backend.api.dto.ReviewImportRequest;
import review.backend.api.dto.ReviewImportResponse;
import review.backend.api.dto.ReviewIntakeStatusResponse;
import review.backend.api.dto.ReviewJsonlFileCandidate;
import review.backend.api.dto.ReviewJsonlFileImportRequest;
import review.backend.application.ReviewIntakeStatusService;
import review.backend.application.ReviewImportService;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewImportController {

    private final ReviewImportService reviewImportService;
    private final ReviewIntakeStatusService reviewIntakeStatusService;

    public ReviewImportController(
            ReviewImportService reviewImportService,
            ReviewIntakeStatusService reviewIntakeStatusService
    ) {
        this.reviewImportService = reviewImportService;
        this.reviewIntakeStatusService = reviewIntakeStatusService;
    }

    @PostMapping("/import")
    public ResponseEntity<ReviewImportResponse> importReviews(@Valid @RequestBody ReviewImportRequest request) {
        ReviewImportResponse response = reviewImportService.importReviews(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jsonl-files")
    public ResponseEntity<List<ReviewJsonlFileCandidate>> listJsonlFiles() {
        return ResponseEntity.ok(reviewImportService.discoverJsonlFiles());
    }

    @GetMapping("/imported-products")
    public ResponseEntity<List<ImportedProductHistoryItem>> listImportedProducts(
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(reviewIntakeStatusService.importedProducts(limit));
    }

    @GetMapping("/intake-status")
    public ResponseEntity<ReviewIntakeStatusResponse> intakeStatus(
            @RequestParam("productCode") String productCode,
            @RequestParam(value = "inputPath", required = false) String inputPath
    ) {
        return ResponseEntity.ok(reviewIntakeStatusService.status(productCode, inputPath));
    }

    @PostMapping("/import-jsonl")
    public ResponseEntity<?> importJsonlFile(@Valid @RequestBody ReviewJsonlFileImportRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(reviewImportService.importJsonlFile(
                    "manual-jsonl",
                    request.productCode().trim(),
                    request.productName(),
                    request.platform(),
                    Path.of(request.inputPath().trim()),
                    Boolean.TRUE.equals(request.replaceExisting())
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", ex.getMessage(),
                    "productCode", request.productCode(),
                    "inputPath", request.inputPath()
            ));
        }
    }

    @PostMapping("/clean-jsonl")
    public ResponseEntity<?> cleanJsonlFile(@Valid @RequestBody ReviewJsonlFileImportRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(reviewImportService.cleanJsonlFile(
                    "manual-jsonl-clean",
                    request.productCode().trim(),
                    request.productName(),
                    request.platform(),
                    Path.of(request.inputPath().trim())
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", ex.getMessage(),
                    "productCode", request.productCode(),
                    "inputPath", request.inputPath()
            ));
        }
    }
}
