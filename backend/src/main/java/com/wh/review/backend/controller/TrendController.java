package com.wh.review.backend.controller;

import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.service.InsightQueryService;
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
            @RequestParam(value = "productCode", defaultValue = "demo-earphone") String productCode,
            @RequestParam(value = "aspect", defaultValue = "battery") String aspect
    ) {
        return insightQueryService.trends(productCode, aspect);
    }
}
