package com.wh.reputation.review;

import com.wh.reputation.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewImportController {
    private final ReviewImportService reviewImportService;

    public ReviewImportController(ReviewImportService reviewImportService) {
        this.reviewImportService = reviewImportService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewImportResult> importReviews(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(reviewImportService.importFile(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ReviewImportResult> importReviewsJson(@RequestBody List<ReviewImportItem> items) {
        return ApiResponse.ok(reviewImportService.importJson(items));
    }
}
