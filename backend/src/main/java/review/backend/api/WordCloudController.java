package review.backend.api;

import static review.backend.application.ReviewAggregationService.ASPECT_ALL;
import static review.backend.application.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import review.backend.api.dto.WordCloudResponse;
import review.backend.application.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WordCloudController {

    private final InsightQueryService insightQueryService;

    public WordCloudController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/wordcloud")
    public WordCloudResponse wordCloud(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode,
            @RequestParam(value = "aspect", defaultValue = ASPECT_ALL) String aspect,
            @RequestParam(value = "uxSecondaryLabel", required = false) String uxSecondaryLabel
    ) {
        return insightQueryService.wordCloud(productCode, aspect, uxSecondaryLabel);
    }
}
