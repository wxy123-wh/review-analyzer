package com.wh.review.backend.controller;

import com.wh.review.backend.dto.ValidationResponse;
import com.wh.review.backend.service.InsightQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ValidationController {

    private final InsightQueryService insightQueryService;

    public ValidationController(InsightQueryService insightQueryService) {
        this.insightQueryService = insightQueryService;
    }

    @GetMapping("/validation")
    public ValidationResponse validation(
            @RequestParam(value = "actionId", required = false) String actionId
    ) {
        return insightQueryService.validation(actionId);
    }
}
