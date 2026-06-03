package review.backend.api;

import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;
import static review.backend.application.ReviewAggregationService.DEFAULT_TREND_ASPECT;

import review.backend.api.dto.TrendResponse;
import review.backend.application.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TrendController {

    private final InsightQueryService insightQueryService;

    public TrendController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/trends")
    public TrendResponse trends(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode,
            @RequestParam(value = "aspect", defaultValue = DEFAULT_TREND_ASPECT) String aspect,
            @RequestParam(value = "uxSecondaryLabel", required = false) String uxSecondaryLabel
    ) {
        return insightQueryService.trends(productCode, aspect, uxSecondaryLabel);
    }
}
