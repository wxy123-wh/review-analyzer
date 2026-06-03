package com.wh.review.backend.controller;

import static com.wh.review.backend.service.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.service.InsightQueryService;
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
