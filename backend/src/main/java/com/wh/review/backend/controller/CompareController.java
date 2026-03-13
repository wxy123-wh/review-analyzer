package com.wh.review.backend.controller;

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
            @RequestParam(value = "productCode", defaultValue = "demo-earphone") String productCode
    ) {
        return insightQueryService.compare(productCode);
    }
}
