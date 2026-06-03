package review.backend.api;

import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import review.backend.api.dto.PositiveInsightResponse;
import review.backend.application.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PositiveInsightController {

    private final InsightQueryService insightQueryService;

    public PositiveInsightController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/positive-insights")
    public PositiveInsightResponse positiveInsights(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode,
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        return insightQueryService.positiveInsights(productCode, limit);
    }
}
