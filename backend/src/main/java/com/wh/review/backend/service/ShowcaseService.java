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
    private static final String STATUS_CONTROLLED_DATA_ONLY = "CONTROLLED_DATA_ONLY";

    private String buildContractNote(String state, String strategy, String dataSource, String detail) {
        return "v1-state=" + state + "; strategy=" + strategy + "; data-source=" + dataSource + "; " + detail;
    }

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
                buildContractNote(
                        "placeholder",
                        "replace",
                        "static-demo-payload",
                        "current payload only preserves the stage field shape and does not represent a real pipeline"
                ),
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
                buildContractNote(
                        "placeholder",
                        "replace",
                        "static-demo-payload",
                        "current payload only preserves the agent arena field shape and does not execute real agents"
                ),
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
                STATUS_CONTROLLED_DATA_ONLY,
                false,
                buildContractNote(
                        "controlled-data-only",
                        "keep",
                        "deterministic-score-weights",
                        "current payload explains fixed issue scoring weights rather than model-level feature attribution"
                ),
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
                buildContractNote(
                        "gated-placeholder",
                        "hide-by-default",
                        "static-demo-payload",
                        "current payload is a gated drill script and is not connected to runtime infrastructure"
                ),
                drills
        );
    }

    public ShowcaseReportPreviewResponse reportPreview(String module) {
        return new ShowcaseReportPreviewResponse(
                STATUS_PLACEHOLDER,
                false,
                buildContractNote(
                        "placeholder",
                        "replace",
                        "static-preview-sections",
                        "current payload only preserves previewSections for module=" + module + " and does not export reports"
                ),
                List.of(
                        "Executive summary for module=" + module,
                        "Top issue snapshot and evidence list",
                        "Simulated KPI trend and next-step checklist"
                )
        );
    }
}
