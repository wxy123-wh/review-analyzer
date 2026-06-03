package com.wh.review.backend.controller;

import static com.wh.review.backend.service.ReviewAggregationService.DEFAULT_PRODUCT_CODE;

import com.wh.review.backend.dto.IssueListResponse;
import com.wh.review.backend.service.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IssueController {

    private final InsightQueryService insightQueryService;

    public IssueController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/issues")
    public IssueListResponse issues(
            @RequestParam(value = "productCode", defaultValue = DEFAULT_PRODUCT_CODE) String productCode
    ) {
        return insightQueryService.listIssues(productCode);
    }
}
