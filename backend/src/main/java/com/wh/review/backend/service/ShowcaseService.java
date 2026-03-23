package com.wh.review.backend.service;

import com.wh.review.backend.dto.showcase.ShowcaseAgentArenaResponse;
import com.wh.review.backend.dto.showcase.ShowcaseAgentItem;
import com.wh.review.backend.dto.showcase.ShowcaseChaosDrill;
import com.wh.review.backend.dto.showcase.ShowcaseChaosResponse;
import com.wh.review.backend.dto.showcase.ShowcaseExplainabilityResponse;
import com.wh.review.backend.dto.showcase.ShowcaseFeatureContribution;
import com.wh.review.backend.dto.showcase.ShowcasePipelineResponse;
import com.wh.review.backend.dto.showcase.ShowcaseReportPreviewResponse;
import com.wh.review.backend.dto.showcase.ShowcaseStage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShowcaseService {

    private static final String STATUS_PLACEHOLDER = "PLACEHOLDER";

    public ShowcasePipelineResponse pipeline() {
        List<ShowcaseStage> stages = List.of(
                new ShowcaseStage("SYNC", "DONE", "ingested 12,480 comments"),
                new ShowcaseStage("CLEANING", "DONE", "noise filter and dedup complete"),
                new ShowcaseStage("ASPECT_NLP", "RUNNING", "domain lexicon v0.3 in dry-run mode"),
                new ShowcaseStage("SCORING", "QUEUED", "priority score job waiting for window close"),
                new ShowcaseStage("DELIVERY", "QUEUED", "dashboard sync pending")
        );
        return new ShowcasePipelineResponse(
                STATUS_PLACEHOLDER,
                false,
                "pipeline orchestration is mocked for acceptance demo",
                stages
        );
    }

    public ShowcaseAgentArenaResponse agentArena() {
        List<ShowcaseAgentItem> agents = List.of(
                new ShowcaseAgentItem("collector-agent", "SYNC", "IDLE", 0.98),
                new ShowcaseAgentItem("insight-agent", "ANALYZE", "RUNNING", 0.86),
                new ShowcaseAgentItem("strategy-agent", "RECOMMEND", "QUEUED", 0.79),
                new ShowcaseAgentItem("guard-agent", "RISK", "RUNNING", 0.91)
        );
        return new ShowcaseAgentArenaResponse(
                STATUS_PLACEHOLDER,
                false,
                "multi-agent collaboration is currently a deterministic simulation",
                agents
        );
    }

    public ShowcaseExplainabilityResponse explainability() {
        List<ShowcaseFeatureContribution> contributions = List.of(
                new ShowcaseFeatureContribution("negative_rate", 0.35),
                new ShowcaseFeatureContribution("mention_volume", 0.24),
                new ShowcaseFeatureContribution("trend_growth", 0.21),
                new ShowcaseFeatureContribution("competitor_gap", 0.20)
        );
        return new ShowcaseExplainabilityResponse(
                STATUS_PLACEHOLDER,
                false,
                "explainability view uses static weighted factors; no model introspection yet",
                contributions
        );
    }

    public ShowcaseChaosResponse chaos() {
        List<ShowcaseChaosDrill> drills = List.of(
                new ShowcaseChaosDrill("db-latency-spike", "PENDING", "simulate p95 increase to 3s"),
                new ShowcaseChaosDrill("provider-rate-limit", "PENDING", "simulate onebound 429 bursts"),
                new ShowcaseChaosDrill("nlp-timeout", "PENDING", "simulate nlp-service timeout > 10s")
        );
        return new ShowcaseChaosResponse(
                STATUS_PLACEHOLDER,
                false,
                "chaos drill playback is static and not connected to runtime infra",
                drills
        );
    }

    public ShowcaseReportPreviewResponse reportPreview(String module) {
        return new ShowcaseReportPreviewResponse(
                STATUS_PLACEHOLDER,
                false,
                "real report export is not implemented; this is a preview payload",
                List.of(
                        "Executive summary for module=" + module,
                        "Top issue snapshot and evidence list",
                        "Simulated KPI trend and next-step checklist"
                )
        );
    }
}
