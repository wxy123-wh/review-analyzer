package review.backend.api;

import review.backend.api.dto.ReviewImportRequest;
import review.backend.api.dto.ReviewImportResponse;
import review.backend.application.ReviewImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewImportController {

    private final ReviewImportService reviewImportService;

    public ReviewImportController(ReviewImportService reviewImportService) {
        this.reviewImportService = reviewImportService;
    }

    @PostMapping("/import")
    public ResponseEntity<ReviewImportResponse> importReviews(@Valid @RequestBody ReviewImportRequest request) {
        ReviewImportResponse response = reviewImportService.importReviews(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
