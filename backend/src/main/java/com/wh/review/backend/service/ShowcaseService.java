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
import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.dto.CompareItem;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.IssueItem;
import com.wh.review.backend.dto.IssueListResponse;
import com.wh.review.backend.dto.TrendPoint;
import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.dto.ValidationItem;
import com.wh.review.backend.dto.ValidationResponse;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.TopIssueScoreBreakdown;
import com.wh.review.backend.persistence.SyncJobRepository;
import com.wh.review.backend.persistence.SyncJobRepository.SyncJobSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ShowcaseService {

    private static final String STATUS_LIVE = "LIVE";
    private static final String STATUS_STABLE = "STABLE";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_CONTROLLED_DATA_ONLY = "CONTROLLED_DATA_ONLY";
    private static final String STATUS_RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_IDLE = "IDLE";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    private static final double W_NEGATIVE_RATE = 0.35D;
    private static final double W_MENTION_VOLUME = 0.25D;
    private static final double W_TREND_GROWTH = 0.20D;
    private static final double W_COMPETITOR_GAP = 0.20D;

    private final DemoReviewAggregationService demoReviewAggregationService;
    private final SyncJobRepository syncJobRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final InsightQueryService insightQueryService;
    private final ActionService actionService;

    public ShowcaseService(
            DemoReviewAggregationService demoReviewAggregationService,
            SyncJobRepository syncJobRepository,
            AnalysisJobRepository analysisJobRepository,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            InsightQueryService insightQueryService,
            ActionService actionService
    ) {
        this.demoReviewAggregationService = demoReviewAggregationService;
        this.syncJobRepository = syncJobRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.insightQueryService = insightQueryService;
        this.actionService = actionService;
    }

    private String buildContractNote(String state, String strategy, String dataSource, String detail) {
        return "v1-state=" + state + "; strategy=" + strategy + "; data-source=" + dataSource + "; " + detail;
    }

    public ShowcasePipelineResponse pipeline() {
        Optional<SyncJobSnapshot> latestSyncJob = syncJobRepository.findLatest();
        Optional<AnalysisJobResponse> latestAnalysisJob = analysisJobRepository.findLatest();
        String productCode = resolveProductCode(latestAnalysisJob, latestSyncJob);
        boolean hasMaterializedOutputs = analysisMaterializationRepository.hasMaterializedOutputs(productCode);
        IssueListResponse issueResponse = insightQueryService.listIssues(productCode);
        List<IssueItem> issues = issueResponse.items();
        List<ActionResponse> actions = actionService.listAll().stream()
                .filter(action -> productCode.equals(action.productCode()))
                .toList();
        ValidationResponse validationResponse = insightQueryService.validation(null);

        List<ShowcaseStage> stages = List.of(
                buildSyncStage(latestSyncJob.orElse(null)),
                buildAnalysisStage(latestAnalysisJob.orElse(null)),
                buildMaterializationStage(productCode, latestAnalysisJob.orElse(null), hasMaterializedOutputs, issueResponse),
                buildActionStage(actions),
                buildValidationStage(validationResponse, actions)
        );
        return new ShowcasePipelineResponse(
                resolveTopLevelStatus(stages.stream().map(ShowcaseStage::state).toList()),
                true,
                buildContractNote(
                        "live",
                        "keep",
                        "sync_jobs+analysis_jobs+materialized_outputs+actions+validation",
                        "productCode=" + productCode + "; stages are synthesized from persisted v1 sync, analysis, materialization, action, and validation state"
                ),
                stages
        );
    }

    public ShowcaseAgentArenaResponse agentArena() {
        Optional<SyncJobSnapshot> latestSyncJob = syncJobRepository.findLatest();
        Optional<AnalysisJobResponse> latestAnalysisJob = analysisJobRepository.findLatest();
        String productCode = resolveProductCode(latestAnalysisJob, latestSyncJob);
        IssueListResponse issueResponse = insightQueryService.listIssues(productCode);
        List<IssueItem> issues = issueResponse.items();
        List<ActionResponse> actions = actionService.listAll().stream()
                .filter(action -> productCode.equals(action.productCode()))
                .toList();
        ValidationResponse validationResponse = insightQueryService.validation(null);

        String syncState = buildSyncStage(latestSyncJob.orElse(null)).state();
        String analysisState = buildAnalysisStage(latestAnalysisJob.orElse(null)).state();
        String insightState = buildInsightLaneState(productCode, issueResponse);
        String actionState = buildActionValidationLaneState(actions, validationResponse);

        List<ShowcaseAgentItem> agents = List.of(
                new ShowcaseAgentItem("sync-lane", "SYNC", syncState, confidenceFromState(syncState, latestSyncJob.isPresent())),
                new ShowcaseAgentItem("analysis-lane", "ANALYSIS", analysisState, confidenceFromState(analysisState, latestAnalysisJob.isPresent())),
                new ShowcaseAgentItem("insight-lane", "QUERY", insightState, confidenceFromState(insightState, !issues.isEmpty())),
                new ShowcaseAgentItem("action-validation-lane", "ACTION_VALIDATION", actionState, confidenceFromState(actionState, !actions.isEmpty()))
        );
        return new ShowcaseAgentArenaResponse(
                resolveTopLevelStatus(agents.stream().map(ShowcaseAgentItem::state).toList()),
                true,
                buildContractNote(
                        "live",
                        "keep",
                        "sync_jobs+analysis_jobs+materialized_outputs+actions+validation",
                        "lane rows are synthesized from persisted v1 subsystem health rather than autonomous placeholder workers"
                ),
                agents
        );
    }

    public ShowcaseExplainabilityResponse explainability() {
        String productCode = resolveProductCode(analysisJobRepository.findLatest(), syncJobRepository.findLatest());
        Optional<TopIssueScoreBreakdown> topIssue = analysisMaterializationRepository.findTopIssueScoreBreakdown(productCode);
        List<ShowcaseFeatureContribution> contributions = topIssue
                .map(this::buildIssueContributions)
                .orElseGet(this::buildFixedWeightContributions);
        return new ShowcaseExplainabilityResponse(
                STATUS_CONTROLLED_DATA_ONLY,
                true,
                buildContractNote(
                        "controlled-data-only",
                        "keep",
                        topIssue.isPresent()
                                ? "materialized_issue_scores+deterministic-score-weights"
                                : "deterministic-score-weights",
                        topIssue
                                .map(issue -> "productCode=" + productCode
                                        + "; issue=" + issue.title()
                                        + "; aspect=" + issue.aspect()
                                        + "; using current fixed-weight issue score decomposition rather than model attribution")
                                .orElse("productCode=" + productCode
                                        + "; no materialized issue score is available yet, so the view exposes the live fixed-weight scoring decomposition only")
                ),
                contributions
        );
    }

    public ShowcaseChaosResponse chaos() {
        Optional<SyncJobSnapshot> latestSyncJob = syncJobRepository.findLatest();
        Optional<AnalysisJobResponse> latestAnalysisJob = analysisJobRepository.findLatest();
        String productCode = resolveProductCode(latestAnalysisJob, latestSyncJob);
        List<ShowcaseChaosDrill> drills = List.of(
                buildChaosSyncSignal(latestSyncJob.orElse(null)),
                buildChaosAnalysisSignal(latestAnalysisJob.orElse(null)),
                buildChaosMaterializationSignal(productCode, latestAnalysisJob.orElse(null))
        );
        return new ShowcaseChaosResponse(
                resolveChaosStatus(drills),
                true,
                buildContractNote(
                        "runtime-state",
                        "keep",
                        "sync_jobs+analysis_jobs+materialized_outputs",
                        "productCode=" + productCode + "; entries reflect recent persisted failure, degraded, or unavailable runtime signals instead of scripted chaos drills"
                ),
                drills
        );
    }

    public ShowcaseReportPreviewResponse reportPreview(String module) {
        String normalizedModule = normalizeModule(module);
        String productCode = resolveProductCode(analysisJobRepository.findLatest(), syncJobRepository.findLatest());
        IssueListResponse issueResponse = insightQueryService.listIssues(productCode);
        List<IssueItem> issues = issueResponse.items();
        CompareResponse compare = insightQueryService.compare(productCode, DemoDataInitializationService.DEFAULT_COMPARE_PRODUCT_CODE);
        TrendResponse trends = insightQueryService.trends(productCode, DemoReviewAggregationService.DEFAULT_TREND_ASPECT);
        List<ActionResponse> actions = actionService.listAll().stream()
                .filter(action -> productCode.equals(action.productCode()))
                .toList();
        ValidationResponse validation = insightQueryService.validation(null);
        List<String> sections = buildPreviewSections(normalizedModule, productCode, issues, compare, trends, actions, validation);
        return new ShowcaseReportPreviewResponse(
                resolveReportStatus(compare, trends, validation, sections),
                true,
                buildContractNote(
                        "live",
                        "keep",
                        "issues+compare+trends+actions+validation",
                        "module=" + normalizedModule + "; preview sections are assembled from current query outputs rather than static report copy"
                )
                ,
                sections
        );
    }

    private String resolveProductCode(
            Optional<AnalysisJobResponse> latestAnalysisJob,
            Optional<SyncJobSnapshot> latestSyncJob
    ) {
        List<ActionResponse> latestActions = actionService.listAll();
        if (!latestActions.isEmpty() && latestActions.getFirst().productCode() != null
                && !latestActions.getFirst().productCode().isBlank()) {
            return latestActions.getFirst().productCode();
        }
        if (latestAnalysisJob.isPresent() && latestAnalysisJob.get().productCode() != null
                && !latestAnalysisJob.get().productCode().isBlank()) {
            return latestAnalysisJob.get().productCode();
        }
        if (latestSyncJob.isPresent() && latestSyncJob.get().targetProductCode() != null
                && !latestSyncJob.get().targetProductCode().isBlank()) {
            return latestSyncJob.get().targetProductCode();
        }
        return demoReviewAggregationService.normalizeProductCode(DemoReviewAggregationService.DEFAULT_PRODUCT_CODE);
    }

    private ShowcaseStage buildSyncStage(SyncJobSnapshot latestSyncJob) {
        if (latestSyncJob == null) {
            return new ShowcaseStage("SYNC", STATUS_UNAVAILABLE, "尚未记录同步任务，当前无法显示真实同步链路状态。");
        }
        return new ShowcaseStage(
                "SYNC",
                normalizeJobState(latestSyncJob.status(), latestSyncJob.errorMessage()),
                "provider=" + safeValue(latestSyncJob.provider())
                        + "; productCode=" + safeValue(latestSyncJob.targetProductCode())
                        + "; fetchedCount=" + latestSyncJob.fetchedCount()
                        + describeError(latestSyncJob.errorMessage())
        );
    }

    private ShowcaseStage buildAnalysisStage(AnalysisJobResponse latestAnalysisJob) {
        if (latestAnalysisJob == null) {
            return new ShowcaseStage("ANALYSIS", STATUS_UNAVAILABLE, "尚未记录分析任务，当前无法显示真实分析执行状态。");
        }
        return new ShowcaseStage(
                "ANALYSIS",
                normalizeJobState(latestAnalysisJob.status(), latestAnalysisJob.errorMessage()),
                "productCode=" + safeValue(latestAnalysisJob.productCode())
                        + "; jobId=" + safeValue(latestAnalysisJob.jobId())
                        + describeError(latestAnalysisJob.errorMessage())
        );
    }

    private ShowcaseStage buildMaterializationStage(
            String productCode,
            AnalysisJobResponse latestAnalysisJob,
            boolean hasMaterializedOutputs,
            IssueListResponse issueResponse
    ) {
        if (!hasMaterializedOutputs) {
            return new ShowcaseStage(
                    "MATERIALIZATION",
                    STATUS_UNAVAILABLE,
                    "productCode=" + productCode + "; 当前尚未检测到物化输出。"
            );
        }
        Optional<Instant> latestSourceUpdateTime = analysisMaterializationRepository.findLatestSourceUpdateTime(productCode);
        boolean stale = latestAnalysisJob != null
                && latestAnalysisJob.finishedAt() != null
                && latestSourceUpdateTime.isPresent()
                && latestSourceUpdateTime.get().isAfter(latestAnalysisJob.finishedAt());
        return new ShowcaseStage(
                "MATERIALIZATION",
                stale ? STATUS_DEGRADED : STATUS_SUCCEEDED,
                "productCode=" + productCode
                        + "; issueCount=" + issueResponse.items().stream().filter(this::isRealIssue).count()
                        + (latestSourceUpdateTime.isPresent() ? "; latestSourceUpdate=" + latestSourceUpdateTime.get() : "")
                        + (stale ? "; source data is newer than the latest successful analysis job" : "; outputs align with the latest persisted analysis window")
        );
    }

    private ShowcaseStage buildActionStage(List<ActionResponse> actions) {
        if (actions.isEmpty()) {
            return new ShowcaseStage("ACTIONS", STATUS_IDLE, "当前产品还没有已登记的改进行动。");
        }
        long plannedCount = actions.stream().filter(action -> "PLANNED".equalsIgnoreCase(action.status())).count();
        return new ShowcaseStage(
                "ACTIONS",
                STATUS_SUCCEEDED,
                "actions=" + actions.size() + "; planned=" + plannedCount + "; latestAction=" + actions.getFirst().actionName()
        );
    }

    private ShowcaseStage buildValidationStage(ValidationResponse validationResponse, List<ActionResponse> actions) {
        if (validationResponse.items().isEmpty()) {
            return new ShowcaseStage(
                "VALIDATION",
                actions.isEmpty() || "empty".equals(validationResponse.state()) ? STATUS_IDLE : STATUS_DEGRADED,
                validationResponse.notice() == null || validationResponse.notice().isBlank()
                        ? "当前没有可展示的验证快照。"
                        : validationResponse.notice()
            );
        }
        ValidationItem latestValidation = validationResponse.items().getFirst();
        return new ShowcaseStage(
                "VALIDATION",
                "degraded".equals(validationResponse.state()) ? STATUS_DEGRADED : STATUS_SUCCEEDED,
                "validationCount=" + validationResponse.items().size()
                        + "; latestImprovementRate=" + formatPercent(latestValidation.improvementRate())
                        + (validationResponse.notice() == null || validationResponse.notice().isBlank()
                        ? ""
                        : "; notice=" + validationResponse.notice())
        );
    }

    private String buildInsightLaneState(String productCode, IssueListResponse issueResponse) {
        if (!analysisMaterializationRepository.hasMaterializedOutputs(productCode)) {
            return STATUS_UNAVAILABLE;
        }
        return switch (issueResponse.state()) {
            case "success" -> STATUS_SUCCEEDED;
            case "empty", "degraded" -> STATUS_DEGRADED;
            case "error" -> STATUS_FAILED;
            default -> issueResponse.items().stream().anyMatch(this::isRealIssue) ? STATUS_SUCCEEDED : STATUS_DEGRADED;
        };
    }

    private String buildActionValidationLaneState(List<ActionResponse> actions, ValidationResponse validationResponse) {
        if (actions.isEmpty()) {
            return STATUS_IDLE;
        }
        if (validationResponse.items().isEmpty()) {
            return STATUS_DEGRADED;
        }
        return "degraded".equals(validationResponse.state()) ? STATUS_DEGRADED : STATUS_SUCCEEDED;
    }

    private List<ShowcaseFeatureContribution> buildIssueContributions(TopIssueScoreBreakdown topIssue) {
        double negativeContribution = W_NEGATIVE_RATE * topIssue.negativeRate();
        double mentionContribution = W_MENTION_VOLUME * topIssue.mentionVolume();
        double trendContribution = W_TREND_GROWTH * topIssue.trendGrowth();
        double competitorContribution = W_COMPETITOR_GAP * topIssue.competitorGap();
        double total = negativeContribution + mentionContribution + trendContribution + competitorContribution;
        if (total <= 0D) {
            return buildFixedWeightContributions();
        }
        return List.of(
                        new ShowcaseFeatureContribution("negative_rate", round4(negativeContribution / total)),
                        new ShowcaseFeatureContribution("mention_volume", round4(mentionContribution / total)),
                        new ShowcaseFeatureContribution("trend_growth", round4(trendContribution / total)),
                        new ShowcaseFeatureContribution("competitor_gap", round4(competitorContribution / total))
                ).stream()
                .sorted((left, right) -> Double.compare(right.weight(), left.weight()))
                .toList();
    }

    private List<ShowcaseFeatureContribution> buildFixedWeightContributions() {
        return List.of(
                new ShowcaseFeatureContribution("negative_rate", W_NEGATIVE_RATE),
                new ShowcaseFeatureContribution("mention_volume", W_MENTION_VOLUME),
                new ShowcaseFeatureContribution("trend_growth", W_TREND_GROWTH),
                new ShowcaseFeatureContribution("competitor_gap", W_COMPETITOR_GAP)
        );
    }

    private ShowcaseChaosDrill buildChaosSyncSignal(SyncJobSnapshot latestSyncJob) {
        if (latestSyncJob == null) {
            return new ShowcaseChaosDrill("sync-runtime", STATUS_UNAVAILABLE, "尚未记录同步运行态，无法判断同步链路韧性。");
        }
        String state = normalizeJobState(latestSyncJob.status(), latestSyncJob.errorMessage());
        return new ShowcaseChaosDrill(
                "sync-runtime",
                normalizeChaosState(state),
                "provider=" + safeValue(latestSyncJob.provider())
                        + "; productCode=" + safeValue(latestSyncJob.targetProductCode())
                        + describeError(latestSyncJob.errorMessage())
        );
    }

    private ShowcaseChaosDrill buildChaosAnalysisSignal(AnalysisJobResponse latestAnalysisJob) {
        if (latestAnalysisJob == null) {
            return new ShowcaseChaosDrill("analysis-runtime", STATUS_UNAVAILABLE, "尚未记录分析运行态，无法判断分析链路韧性。");
        }
        String state = normalizeJobState(latestAnalysisJob.status(), latestAnalysisJob.errorMessage());
        return new ShowcaseChaosDrill(
                "analysis-runtime",
                normalizeChaosState(state),
                "productCode=" + safeValue(latestAnalysisJob.productCode())
                        + "; jobId=" + safeValue(latestAnalysisJob.jobId())
                        + describeError(latestAnalysisJob.errorMessage())
        );
    }

    private ShowcaseChaosDrill buildChaosMaterializationSignal(String productCode, AnalysisJobResponse latestAnalysisJob) {
        boolean hasMaterializedOutputs = analysisMaterializationRepository.hasMaterializedOutputs(productCode);
        if (!hasMaterializedOutputs) {
            return new ShowcaseChaosDrill(
                    "materialization-runtime",
                    STATUS_UNAVAILABLE,
                    "productCode=" + productCode + "; 当前尚未检测到物化输出。"
            );
        }
        Optional<Instant> latestSourceUpdateTime = analysisMaterializationRepository.findLatestSourceUpdateTime(productCode);
        boolean stale = latestAnalysisJob != null
                && latestAnalysisJob.finishedAt() != null
                && latestSourceUpdateTime.isPresent()
                && latestSourceUpdateTime.get().isAfter(latestAnalysisJob.finishedAt());
        return new ShowcaseChaosDrill(
                "materialization-runtime",
                stale ? STATUS_DEGRADED : STATUS_STABLE,
                stale
                        ? "productCode=" + productCode + "; source reviews are newer than the latest successful analysis output."
                        : "productCode=" + productCode + "; materialized outputs are aligned with the latest persisted analysis window."
        );
    }

    private String resolveChaosStatus(List<ShowcaseChaosDrill> drills) {
        List<String> states = drills.stream().map(ShowcaseChaosDrill::state).toList();
        if (states.stream().allMatch(STATUS_UNAVAILABLE::equals)) {
            return STATUS_RUNTIME_UNAVAILABLE;
        }
        if (states.stream().anyMatch(this::isAttentionState)) {
            return STATUS_DEGRADED;
        }
        return STATUS_STABLE;
    }

    private List<String> buildPreviewSections(
            String module,
            String productCode,
            List<IssueItem> issues,
            CompareResponse compare,
            TrendResponse trends,
            List<ActionResponse> actions,
            ValidationResponse validation
    ) {
        IssueItem topIssue = issues.stream().filter(this::isRealIssue).findFirst().orElse(null);
        CompareItem largestGap = compare.items().stream()
                .sorted((left, right) -> Double.compare(Math.abs(right.gap()), Math.abs(left.gap())))
                .findFirst()
                .orElse(null);
        TrendPoint latestTrendPoint = trends.points().isEmpty() ? null : trends.points().getLast();
        ValidationItem latestValidation = validation.items().isEmpty() ? null : validation.items().getFirst();

        return switch (module) {
            case "issues" -> List.of(
                    topIssue == null
                            ? "问题清单：当前没有可用的高优先级问题，需先完成评论分析物化。"
                            : "问题清单：当前最高优先级问题是“" + topIssue.title() + "”，优先级 "
                                    + formatScore(topIssue.priorityScore()) + "。",
                    topIssue == null
                            ? "问题证据：暂无代表性证据摘要。"
                            : "问题证据：" + topIssue.evidenceSummary(),
                    "问题覆盖：当前返回 " + issues.stream().filter(this::isRealIssue).count() + " 条真实问题项。"
            );
            case "compare" -> List.of(
                    "对比范围：主产品 " + productCode + "，竞品 " + safeValue(compare.comparisonProductCode()) + "。",
                    largestGap == null
                            ? "对比信号：" + safeValue(compare.notice())
                            : "最大差距：" + largestGap.aspect() + " 维度差距 " + formatSignedScore(largestGap.gap()) + "。",
                    "对比状态：" + compare.state() + (compare.notice() == null ? "。" : "，" + compare.notice())
            );
            case "trends" -> List.of(
                    "趋势范围：产品 " + productCode + "，关注维度 " + DemoReviewAggregationService.DEFAULT_TREND_ASPECT + "。",
                    latestTrendPoint == null
                            ? "趋势信号：" + safeValue(trends.notice())
                            : "最新趋势：" + latestTrendPoint.period() + " 负面率 "
                                    + formatPercent(latestTrendPoint.negativeRate()) + "，提及量 "
                                    + latestTrendPoint.mentionVolume() + "。",
                    latestTrendPoint == null ? "趋势状态：暂无可用时间窗口。" : "趋势状态：已回放到最新物化时间窗口。"
            );
            default -> List.of(
                    topIssue == null
                            ? "执行摘要：产品 " + productCode + " 当前还没有完成真实问题物化。"
                            : "执行摘要：产品 " + productCode + " 当前最高优先级问题是“" + topIssue.title() + "”。",
                    largestGap == null
                            ? "竞品摘要：" + safeValue(compare.notice())
                            : "竞品摘要：" + largestGap.aspect() + " 维度差距 " + formatSignedScore(largestGap.gap()) + "。",
                    latestTrendPoint == null
                            ? "趋势摘要：" + safeValue(trends.notice())
                            : "趋势摘要：" + latestTrendPoint.period() + " 负面率 "
                                    + formatPercent(latestTrendPoint.negativeRate()) + "。",
                    latestValidation == null
                            ? "动作与验证：当前已登记 " + actions.size() + " 个动作，尚无可展示的验证快照。"
                            : "动作与验证：当前已登记 " + actions.size() + " 个动作；最新验证结论为“"
                                    + latestValidation.summary() + "”。"
            );
        };
    }

    private String resolveReportStatus(
            CompareResponse compare,
            TrendResponse trends,
            ValidationResponse validation,
            List<String> sections
    ) {
        if (sections.isEmpty()) {
            return STATUS_RUNTIME_UNAVAILABLE;
        }
        if (!"success".equals(compare.state())
                || !"success".equals(trends.state())
                || !"success".equals(validation.state())) {
            return STATUS_DEGRADED;
        }
        return STATUS_LIVE;
    }

    private String normalizeJobState(String status, String errorMessage) {
        String normalizedStatus = safeValue(status).trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus.isBlank()) {
            return STATUS_UNAVAILABLE;
        }
        if (errorMessage != null && errorMessage.toLowerCase(Locale.ROOT).startsWith("degraded:")) {
            return STATUS_DEGRADED;
        }
        return switch (normalizedStatus) {
            case STATUS_QUEUED -> STATUS_QUEUED;
            case STATUS_RUNNING -> STATUS_RUNNING;
            case STATUS_SUCCEEDED -> STATUS_SUCCEEDED;
            case STATUS_FAILED -> STATUS_FAILED;
            default -> normalizedStatus;
        };
    }

    private String resolveTopLevelStatus(List<String> states) {
        if (states.isEmpty() || states.stream().allMatch(this::isUnavailableOrIdleState)) {
            return STATUS_RUNTIME_UNAVAILABLE;
        }
        if (states.stream().anyMatch(this::isAttentionState)) {
            return STATUS_DEGRADED;
        }
        return STATUS_LIVE;
    }

    private boolean isUnavailableOrIdleState(String state) {
        return STATUS_UNAVAILABLE.equals(state) || STATUS_IDLE.equals(state);
    }

    private boolean isAttentionState(String state) {
        return STATUS_FAILED.equals(state)
                || STATUS_DEGRADED.equals(state)
                || STATUS_UNAVAILABLE.equals(state);
    }

    private double confidenceFromState(String state, boolean hasBackingData) {
        double value = switch (state) {
            case STATUS_SUCCEEDED, STATUS_STABLE, STATUS_LIVE -> 0.92D;
            case STATUS_RUNNING -> 0.78D;
            case STATUS_QUEUED -> 0.60D;
            case STATUS_IDLE -> 0.48D;
            case STATUS_DEGRADED -> 0.45D;
            case STATUS_FAILED -> 0.18D;
            case STATUS_UNAVAILABLE, STATUS_RUNTIME_UNAVAILABLE -> 0.10D;
            default -> 0.55D;
        };
        if (!hasBackingData) {
            return round4(Math.min(value, 0.35D));
        }
        return round4(value);
    }

    private boolean isRealIssue(IssueItem issue) {
        return issue != null && issue.issueId() != null && !issue.issueId().startsWith("iss-demo-");
    }

    private String normalizeChaosState(String state) {
        if (STATUS_FAILED.equals(state) || STATUS_DEGRADED.equals(state) || STATUS_UNAVAILABLE.equals(state)) {
            return state;
        }
        if (STATUS_RUNNING.equals(state) || STATUS_QUEUED.equals(state)) {
            return STATUS_DEGRADED;
        }
        return STATUS_STABLE;
    }

    private String normalizeModule(String module) {
        if (module == null || module.isBlank()) {
            return "overview";
        }
        return module.trim().toLowerCase(Locale.ROOT);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String describeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "";
        }
        return "; signal=" + errorMessage;
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100D);
    }

    private String formatScore(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String formatSignedScore(double value) {
        return String.format(Locale.ROOT, "%+.4f", value);
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }
}
