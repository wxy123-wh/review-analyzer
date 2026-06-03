package com.wh.review.backend.controller;

import static com.wh.review.backend.service.ReviewAggregationService.ASPECT_ALL;
import static com.wh.review.backend.service.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import com.wh.review.backend.dto.WordCloudResponse;
import com.wh.review.backend.service.InsightQueryService;
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
            @RequestParam(value = "aspect", defaultValue = ASPECT_ALL) String aspect
    ) {
        return insightQueryService.wordCloud(productCode, aspect);
    }
}
