package com.wh.review.backend.controller;

import com.wh.review.backend.dto.showcase.ShowcaseAgentArenaResponse;
import com.wh.review.backend.dto.showcase.ShowcaseChaosResponse;
import com.wh.review.backend.dto.showcase.ShowcaseExplainabilityResponse;
import com.wh.review.backend.dto.showcase.ShowcasePipelineResponse;
import com.wh.review.backend.dto.showcase.ShowcaseReportPreviewRequest;
import com.wh.review.backend.dto.showcase.ShowcaseReportPreviewResponse;
import com.wh.review.backend.service.ShowcaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showcase")
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    public ShowcaseController(ShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping("/pipeline")
    public ShowcasePipelineResponse pipeline() {
        return showcaseService.pipeline();
    }

    @GetMapping("/agent-arena")
    public ShowcaseAgentArenaResponse agentArena() {
        return showcaseService.agentArena();
    }

    @GetMapping("/explainability")
    public ShowcaseExplainabilityResponse explainability() {
        return showcaseService.explainability();
    }

    @GetMapping("/chaos")
    public ShowcaseChaosResponse chaos() {
        return showcaseService.chaos();
    }

    @PostMapping("/reports/preview")
    public ShowcaseReportPreviewResponse reportPreview(@Valid @RequestBody ShowcaseReportPreviewRequest request) {
        return showcaseService.reportPreview(request.module());
    }
}
