package com.wh.review.backend.controller;

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
            @RequestParam(value = "productCode", defaultValue = "demo-earphone") String productCode,
            @RequestParam(value = "aspect", defaultValue = "all") String aspect
    ) {
        return insightQueryService.wordCloud(productCode, aspect);
    }
}
