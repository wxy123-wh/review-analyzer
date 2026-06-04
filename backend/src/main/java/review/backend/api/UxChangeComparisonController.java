package review.backend.api;

import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import review.backend.api.dto.UxChangeComparisonHistoryResponse;
import review.backend.api.dto.UxChangeComparisonRequest;
import review.backend.api.dto.UxChangeComparisonResponse;
import review.backend.application.UxChangeComparisonService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ux-change-comparisons")
public class UxChangeComparisonController {

    private final UxChangeComparisonService uxChangeComparisonService;

    public UxChangeComparisonController(UxChangeComparisonService uxChangeComparisonService) {
        this.uxChangeComparisonService = uxChangeComparisonService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UxChangeComparisonResponse create(@RequestBody UxChangeComparisonRequest request) {
        return uxChangeComparisonService.createAndCompare(request);
    }

    @GetMapping
    public UxChangeComparisonHistoryResponse list(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode
    ) {
        return uxChangeComparisonService.list(productCode);
    }

    @GetMapping("/{id}")
    public UxChangeComparisonResponse detail(@PathVariable("id") String id) {
        return uxChangeComparisonService.findAndCompare(id);
    }
}
