package com.wh.review.backend.controller;

import static com.wh.review.backend.service.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import com.wh.review.backend.dto.PositiveInsightResponse;
import com.wh.review.backend.service.InsightQueryService;
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
