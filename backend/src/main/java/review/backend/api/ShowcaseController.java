package review.backend.api;

import review.backend.api.dto.showcase.ShowcaseAgentArenaResponse;
import review.backend.api.dto.showcase.ShowcaseChaosResponse;
import review.backend.api.dto.showcase.ShowcaseExplainabilityResponse;
import review.backend.api.dto.showcase.ShowcasePipelineResponse;
import review.backend.api.dto.showcase.ShowcaseReportPreviewRequest;
import review.backend.api.dto.showcase.ShowcaseReportPreviewResponse;
import review.backend.application.ShowcaseService;
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
