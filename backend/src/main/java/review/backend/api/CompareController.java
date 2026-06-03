package review.backend.api;

import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import review.backend.api.dto.CompareResponse;
import review.backend.application.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CompareController {

    private final InsightQueryService insightQueryService;

    public CompareController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/compare")
    public CompareResponse compare(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode,
            @RequestParam(value = "comparisonProductCode", required = false) String comparisonProductCode
    ) {
        return insightQueryService.compare(productCode, comparisonProductCode);
    }
}
