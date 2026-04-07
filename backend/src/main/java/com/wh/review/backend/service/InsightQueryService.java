package com.wh.review.backend.service;

import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.CompareItem;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.IssueItem;
import com.wh.review.backend.dto.IssueListResponse;
import com.wh.review.backend.dto.TrendPoint;
import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.dto.ValidationItem;
import com.wh.review.backend.dto.ValidationResponse;
import com.wh.review.backend.dto.WordCloudItem;
import com.wh.review.backend.dto.WordCloudResponse;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedCompareAspectRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedIssueRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedTrendReviewRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedWordCloudReviewRecord;
import com.wh.review.backend.persistence.ActionRepository.ActionValidationContext;
import com.wh.review.backend.persistence.ValidationMetricsRepository;
import com.wh.review.backend.persistence.ValidationMetricsRepository.MetricsPayload;
import com.wh.review.backend.persistence.ValidationMetricsRepository.ValidationSnapshot;
import com.wh.review.backend.util.MathUtils;
import com.wh.review.backend.util.SimpleCache;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InsightQueryService {

    /**
     * Wave-1 contract note:
     * <p>v1 的唯一分析结果来源被固定为“analysis job 物化结果，查询侧消费它”。</p>
     * <p>在 analysis job 真正执行前，本服务仍只提供受控演示数据查询与契约字段稳定性，
     * 不能被当作长期正式的 compute-on-read 主实现。</p>
     */

    private static final double W_NEGATIVE_RATE = 0.35;
    private static final double W_MENTION_VOLUME = 0.25;
    private static final double W_TREND_GROWTH = 0.20;
    private static final double W_COMPETITOR_GAP = 0.20;
    private static final String NO_DATA_NOTICE = "当前暂无可分析的演示评论，请先初始化演示数据。";
    private static final String QUERY_FAILURE_NOTICE = "评论洞察正在更新，请稍后刷新重试。";
    private static final String STATE_SUCCESS = "success";
    private static final String STATE_EMPTY = "empty";
    private static final String STATE_DEGRADED = "degraded";
    private static final String STATE_ERROR = "error";
    private static final String COMPARE_STATE_SUCCESS = "success";
    private static final String COMPARE_STATE_MISSING_TARGET = "missing-target";
    private static final String COMPARE_STATE_PRIMARY_UNAVAILABLE = "primary-unavailable";
    private static final String COMPARE_STATE_COMPARISON_UNAVAILABLE = "comparison-unavailable";
    private static final String COMPARE_STATE_ERROR = "error";
    private static final String COMPARE_MISSING_TARGET_NOTICE = "请选择需要对比的竞品后再查看对比结果。";
    private static final String COMPARE_PRIMARY_UNAVAILABLE_NOTICE = "主产品暂无可用分析结果，请先完成受控数据初始化与分析。";
    private static final String COMPARE_COMPARISON_UNAVAILABLE_NOTICE = "竞品暂无可用分析结果，请先完成受控数据初始化与分析。";
    private static final String ACTION_NOT_FOUND_NOTICE = "未找到对应改进动作，请确认动作编号。";
    private static final String NO_ACTION_NOTICE = "当前暂无改进动作，请先创建动作后查看验证结果。";
    private static final int WORD_CLOUD_TOP_N = 24;
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z][A-Za-z\\-]{2,}");
    private static final Set<String> WORD_STOP_WORDS = Set.of(
            "批次", "整体", "表现", "体验", "场景", "使用", "连续", "明显", "日常", "需要", "影响", "希望", "优化"
    );
    private static final Map<String, String> WORD_ALIAS = Map.ofEntries(
            Map.entry("battery", "续航"),
            Map.entry("bluetooth", "蓝牙连接"),
            Map.entry("noise-canceling", "降噪"),
            Map.entry("comfort", "舒适"),
            Map.entry("microphone", "通话收音")
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightQueryService.class);

    private final ActionService actionService;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final DemoReviewAggregationService demoReviewAggregationService;
    private final ValidationMetricsRepository validationMetricsRepository;
    private final SimpleCache<String, List<DemoReviewAggregationService.AggregatedReview>> reviewsCache =
            new SimpleCache<>(10 * 60 * 1000); // 10 minutes TTL

    public InsightQueryService(
            ActionService actionService,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            DemoReviewAggregationService demoReviewAggregationService,
            ValidationMetricsRepository validationMetricsRepository
    ) {
        this.actionService = actionService;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.demoReviewAggregationService = demoReviewAggregationService;
        this.validationMetricsRepository = validationMetricsRepository;
    }

    public IssueListResponse listIssues(String productCode) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        try {
            List<MaterializedIssueRecord> records = analysisMaterializationRepository.findIssues(normalizedProductCode);
            if (records.isEmpty() && !analysisMaterializationRepository.hasMaterializedOutputs(normalizedProductCode)) {
                return new IssueListResponse(List.of(), STATE_EMPTY, NO_DATA_NOTICE);
            }

            List<IssueItem> items = records.stream()
                    .filter(record -> !DemoReviewAggregationService.ASPECT_UNKNOWN.equals(record.aspect()))
                    .filter(record -> record.negativeRate() > 0D)
                    .map(record -> new IssueItem(
                            buildIssueId(record.aspect(), record.clusterId()),
                            record.title(),
                            record.aspect(),
                            roundTo4(record.priorityScore()),
                            buildIssueEvidenceSummary(
                                    demoReviewAggregationService.aspectDisplayName(record.aspect()),
                                    record.mentionCount(),
                                    record.negativeCount(),
                                    record.negativeRate(),
                                    record.trendGrowth()
                            )
                    ))
                    .toList();

            if (items.isEmpty()) {
                return new IssueListResponse(List.of(), STATE_EMPTY, "评论情绪整体平稳，当前未识别到高优先级问题。");
            }

            return new IssueListResponse(items, STATE_SUCCESS);
        } catch (Exception ex) {
            LOGGER.warn("failed to load materialized issue list, productCode={}", normalizedProductCode, ex);
            return new IssueListResponse(List.of(), STATE_ERROR, QUERY_FAILURE_NOTICE);
        }
    }

    public CompareResponse compare(String productCode, String comparisonProductCode) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        String normalizedComparisonProductCode = normalizeComparisonProductCode(comparisonProductCode);

        if (normalizedComparisonProductCode == null) {
            return new CompareResponse(
                    normalizedProductCode,
                    null,
                    COMPARE_STATE_MISSING_TARGET,
                    COMPARE_MISSING_TARGET_NOTICE,
                    List.of()
            );
        }

        try {
            if (!analysisMaterializationRepository.hasMaterializedOutputs(normalizedProductCode)) {
                return new CompareResponse(
                        normalizedProductCode,
                        normalizedComparisonProductCode,
                        COMPARE_STATE_PRIMARY_UNAVAILABLE,
                        COMPARE_PRIMARY_UNAVAILABLE_NOTICE,
                        List.of()
                );
            }
            if (!analysisMaterializationRepository.hasMaterializedOutputs(normalizedComparisonProductCode)) {
                return new CompareResponse(
                        normalizedProductCode,
                        normalizedComparisonProductCode,
                        COMPARE_STATE_COMPARISON_UNAVAILABLE,
                        COMPARE_COMPARISON_UNAVAILABLE_NOTICE,
                        List.of()
                );
            }

            Map<String, Double> primaryScores = toAspectScoreMap(
                    analysisMaterializationRepository.findCompareAspectScores(normalizedProductCode)
            );
            Map<String, Double> comparisonScores = toAspectScoreMap(
                    analysisMaterializationRepository.findCompareAspectScores(normalizedComparisonProductCode)
            );

            List<CompareItem> items = demoReviewAggregationService.allAspectCodes().stream()
                    .map(aspect -> {
                        double ourScore = primaryScores.getOrDefault(aspect, 0D);
                        double competitorScore = comparisonScores.getOrDefault(aspect, 0D);
                        return new CompareItem(
                                aspect,
                                roundTo4(ourScore),
                                roundTo4(competitorScore),
                                roundTo4(ourScore - competitorScore)
                        );
                    })
                    .toList();

            return new CompareResponse(
                    normalizedProductCode,
                    normalizedComparisonProductCode,
                    COMPARE_STATE_SUCCESS,
                    null,
                    items
            );
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to load materialized compare data, productCode={}, comparisonProductCode={}",
                    normalizedProductCode,
                    normalizedComparisonProductCode,
                    ex
            );
            return new CompareResponse(
                    normalizedProductCode,
                    normalizedComparisonProductCode,
                    COMPARE_STATE_ERROR,
                    QUERY_FAILURE_NOTICE,
                    List.of()
            );
        }
    }

    public TrendResponse trends(String productCode, String aspect) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = demoReviewAggregationService.normalizeTrendAspect(aspect);

        try {
            List<MaterializedTrendReviewRecord> reviews = analysisMaterializationRepository.findTrendReviews(
                    normalizedProductCode,
                    normalizedAspect
            );
            if (reviews.isEmpty()) {
                return new TrendResponse(normalizedProductCode, normalizedAspect, List.of(), STATE_EMPTY, NO_DATA_NOTICE);
            }

            Map<String, PeriodStats> periodStats = new TreeMap<>();
            for (MaterializedTrendReviewRecord review : reviews) {
                String period = toIsoWeekPeriod(review.reviewTime());
                PeriodStats stats = periodStats.computeIfAbsent(period, key -> new PeriodStats());
                stats.mentionCount++;
                if (normalizeSentiment(review.sentimentPolarity()) == DemoReviewAggregationService.Sentiment.NEGATIVE) {
                    stats.negativeCount++;
                }
            }

            List<TrendPoint> points = periodStats.entrySet().stream()
                    .map(entry -> {
                        PeriodStats stats = entry.getValue();
                        double negativeRate = stats.mentionCount == 0
                                ? 0D
                                : roundTo4((double) stats.negativeCount / stats.mentionCount);
                        return new TrendPoint(entry.getKey(), negativeRate, stats.mentionCount);
                    })
                    .toList();

            return new TrendResponse(normalizedProductCode, normalizedAspect, points, STATE_SUCCESS, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to load materialized trend data, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new TrendResponse(normalizedProductCode, normalizedAspect, List.of(), STATE_ERROR, QUERY_FAILURE_NOTICE);
        }
    }

    public WordCloudResponse wordCloud(String productCode, String aspect) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = demoReviewAggregationService.normalizeWordCloudAspect(aspect);

        try {
            String scopedAspect = DemoReviewAggregationService.ASPECT_ALL.equals(normalizedAspect)
                    ? null
                    : normalizedAspect;
            List<MaterializedWordCloudReviewRecord> reviews = analysisMaterializationRepository.findWordCloudReviews(
                    normalizedProductCode,
                    scopedAspect
            );
            if (reviews.isEmpty()) {
                return new WordCloudResponse(normalizedProductCode, normalizedAspect, List.of(), STATE_EMPTY, NO_DATA_NOTICE);
            }

            Map<String, KeywordStats> keywordStats = new HashMap<>();
            for (MaterializedWordCloudReviewRecord review : reviews) {
                Matcher matcher = WORD_TOKEN_PATTERN.matcher(review.content() == null ? "" : review.content());
                while (matcher.find()) {
                    String keyword = normalizeWordCloudKeyword(matcher.group());
                    if (keyword == null || WORD_STOP_WORDS.contains(keyword)) {
                        continue;
                    }
                    KeywordStats stats = keywordStats.computeIfAbsent(keyword, key -> new KeywordStats());
                    stats.frequency++;
                    switch (normalizeSentiment(review.sentimentPolarity())) {
                        case POSITIVE -> stats.positiveCount++;
                        case NEGATIVE -> stats.negativeCount++;
                        case NEUTRAL -> stats.neutralCount++;
                    }
                }
            }

            if (keywordStats.isEmpty()) {
                return new WordCloudResponse(
                        normalizedProductCode,
                        normalizedAspect,
                        List.of(),
                        STATE_DEGRADED,
                        "当前评论文本暂未提取到可展示关键词，请稍后重试。"
                );
            }

            int maxFrequency = keywordStats.values().stream()
                    .mapToInt(KeywordStats::frequency)
                    .max()
                    .orElse(1);

            List<WordCloudItem> items = keywordStats.entrySet().stream()
                    .sorted(Comparator
                            .comparingInt((Map.Entry<String, KeywordStats> entry) -> entry.getValue().frequency())
                            .reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(WORD_CLOUD_TOP_N)
                    .map(entry -> new WordCloudItem(
                            entry.getKey(),
                            entry.getValue().frequency(),
                            roundTo4((double) entry.getValue().frequency() / maxFrequency),
                            resolveSentimentTag(entry.getValue())
                    ))
                    .toList();

            return new WordCloudResponse(normalizedProductCode, normalizedAspect, items, STATE_SUCCESS, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to load materialized wordcloud data, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new WordCloudResponse(normalizedProductCode, normalizedAspect, List.of(), STATE_ERROR, QUERY_FAILURE_NOTICE);
        }
    }

    public ValidationResponse validation(String actionId) {
        if (actionId != null && !actionId.isBlank()) {
            return actionService.findValidationContextById(actionId)
                    .map(action -> {
                        ValidationItem item = buildValidationFromAction(action);
                        String notice = isValidationFallback(item) ? QUERY_FAILURE_NOTICE : null;
                        return new ValidationResponse(
                                List.of(item),
                                notice == null ? STATE_SUCCESS : STATE_DEGRADED,
                                notice
                        );
                    })
                    .orElseGet(() -> new ValidationResponse(List.of(), STATE_EMPTY, ACTION_NOT_FOUND_NOTICE));
        }

        List<ValidationItem> items = actionService.listValidationContexts().stream()
                .map(this::buildValidationFromAction)
                .toList();
        if (items.isEmpty()) {
            return new ValidationResponse(List.of(), STATE_EMPTY, NO_ACTION_NOTICE);
        }
        boolean degraded = items.stream().anyMatch(this::isValidationFallback);
        return new ValidationResponse(items, degraded ? STATE_DEGRADED : STATE_SUCCESS, degraded ? QUERY_FAILURE_NOTICE : null);
    }

    private ValidationItem buildValidationFromAction(ActionValidationContext context) {
        ActionResponse action = context.action();
        Long actionNumericId = parseActionId(action.actionId());
        if (actionNumericId != null) {
            Optional<ValidationSnapshot> snapshot = validationMetricsRepository.findLatestByActionId(actionNumericId);
            if (snapshot.isPresent()) {
                return toValidationItem(action.actionId(), snapshot.get());
            }
        }

        String productCode = demoReviewAggregationService.normalizeProductCode(action.productCode());
        String aspect = resolveAspect(context);
        String aspectName = aspect == null ? "综合问题" : demoReviewAggregationService.aspectDisplayName(aspect);
        try {
            List<DemoReviewAggregationService.AggregatedReview> allReviews =
                    loadReviewsWithCache(productCode);
            List<DemoReviewAggregationService.AggregatedReview> scopedReviews =
                    demoReviewAggregationService.filterByAspect(allReviews, aspect);
            ValidationSnapshot snapshot = createAndPersistValidationSnapshot(actionNumericId, context, scopedReviews, aspectName, aspect);
            return toValidationItem(action.actionId(), snapshot);
        } catch (Exception ex) {
            LOGGER.warn("failed to build validation summary, actionId={}", action.actionId(), ex);
            return new ValidationItem(
                    action.actionId(),
                    0D,
                    0D,
                    0D,
                    QUERY_FAILURE_NOTICE
            );
        }
    }

    private ValidationSnapshot createAndPersistValidationSnapshot(
            Long actionNumericId,
            ActionValidationContext context,
            List<DemoReviewAggregationService.AggregatedReview> scopedReviews,
            String aspectName,
            String aspect
    ) {
        ActionResponse action = context.action();
        Instant calculatedAt = Instant.now();
        Instant fallbackBoundary = resolveFallbackBoundary(scopedReviews, action.createdAt());
        Instant boundary = resolveEffectiveBoundary(context.launchedAt(), scopedReviews, fallbackBoundary);
        List<DemoReviewAggregationService.AggregatedReview> before = scopedReviews.stream()
                .filter(review -> review.reviewTime().isBefore(boundary))
                .toList();
        List<DemoReviewAggregationService.AggregatedReview> after = scopedReviews.stream()
                .filter(review -> !review.reviewTime().isBefore(boundary))
                .toList();

        String summary;
        MetricsPayload beforeMetrics;
        MetricsPayload afterMetrics;
        if (before.isEmpty() || after.isEmpty()) {
            summary = buildValidationInsufficientDataSummary(action, aspectName, scopedReviews.size());
            beforeMetrics = metricsPayload(before, aspect, boundary);
            afterMetrics = metricsPayload(after, aspect, boundary);
        } else {
            double beforeNegativeRate = computeNegativeRate(before);
            double afterNegativeRate = computeNegativeRate(after);
            double improvementRate = roundTo4(beforeNegativeRate - afterNegativeRate);
            summary = buildValidationSummary(
                    action,
                    aspectName,
                    beforeNegativeRate,
                    afterNegativeRate,
                    improvementRate,
                    before.size(),
                    after.size(),
                    boundary,
                    context.launchedAt() != null && !before.isEmpty() && !after.isEmpty()
            );
            beforeMetrics = metricsPayload(before, aspect, boundary);
            afterMetrics = metricsPayload(after, aspect, boundary);
        }

        ValidationSnapshot snapshot = new ValidationSnapshot(
                actionNumericId == null ? -1L : actionNumericId,
                resolveWindowStart(scopedReviews, boundary),
                resolveWindowEnd(scopedReviews, boundary),
                beforeMetrics,
                afterMetrics,
                summary,
                calculatedAt
        );
        if (actionNumericId != null) {
            validationMetricsRepository.save(snapshot);
            if (context.launchedAt() == null) {
                actionService.updateLaunchContext(action.actionId(), boundary);
            }
        }
        return snapshot;
    }

    private ValidationItem toValidationItem(String actionId, ValidationSnapshot snapshot) {
        double beforeNegativeRate = roundTo4(snapshot.beforeMetrics().negativeRate());
        double afterNegativeRate = roundTo4(snapshot.afterMetrics().negativeRate());
        return new ValidationItem(
                actionId,
                beforeNegativeRate,
                afterNegativeRate,
                roundTo4(beforeNegativeRate - afterNegativeRate),
                snapshot.conclusion()
        );
    }

    private MetricsPayload metricsPayload(
            List<DemoReviewAggregationService.AggregatedReview> reviews,
            String aspect,
            Instant boundary
    ) {
        long negativeCount = reviews.stream()
                .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                .count();
        return new MetricsPayload(
                reviews.size(),
                (int) negativeCount,
                computeNegativeRate(reviews),
                aspect,
                boundary.toString()
        );
    }

    private Instant resolveFallbackBoundary(List<DemoReviewAggregationService.AggregatedReview> scopedReviews, Instant fallback) {
        if (scopedReviews.isEmpty()) {
            return fallback == null ? Instant.EPOCH : fallback;
        }
        return scopedReviews.get(scopedReviews.size() / 2).reviewTime();
    }

    private Instant resolveEffectiveBoundary(
            Instant launchedAt,
            List<DemoReviewAggregationService.AggregatedReview> scopedReviews,
            Instant fallbackBoundary
    ) {
        if (launchedAt == null) {
            return fallbackBoundary;
        }
        boolean hasBefore = scopedReviews.stream().anyMatch(review -> review.reviewTime().isBefore(launchedAt));
        boolean hasAfter = scopedReviews.stream().anyMatch(review -> !review.reviewTime().isBefore(launchedAt));
        if (hasBefore && hasAfter) {
            return launchedAt;
        }
        return fallbackBoundary;
    }

    private Instant resolveWindowStart(List<DemoReviewAggregationService.AggregatedReview> scopedReviews, Instant boundary) {
        if (scopedReviews.isEmpty()) {
            return boundary;
        }
        return scopedReviews.getFirst().reviewTime();
    }

    private Instant resolveWindowEnd(List<DemoReviewAggregationService.AggregatedReview> scopedReviews, Instant boundary) {
        if (scopedReviews.isEmpty()) {
            return boundary;
        }
        return scopedReviews.getLast().reviewTime();
    }

    private Long parseActionId(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(actionId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveAspect(ActionValidationContext context) {
        if (context.aspect() != null && !context.aspect().isBlank()) {
            return demoReviewAggregationService.normalizeAspect(context.aspect());
        }
        return resolveAspectByIssueId(context.action().issueId());
    }

    private boolean isValidationFallback(ValidationItem item) {
        return item != null && QUERY_FAILURE_NOTICE.equals(item.summary());
    }

    private String resolveIssueTitle(String aspect) {
        return switch (aspect) {
            case "battery" -> "续航体验波动";
            case "bluetooth" -> "蓝牙连接稳定性不足";
            case "noise-canceling" -> "降噪效果一致性不足";
            case "comfort" -> "佩戴舒适度反馈分化";
            case "microphone" -> "通话收音表现待优化";
            default -> "综合体验反馈待优化";
        };
    }

    private String buildIssueEvidenceSummary(
            String aspectName,
            int mentionCount,
            int negativeCount,
            double negativeRate,
            double trendDelta
    ) {
        String trendText;
        if (trendDelta > 0.005D) {
            trendText = "较上一时间窗口上升 " + roundTo2(Math.abs(trendDelta) * 100) + "%";
        } else if (trendDelta < -0.005D) {
            trendText = "较上一时间窗口下降 " + roundTo2(Math.abs(trendDelta) * 100) + "%";
        } else {
            trendText = "较上一时间窗口基本持平";
        }
        return aspectName + "相关评论共 " + mentionCount
                + " 条，负向反馈 " + negativeCount
                + " 条（" + roundTo2(negativeRate * 100) + "%），" + trendText + "。";
    }

    private String buildIssueId(String aspect, long clusterId) {
        return "iss-" + aspect.replace('-', '_') + "-" + clusterId;
    }

    private String resolveAspectByIssueId(String issueId) {
        if (issueId == null || issueId.isBlank()) {
            return null;
        }
        String lower = issueId.toLowerCase();
        if (lower.contains("battery")) {
            return "battery";
        }
        if (lower.contains("connect") || lower.contains("bluetooth")) {
            return "bluetooth";
        }
        if (lower.contains("noise")) {
            return "noise-canceling";
        }
        if (lower.contains("comfort")) {
            return "comfort";
        }
        if (lower.contains("call") || lower.contains("microphone")) {
            return "microphone";
        }
        return null;
    }

    private String buildValidationSummary(
            ActionResponse action,
            String aspectName,
            double beforeNegativeRate,
            double afterNegativeRate,
            double improvementRate,
            int beforeSampleCount,
            int afterSampleCount,
            Instant boundary,
            boolean usedPersistedLaunchWindow
    ) {
        String actionName = actionDisplayName(action.actionName());
        double beforePct = roundTo2(beforeNegativeRate * 100);
        double afterPct = roundTo2(afterNegativeRate * 100);
        double deltaPct = roundTo2(Math.abs(improvementRate) * 100);
        String boundaryLabel = usedPersistedLaunchWindow ? "动作落地时间" : "动作关联评论窗口";
        String windowNote = "（以 " + boundaryLabel + " " + boundary + " 为分界，前"
                + beforeSampleCount + "条、后" + afterSampleCount + "条）";

        if (improvementRate > 0.005D) {
            return "动作「" + actionName + "」在" + aspectName + "维度演示评论中" + windowNote + "，负向占比由 "
                    + beforePct + "% 降至 " + afterPct + "%，改善 " + deltaPct + "%。";
        }
        if (improvementRate < -0.005D) {
            return "动作「" + actionName + "」在" + aspectName + "维度演示评论中" + windowNote + "，负向占比由 "
                    + beforePct + "% 升至 " + afterPct + "%，上升 " + deltaPct + "%，建议继续跟进。";
        }
        return "动作「" + actionName + "」在" + aspectName + "维度演示评论中" + windowNote + "，负向占比基本持平（"
                + beforePct + "% -> " + afterPct + "%）。";
    }

    private String buildValidationInsufficientDataSummary(ActionResponse action, String aspectName, int sampleCount) {
        String actionName = actionDisplayName(action.actionName());
        return "动作「" + actionName + "」在" + aspectName + "维度仅有 " + sampleCount
                + " 条演示评论，暂无法形成稳定的前后对比结论。";
    }

    private String actionDisplayName(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return "未命名动作";
        }
        return actionName.trim();
    }

    private String normalizeWordCloudKeyword(String rawKeyword) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return null;
        }
        String normalized = rawKeyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return null;
        }
        return WORD_ALIAS.getOrDefault(normalized, normalized);
    }

    private DemoReviewAggregationService.Sentiment normalizeSentiment(String sentimentPolarity) {
        if (sentimentPolarity == null || sentimentPolarity.isBlank()) {
            return DemoReviewAggregationService.Sentiment.NEUTRAL;
        }
        return switch (sentimentPolarity.trim().toUpperCase(Locale.ROOT)) {
            case "NEGATIVE" -> DemoReviewAggregationService.Sentiment.NEGATIVE;
            case "POSITIVE" -> DemoReviewAggregationService.Sentiment.POSITIVE;
            default -> DemoReviewAggregationService.Sentiment.NEUTRAL;
        };
    }

    private String resolveSentimentTag(KeywordStats stats) {
        if (stats.negativeCount >= stats.positiveCount && stats.negativeCount >= stats.neutralCount) {
            return "负向";
        }
        if (stats.positiveCount >= stats.neutralCount) {
            return "正向";
        }
        return "中性";
    }

    private double computeNegativeRate(List<DemoReviewAggregationService.AggregatedReview> reviews) {
        if (reviews.isEmpty()) {
            return 0D;
        }
        long negativeCount = reviews.stream()
                .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                .count();
        return roundTo4((double) negativeCount / reviews.size());
    }

    private String toIsoWeekPeriod(java.time.Instant reviewTime) {
        java.time.ZonedDateTime zoned = java.time.ZonedDateTime.ofInstant(reviewTime, java.time.ZoneOffset.UTC);
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        int year = zoned.get(weekFields.weekBasedYear());
        int week = zoned.get(weekFields.weekOfWeekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    private double clamp01(double value) {
        return MathUtils.clamp01(value);
    }

    private double roundTo4(double value) {
        return MathUtils.roundTo4(value);
    }

    private double roundTo2(double value) {
        return MathUtils.roundTo2(value);
    }

    private String normalizeComparisonProductCode(String comparisonProductCode) {
        if (comparisonProductCode == null || comparisonProductCode.isBlank()) {
            return null;
        }
        return comparisonProductCode.trim();
    }

    private Map<String, Double> toAspectScoreMap(List<MaterializedCompareAspectRecord> records) {
        Map<String, Double> aspectScores = new HashMap<>();
        for (MaterializedCompareAspectRecord record : records) {
            String normalizedAspect = demoReviewAggregationService.normalizeAspect(record.aspect());
            if (DemoReviewAggregationService.ASPECT_UNKNOWN.equals(normalizedAspect) || record.mentionCount() <= 0) {
                continue;
            }
            aspectScores.put(normalizedAspect, clamp01(record.avgSentimentScore()));
        }
        return aspectScores;
    }

    private static final class KeywordStats {
        private int frequency;
        private int positiveCount;
        private int neutralCount;
        private int negativeCount;

        private int frequency() {
            return frequency;
        }
    }

    private static final class PeriodStats {
        private int mentionCount;
        private int negativeCount;
    }

    private List<DemoReviewAggregationService.AggregatedReview> loadReviewsWithCache(String productCode) {
        return reviewsCache.get(productCode, () -> demoReviewAggregationService.loadReviews(productCode));
    }
}
