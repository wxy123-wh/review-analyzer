package com.wh.review.backend.service;

import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.CompareItem;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.DataQualityResponse;
import com.wh.review.backend.dto.IssueItem;
import com.wh.review.backend.dto.IssueListResponse;
import com.wh.review.backend.dto.PositiveInsightItem;
import com.wh.review.backend.dto.PositiveInsightResponse;
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
import com.wh.review.backend.persistence.DataQualityRepository;
import com.wh.review.backend.persistence.DataQualityRepository.DataQualityRun;
import com.wh.review.backend.persistence.ReviewSemanticLabelRepository;
import com.wh.review.backend.persistence.ReviewSemanticLabelRepository.PositiveInsightAggregate;
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

    private static final double W_NEGATIVE_RATE = 0.35;
    private static final double W_MENTION_VOLUME = 0.25;
    private static final double W_TREND_GROWTH = 0.20;
    private static final double W_COMPETITOR_GAP = 0.20;
    private static final String NO_DATA_NOTICE = "当前暂无可分析的真实评论，请先通过爬虫或 JSONL 导入评论，再启动分析。";
    private static final String QUERY_FAILURE_NOTICE = "评论洞察正在更新，请稍后刷新重试。";
    private static final String STATE_SUCCESS = "success";
    private static final String STATE_EMPTY = "empty";
    private static final String STATE_DEGRADED = "degraded";
    private static final String STATE_ERROR = "error";
    private static final int POSITIVE_INSIGHT_DEFAULT_LIMIT = 5;
    private static final String COMPARE_STATE_SUCCESS = "success";
    private static final String COMPARE_STATE_MISSING_TARGET = "missing-target";
    private static final String COMPARE_STATE_PRIMARY_UNAVAILABLE = "primary-unavailable";
    private static final String COMPARE_STATE_COMPARISON_UNAVAILABLE = "comparison-unavailable";
    private static final String COMPARE_STATE_ERROR = "error";
    private static final String COMPARE_MISSING_TARGET_NOTICE = "请选择需要对比的竞品后再查看对比结果。";
    private static final String COMPARE_PRIMARY_UNAVAILABLE_NOTICE = "主产品暂无可用分析结果，请先导入真实评论并启动分析。";
    private static final String COMPARE_COMPARISON_UNAVAILABLE_NOTICE = "竞品暂无可用分析结果，请先导入竞品真实评论并启动分析。";
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
    private final DataQualityRepository dataQualityRepository;
    private final ReviewSemanticLabelRepository reviewSemanticLabelRepository;
    private final ReviewAggregationService reviewAggregationService;
    private final ValidationMetricsRepository validationMetricsRepository;
    private final SimpleCache<String, List<ReviewAggregationService.AggregatedReview>> reviewsCache =
            new SimpleCache<>(10 * 60 * 1000); // 10 minutes TTL

    public InsightQueryService(
            ActionService actionService,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            DataQualityRepository dataQualityRepository,
            ReviewSemanticLabelRepository reviewSemanticLabelRepository,
            ReviewAggregationService reviewAggregationService,
            ValidationMetricsRepository validationMetricsRepository
    ) {
        this.actionService = actionService;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.dataQualityRepository = dataQualityRepository;
        this.reviewSemanticLabelRepository = reviewSemanticLabelRepository;
        this.reviewAggregationService = reviewAggregationService;
        this.validationMetricsRepository = validationMetricsRepository;
    }

    public IssueListResponse listIssues(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        try {
            List<MaterializedIssueRecord> records = analysisMaterializationRepository.findIssues(normalizedProductCode);
            if (records.isEmpty() && !analysisMaterializationRepository.hasMaterializedOutputs(normalizedProductCode)) {
                return new IssueListResponse(List.of(), STATE_EMPTY, NO_DATA_NOTICE);
            }

            List<IssueItem> items = records.stream()
                    .filter(record -> !ReviewAggregationService.ASPECT_UNKNOWN.equals(record.aspect()))
                    .filter(record -> record.negativeRate() > 0D)
                    .map(record -> new IssueItem(
                            buildIssueId(record.aspect(), record.clusterId()),
                            record.title(),
                            record.aspect(),
                            roundTo4(record.priorityScore()),
                            buildIssueEvidenceSummary(
                                    reviewAggregationService.aspectDisplayName(record.aspect()),
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
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
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

            List<CompareItem> items = reviewAggregationService.allAspectCodes().stream()
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
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = reviewAggregationService.normalizeTrendAspect(aspect);

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
                if (normalizeSentiment(review.sentimentPolarity()) == ReviewAggregationService.Sentiment.NEGATIVE) {
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
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = reviewAggregationService.normalizeWordCloudAspect(aspect);

        try {
            String scopedAspect = ReviewAggregationService.ASPECT_ALL.equals(normalizedAspect)
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

    public DataQualityResponse dataQuality(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        try {
            Optional<DataQualityRun> latestRun = dataQualityRepository.findLatest(normalizedProductCode);
            if (latestRun.isEmpty()) {
                return new DataQualityResponse(
                        normalizedProductCode,
                        STATE_EMPTY,
                        "当前暂无清洗摘要，请先运行 pipeline/clean_reviews.py 并在导入时携带 cleaningSummary。",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null
                );
            }
            DataQualityRun run = latestRun.get();
            return new DataQualityResponse(
                    run.productCode(),
                    STATE_SUCCESS,
                    null,
                    run.rawCount(),
                    run.cleanedCount(),
                    run.removedCount(),
                    run.htmlCleanedCount(),
                    run.exactDuplicateCount(),
                    run.emptyContentCount(),
                    run.invalidJsonCount(),
                    run.importedAt()
            );
        } catch (Exception ex) {
            LOGGER.warn("failed to load data quality summary, productCode={}", normalizedProductCode, ex);
            return new DataQualityResponse(
                    normalizedProductCode,
                    STATE_ERROR,
                    QUERY_FAILURE_NOTICE,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null
            );
        }
    }

    public PositiveInsightResponse positiveInsights(String productCode, int limit) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        int normalizedLimit = limit <= 0 ? POSITIVE_INSIGHT_DEFAULT_LIMIT : Math.min(limit, 20);
        try {
            List<PositiveInsightAggregate> aggregates =
                    reviewSemanticLabelRepository.findPositiveInsightAggregates(normalizedProductCode);
            int totalPositiveLabels = reviewSemanticLabelRepository.countPositiveLabels(normalizedProductCode);
            if (aggregates.isEmpty() || totalPositiveLabels <= 0) {
                return new PositiveInsightResponse(
                        STATE_EMPTY,
                        "当前暂无可提炼的正面 UX 标签，请先导入真实评论并启动分析。",
                        List.of()
                );
            }

            List<PositiveInsightItem> items = aggregates.stream()
                    .map(aggregate -> toPositiveInsightItem(normalizedProductCode, aggregate, totalPositiveLabels))
                    .sorted(Comparator
                            .comparingDouble(PositiveInsightItem::score)
                            .reversed()
                            .thenComparing(PositiveInsightItem::uxSecondaryLabel))
                    .limit(normalizedLimit)
                    .toList();
            return new PositiveInsightResponse(items.isEmpty() ? STATE_EMPTY : STATE_SUCCESS, items);
        } catch (Exception ex) {
            LOGGER.warn("failed to load positive insights, productCode={}", normalizedProductCode, ex);
            return new PositiveInsightResponse(STATE_ERROR, QUERY_FAILURE_NOTICE, List.of());
        }
    }

    private PositiveInsightItem toPositiveInsightItem(
            String productCode,
            PositiveInsightAggregate aggregate,
            int totalPositiveLabels
    ) {
        double mentionShare = totalPositiveLabels <= 0 ? 0D : (double) aggregate.mentionCount() / totalPositiveLabels;
        double positiveRate = aggregate.mentionCount() == 0 ? 0D : (double) aggregate.positiveCount() / aggregate.mentionCount();
        double avgConfidence = clamp01(aggregate.avgConfidence());
        double score = roundTo4(mentionShare * 0.45D + positiveRate * 0.35D + avgConfidence * 0.20D);
        List<String> evidence = reviewSemanticLabelRepository.findEvidence(
                productCode,
                aggregate.aspect(),
                aggregate.uxSecondaryLabel(),
                3
        ).stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .limit(3)
                .toList();
        String sellingPoint = normalizeSellingPoint(aggregate.standardizedReason(), aggregate.aspect());
        return new PositiveInsightItem(
                buildSellingPointId(aggregate.aspect(), aggregate.uxSecondaryLabel()),
                aggregate.aspect(),
                aggregate.uxPrimaryLabel(),
                aggregate.uxSecondaryLabel(),
                sellingPoint,
                aggregate.mentionCount(),
                roundTo4(positiveRate),
                score,
                evidence
        );
    }

    private String buildSellingPointId(String aspect, String uxSecondaryLabel) {
        String safeAspect = aspect == null || aspect.isBlank()
                ? "general"
                : aspect.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String labelHash = Integer.toHexString((uxSecondaryLabel == null ? "" : uxSecondaryLabel).hashCode());
        return "sp-" + safeAspect + "-" + labelHash;
    }

    private String normalizeSellingPoint(String standardizedReason, String aspect) {
        if (standardizedReason != null && !standardizedReason.isBlank() && !"无明显问题".equals(standardizedReason.trim())) {
            return standardizedReason.trim();
        }
        return switch (aspect == null ? "" : aspect) {
            case "battery" -> "续航持久";
            case "bluetooth" -> "连接稳定";
            case "noise-canceling" -> "降噪明显";
            case "comfort" -> "佩戴舒适";
            case "microphone" -> "通话清晰";
            default -> "正面体验稳定";
        };
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

        try {
            String productCode = reviewAggregationService.normalizeProductCode(action.productCode());
            String aspect = resolveAspect(context);
            String aspectName = aspect == null ? "综合问题" : reviewAggregationService.aspectDisplayName(aspect);
            List<ReviewAggregationService.AggregatedReview> allReviews =
                    loadReviewsWithCache(productCode);
            List<ReviewAggregationService.AggregatedReview> scopedReviews =
                    reviewAggregationService.filterByAspect(allReviews, aspect);
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
            List<ReviewAggregationService.AggregatedReview> scopedReviews,
            String aspectName,
            String aspect
    ) {
        ActionResponse action = context.action();
        Instant calculatedAt = Instant.now();
        Instant fallbackBoundary = resolveFallbackBoundary(scopedReviews, action.createdAt());
        Instant boundary = resolveEffectiveBoundary(context.launchedAt(), scopedReviews, fallbackBoundary);
        List<ReviewAggregationService.AggregatedReview> before = scopedReviews.stream()
                .filter(review -> review.reviewTime().isBefore(boundary))
                .toList();
        List<ReviewAggregationService.AggregatedReview> after = scopedReviews.stream()
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
            List<ReviewAggregationService.AggregatedReview> reviews,
            String aspect,
            Instant boundary
    ) {
        long negativeCount = reviews.stream()
                .filter(review -> review.sentiment() == ReviewAggregationService.Sentiment.NEGATIVE)
                .count();
        return new MetricsPayload(
                reviews.size(),
                (int) negativeCount,
                computeNegativeRate(reviews),
                aspect,
                boundary.toString()
        );
    }

    private Instant resolveFallbackBoundary(List<ReviewAggregationService.AggregatedReview> scopedReviews, Instant fallback) {
        if (scopedReviews.isEmpty()) {
            return fallback == null ? Instant.EPOCH : fallback;
        }
        return scopedReviews.get(scopedReviews.size() / 2).reviewTime();
    }

    private Instant resolveEffectiveBoundary(
            Instant launchedAt,
            List<ReviewAggregationService.AggregatedReview> scopedReviews,
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

    private Instant resolveWindowStart(List<ReviewAggregationService.AggregatedReview> scopedReviews, Instant boundary) {
        if (scopedReviews.isEmpty()) {
            return boundary;
        }
        return scopedReviews.getFirst().reviewTime();
    }

    private Instant resolveWindowEnd(List<ReviewAggregationService.AggregatedReview> scopedReviews, Instant boundary) {
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
            return reviewAggregationService.normalizeAspect(context.aspect());
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
            return "动作「" + actionName + "」在" + aspectName + "维度真实评论中" + windowNote + "，负向占比由 "
                    + beforePct + "% 降至 " + afterPct + "%，改善 " + deltaPct + "%。";
        }
        if (improvementRate < -0.005D) {
            return "动作「" + actionName + "」在" + aspectName + "维度真实评论中" + windowNote + "，负向占比由 "
                    + beforePct + "% 升至 " + afterPct + "%，上升 " + deltaPct + "%，建议继续跟进。";
        }
        return "动作「" + actionName + "」在" + aspectName + "维度真实评论中" + windowNote + "，负向占比基本持平（"
                + beforePct + "% -> " + afterPct + "%）。";
    }

    private String buildValidationInsufficientDataSummary(ActionResponse action, String aspectName, int sampleCount) {
        String actionName = actionDisplayName(action.actionName());
        return "动作「" + actionName + "」在" + aspectName + "维度仅有 " + sampleCount
                + " 条真实评论，暂无法形成稳定的前后对比结论。";
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

    private ReviewAggregationService.Sentiment normalizeSentiment(String sentimentPolarity) {
        if (sentimentPolarity == null || sentimentPolarity.isBlank()) {
            return ReviewAggregationService.Sentiment.NEUTRAL;
        }
        return switch (sentimentPolarity.trim().toUpperCase(Locale.ROOT)) {
            case "NEGATIVE" -> ReviewAggregationService.Sentiment.NEGATIVE;
            case "POSITIVE" -> ReviewAggregationService.Sentiment.POSITIVE;
            default -> ReviewAggregationService.Sentiment.NEUTRAL;
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

    private double computeNegativeRate(List<ReviewAggregationService.AggregatedReview> reviews) {
        if (reviews.isEmpty()) {
            return 0D;
        }
        long negativeCount = reviews.stream()
                .filter(review -> review.sentiment() == ReviewAggregationService.Sentiment.NEGATIVE)
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
            String normalizedAspect = reviewAggregationService.normalizeAspect(record.aspect());
            if (ReviewAggregationService.ASPECT_UNKNOWN.equals(normalizedAspect) || record.mentionCount() <= 0) {
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

    private List<ReviewAggregationService.AggregatedReview> loadReviewsWithCache(String productCode) {
        return reviewsCache.get(productCode, () -> reviewAggregationService.loadReviews(productCode));
    }
}
