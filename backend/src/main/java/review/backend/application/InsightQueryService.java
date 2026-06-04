package review.backend.application;

import review.backend.api.dto.ActionResponse;
import review.backend.api.dto.CompareItem;
import review.backend.api.dto.CompareResponse;
import review.backend.api.dto.DataQualityResponse;
import review.backend.api.dto.IssueItem;
import review.backend.api.dto.IssueListResponse;
import review.backend.api.dto.PositiveInsightItem;
import review.backend.api.dto.PositiveInsightResponse;
import review.backend.api.dto.TrendPoint;
import review.backend.api.dto.TrendResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.api.dto.UxPrimaryLabelResponse;
import review.backend.api.dto.UxSecondaryLabelResponse;
import review.backend.api.dto.ValidationItem;
import review.backend.api.dto.ValidationResponse;
import review.backend.api.dto.WordCloudItem;
import review.backend.api.dto.WordCloudResponse;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.MaterializedCompareAspectRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedIssueRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedTrendReviewRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedValidationReviewRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedWordCloudReviewRecord;
import review.backend.data.ActionRepository.ActionValidationContext;
import review.backend.data.DataQualityRepository;
import review.backend.data.DataQualityRepository.DataQualityRun;
import review.backend.data.ProductRepository;
import review.backend.data.ReviewQueryRepository;
import review.backend.data.ReviewSemanticLabelRepository;
import review.backend.data.ReviewSemanticLabelRepository.PositiveInsightAggregate;
import review.backend.data.TaxonomyRepository;
import review.backend.data.ValidationMetricsRepository;
import review.backend.data.ValidationMetricsRepository.MetricsPayload;
import review.backend.data.ValidationMetricsRepository.ValidationSnapshot;
import review.backend.util.MathUtils;
import review.backend.util.SimpleCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private static final String ANALYSIS_NOT_READY_NOTICE = "真实评论已导入数据库，但 LLM 分析结果尚未写入下游表，请等待分析完成或重新启动 LLM 分析。";
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
    private static final String COMPARE_STATE_TAXONOMY_MISMATCH = "taxonomy-mismatch";
    private static final String COMPARE_STATE_ERROR = "error";
    private static final String COMPARE_MISSING_TARGET_NOTICE = "请选择需要对比的竞品后再查看对比结果。";
    private static final String COMPARE_PRIMARY_UNAVAILABLE_NOTICE = "主产品暂无可用分析结果，请先导入真实评论并启动分析。";
    private static final String COMPARE_COMPARISON_UNAVAILABLE_NOTICE = "竞品暂无可用分析结果，请先导入竞品真实评论并启动分析。";
    private static final String COMPARE_TAXONOMY_MISMATCH_NOTICE = "主商品与竞品绑定的 UX 标签体系不一致，请先统一 taxonomy 后再对比。";
    private static final String ACTION_NOT_FOUND_NOTICE = "未找到对应改进动作，请确认动作编号。";
    private static final String NO_ACTION_NOTICE = "当前暂无改进动作，请先创建动作后查看验证结果。";
    private static final int WORD_CLOUD_TOP_N = 24;
    private static final int WORD_CLOUD_SENTIMENT_BUCKET_SIZE = WORD_CLOUD_TOP_N / 2;
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z][A-Za-z\\-]{2,}");
    private static final Set<String> WORD_STOP_WORDS = Set.of(
            "批次", "整体", "表现", "体验", "场景", "使用", "连续", "明显", "日常", "需要", "影响", "希望", "优化",
            "商品", "用户", "评价", "评论", "追评", "京东", "平台", "购买", "收到", "客服", "发货",
            "手机", "耳机", "小米", "buds", "pro", "airpods", "app",
            "音质音效", "做工质感", "舒适度", "续航能力", "其他特色", "无明显问题", "综合体验问题",
            "这款", "这个", "首先", "一般"
    );
    private static final Set<String> WORD_TEXT_NOISE_TERMS = Set.of(
            "商品", "产品", "手机", "耳机", "小米", "Buds", "buds", "Pro", "pro", "AirPods", "airpods", "APP", "app",
            "音质音效", "做工质感", "舒适度", "续航能力", "其他特色"
    );
    private static final Set<String> WORD_ALLOWED_ENGLISH_TERMS = Set.of(
            "wifi", "wi-fi", "nfc", "ios", "android", "type-c", "usb-c"
    );
    private static final Set<String> WORD_PHRASE_NOISE_MARKERS = Set.of(
            "觉得", "最近", "这款", "这个", "入手", "拿到", "挑了", "评测", "首先", "最后", "决定"
    );
    private static final Map<String, String> WORD_ALIAS = Map.ofEntries(
            Map.entry("battery", "续航"),
            Map.entry("bluetooth", "蓝牙连接"),
            Map.entry("noise-canceling", "降噪"),
            Map.entry("comfort", "舒适"),
            Map.entry("microphone", "通话收音")
    );
    private static final Set<String> WORD_DIMENSION_TERMS = Set.of(
            "续航", "电池", "蓝牙", "蓝牙连接", "连接", "降噪", "通透", "舒适", "佩戴", "通话", "通话收音",
            "麦克风", "音质", "音效", "低音", "高音", "物流", "售后", "包装", "客服"
    );
    private static final Set<String> WORD_ISSUE_TERMS = Set.of(
            "断连", "断开", "卡顿", "延迟", "漏音", "杂音", "噪音", "掉电", "耗电", "发热", "刺耳", "不稳",
            "不适", "压耳", "夹耳", "失灵", "坏了", "闷", "糊", "差评", "故障"
    );
    private static final Set<String> WORD_POSITIVE_TERMS = Set.of(
            "稳定", "舒适", "清晰", "满意", "好用", "不错", "漂亮", "轻便", "流畅", "推荐", "方便", "精致"
    );
    private static final Set<String> WORD_ACTION_TERMS = Set.of(
            "连接", "断开", "断连", "佩戴", "充电", "收音", "降噪", "切换", "更新", "检测", "维修", "退换"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightQueryService.class);

    private final ActionService actionService;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final DataQualityRepository dataQualityRepository;
    private final ProductRepository productRepository;
    private final ReviewQueryRepository reviewQueryRepository;
    private final ReviewSemanticLabelRepository reviewSemanticLabelRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final ReviewAggregationService reviewAggregationService;
    private final ValidationMetricsRepository validationMetricsRepository;
    private final SimpleCache<String, List<ReviewAggregationService.AggregatedReview>> reviewsCache =
            new SimpleCache<>(10 * 60 * 1000); // 10 minutes TTL

    public InsightQueryService(
            ActionService actionService,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            DataQualityRepository dataQualityRepository,
            ProductRepository productRepository,
            ReviewQueryRepository reviewQueryRepository,
            ReviewSemanticLabelRepository reviewSemanticLabelRepository,
            TaxonomyRepository taxonomyRepository,
            ReviewAggregationService reviewAggregationService,
            ValidationMetricsRepository validationMetricsRepository
    ) {
        this.actionService = actionService;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.dataQualityRepository = dataQualityRepository;
        this.productRepository = productRepository;
        this.reviewQueryRepository = reviewQueryRepository;
        this.reviewSemanticLabelRepository = reviewSemanticLabelRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.reviewAggregationService = reviewAggregationService;
        this.validationMetricsRepository = validationMetricsRepository;
    }

    public IssueListResponse listIssues(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        try {
            List<MaterializedIssueRecord> records = analysisMaterializationRepository.findIssues(normalizedProductCode);
            if (records.isEmpty() && !analysisMaterializationRepository.hasMaterializedOutputs(normalizedProductCode)) {
                return new IssueListResponse(List.of(), STATE_EMPTY, emptyAnalysisNotice(normalizedProductCode));
            }

            List<IssueItem> items = records.stream()
                    .filter(record -> !TaxonomyService.FALLBACK_SECONDARY_LABEL.equals(
                            labelOrAspect(record.uxSecondaryLabel(), TaxonomyService.FALLBACK_SECONDARY_LABEL)
                    ))
                    .filter(record -> record.negativeRate() > 0D)
                    .map(record -> new IssueItem(
                            buildIssueId(labelOrAspect(record.uxSecondaryLabel(), record.aspect()), record.clusterId()),
                            record.title(),
                            record.aspect(),
                            labelOrAspect(record.uxPrimaryLabel(), TaxonomyService.FALLBACK_PRIMARY_LABEL),
                            labelOrAspect(record.uxSecondaryLabel(), record.aspect()),
                            roundTo4(record.priorityScore()),
                            buildIssueEvidenceSummary(
                                    labelOrAspect(record.uxSecondaryLabel(), reviewAggregationService.aspectDisplayName(record.aspect())),
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
        String productName = resolveProductName(normalizedProductCode);
        String comparisonProductName = normalizedComparisonProductCode == null
                ? null
                : resolveProductName(normalizedComparisonProductCode);

        if (normalizedComparisonProductCode == null) {
            return new CompareResponse(
                    normalizedProductCode,
                    productName,
                    null,
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
                        productName,
                        normalizedComparisonProductCode,
                        comparisonProductName,
                        COMPARE_STATE_PRIMARY_UNAVAILABLE,
                        COMPARE_PRIMARY_UNAVAILABLE_NOTICE,
                        List.of()
                );
            }
            if (!analysisMaterializationRepository.hasMaterializedOutputs(normalizedComparisonProductCode)) {
                return new CompareResponse(
                        normalizedProductCode,
                        productName,
                        normalizedComparisonProductCode,
                        comparisonProductName,
                        COMPARE_STATE_COMPARISON_UNAVAILABLE,
                        COMPARE_COMPARISON_UNAVAILABLE_NOTICE,
                        List.of()
                );
            }
            if (!hasMatchingTaxonomy(normalizedProductCode, normalizedComparisonProductCode)) {
                return new CompareResponse(
                        normalizedProductCode,
                        productName,
                        normalizedComparisonProductCode,
                        comparisonProductName,
                        COMPARE_STATE_TAXONOMY_MISMATCH,
                        COMPARE_TAXONOMY_MISMATCH_NOTICE,
                        List.of()
                );
            }

            Map<String, MaterializedCompareAspectRecord> primaryRecords = toCompareRecordMap(
                    analysisMaterializationRepository.findCompareAspectScores(normalizedProductCode)
            );
            Map<String, MaterializedCompareAspectRecord> comparisonRecords = toCompareRecordMap(
                    analysisMaterializationRepository.findCompareAspectScores(normalizedComparisonProductCode)
            );
            Map<String, Double> primaryScores = toUxScoreMap(primaryRecords);
            Map<String, Double> comparisonScores = toUxScoreMap(comparisonRecords);

            LinkedHashSet<String> labels = new LinkedHashSet<>();
            labels.addAll(primaryScores.keySet());
            labels.addAll(comparisonScores.keySet());
            List<CompareItem> items = labels.stream()
                    .map(label -> {
                        MaterializedCompareAspectRecord record = primaryRecords.getOrDefault(label, comparisonRecords.get(label));
                        double ourScore = primaryScores.getOrDefault(label, 0D);
                        double competitorScore = comparisonScores.getOrDefault(label, 0D);
                        int ourMentionCount = primaryRecords.containsKey(label) ? primaryRecords.get(label).mentionCount() : 0;
                        int competitorMentionCount = comparisonRecords.containsKey(label) ? comparisonRecords.get(label).mentionCount() : 0;
                        double ourNegativeRate = negativeRate(primaryRecords.get(label));
                        double competitorNegativeRate = negativeRate(comparisonRecords.get(label));
                        return new CompareItem(
                                record == null ? ReviewAggregationService.ASPECT_UNKNOWN : record.aspect(),
                                record == null ? "" : record.uxPrimaryLabel(),
                                label,
                                roundTo4(ourScore),
                                roundTo4(competitorScore),
                                roundTo4(ourScore - competitorScore),
                                ourMentionCount,
                                competitorMentionCount,
                                ourNegativeRate,
                                competitorNegativeRate,
                                roundTo4(ourNegativeRate - competitorNegativeRate)
                        );
                    })
                    .toList();

            return new CompareResponse(
                    normalizedProductCode,
                    productName,
                    normalizedComparisonProductCode,
                    comparisonProductName,
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
                    productName,
                    normalizedComparisonProductCode,
                    comparisonProductName,
                    COMPARE_STATE_ERROR,
                    QUERY_FAILURE_NOTICE,
                    List.of()
            );
        }
    }

    public TrendResponse trends(String productCode, String aspect) {
        return trends(productCode, aspect, null);
    }

    public TrendResponse trends(String productCode, String aspect, String uxSecondaryLabel) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = reviewAggregationService.normalizeTrendAspect(aspect);
        String normalizedLabel = normalizeUxFilter(uxSecondaryLabel, normalizedAspect);

        try {
            List<MaterializedTrendReviewRecord> reviews = analysisMaterializationRepository.findTrendReviews(
                    normalizedProductCode,
                    normalizedLabel
            );
            if (reviews.isEmpty()) {
                return new TrendResponse(
                        normalizedProductCode,
                        normalizedAspect,
                        normalizedLabel,
                        List.of(),
                        STATE_EMPTY,
                        emptyAnalysisNotice(normalizedProductCode)
                );
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

            return new TrendResponse(normalizedProductCode, normalizedAspect, normalizedLabel, points, STATE_SUCCESS, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to load materialized trend data, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new TrendResponse(normalizedProductCode, normalizedAspect, normalizedLabel, List.of(), STATE_ERROR, QUERY_FAILURE_NOTICE);
        }
    }

    public WordCloudResponse wordCloud(String productCode, String aspect) {
        return wordCloud(productCode, aspect, null);
    }

    public WordCloudResponse wordCloud(String productCode, String aspect, String uxSecondaryLabel) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = reviewAggregationService.normalizeWordCloudAspect(aspect);
        String normalizedLabel = ReviewAggregationService.ASPECT_ALL.equals(normalizedAspect)
                ? normalizeUxFilter(uxSecondaryLabel, ReviewAggregationService.ASPECT_ALL)
                : normalizeUxFilter(uxSecondaryLabel, normalizedAspect);

        try {
            String scopedAspect = ReviewAggregationService.ASPECT_ALL.equals(normalizedLabel)
                    ? null
                    : normalizedLabel;
            List<MaterializedWordCloudReviewRecord> reviews = analysisMaterializationRepository.findWordCloudReviews(
                    normalizedProductCode,
                    scopedAspect
            );
            if (reviews.isEmpty()) {
                return new WordCloudResponse(
                        normalizedProductCode,
                        normalizedAspect,
                        normalizedLabel,
                        List.of(),
                        STATE_EMPTY,
                        emptyAnalysisNotice(normalizedProductCode)
                );
            }

            Map<ReviewAggregationService.Sentiment, Map<String, KeywordStats>> keywordStatsBySentiment =
                    new EnumMap<>(ReviewAggregationService.Sentiment.class);
            keywordStatsBySentiment.put(ReviewAggregationService.Sentiment.NEGATIVE, new HashMap<>());
            keywordStatsBySentiment.put(ReviewAggregationService.Sentiment.POSITIVE, new HashMap<>());
            keywordStatsBySentiment.put(ReviewAggregationService.Sentiment.NEUTRAL, new HashMap<>());

            for (MaterializedWordCloudReviewRecord review : reviews) {
                ReviewAggregationService.Sentiment sentiment = normalizeSentiment(review.sentimentPolarity());
                Map<String, KeywordStats> keywordStats = keywordStatsBySentiment.get(sentiment);
                Set<String> keywordsInReview = new LinkedHashSet<>();
                for (String sourceText : wordCloudSourceTexts(review)) {
                    Matcher matcher = WORD_TOKEN_PATTERN.matcher(sourceText);
                    while (matcher.find()) {
                        String keyword = normalizeWordCloudKeyword(matcher.group());
                        if (keyword == null) {
                            continue;
                        }
                        for (String expandedKeyword : expandWordCloudKeyword(keyword, sentiment)) {
                            if (!shouldSkipWordCloudKeyword(expandedKeyword, sentiment)) {
                                keywordsInReview.add(expandedKeyword);
                            }
                        }
                    }
                }
                for (String keyword : keywordsInReview) {
                    KeywordStats stats = keywordStats.computeIfAbsent(keyword, key -> new KeywordStats());
                    stats.add(sentiment);
                }
            }

            List<KeywordCandidate> candidates = balancedWordCloudCandidates(keywordStatsBySentiment);

            if (candidates.isEmpty()) {
                return new WordCloudResponse(
                        normalizedProductCode,
                        normalizedAspect,
                        normalizedLabel,
                        List.of(),
                        STATE_DEGRADED,
                        "当前评论文本暂未提取到可展示关键词，请稍后重试。"
                );
            }

            int maxFrequency = candidates.stream()
                    .mapToInt(KeywordCandidate::frequency)
                    .max()
                    .orElse(1);

            List<WordCloudItem> items = candidates.stream()
                    .map(candidate -> new WordCloudItem(
                            candidate.keyword(),
                            candidate.frequency(),
                            roundTo4((double) candidate.frequency() / maxFrequency),
                            candidate.sentimentTag(),
                            inferPartOfSpeech(candidate.keyword()),
                            inferWordType(candidate.keyword())
                    ))
                    .toList();

            return new WordCloudResponse(normalizedProductCode, normalizedAspect, normalizedLabel, items, STATE_SUCCESS, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to load materialized wordcloud data, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new WordCloudResponse(normalizedProductCode, normalizedAspect, normalizedLabel, List.of(), STATE_ERROR, QUERY_FAILURE_NOTICE);
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
                    run.placeholderContentCount(),
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
                        positiveInsightEmptyNotice(normalizedProductCode),
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
                aggregate.uxSecondaryLabel(),
                3
        ).stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .limit(3)
                .toList();
        String sellingPoint = normalizeSellingPoint(aggregate.standardizedReason(), aggregate.uxSecondaryLabel());
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

    private String emptyAnalysisNotice(String productCode) {
        return reviewQueryRepository.countByProductCode(productCode) > 0 ? ANALYSIS_NOT_READY_NOTICE : NO_DATA_NOTICE;
    }

    private String positiveInsightEmptyNotice(String productCode) {
        if (reviewQueryRepository.countByProductCode(productCode) <= 0) {
            return NO_DATA_NOTICE;
        }
        if (analysisMaterializationRepository.hasMaterializedOutputs(productCode)) {
            return "LLM 分析已完成，但当前没有足够的正向 UX 标签可提炼卖点。";
        }
        return ANALYSIS_NOT_READY_NOTICE;
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
        return labelOrAspect(aspect, "正面体验") + "表现稳定";
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
            String uxSecondaryLabel = resolveUxSecondaryLabel(context);
            String aspectName = uxSecondaryLabel == null
                    ? aspect == null ? "综合问题" : reviewAggregationService.aspectDisplayName(aspect)
                    : uxSecondaryLabel;
            List<ReviewAggregationService.AggregatedReview> scopedReviews =
                    loadValidationReviews(productCode, uxSecondaryLabel, aspect);
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

    private String resolveUxSecondaryLabel(ActionValidationContext context) {
        if (context.uxSecondaryLabel() != null && !context.uxSecondaryLabel().isBlank()) {
            return context.uxSecondaryLabel().trim();
        }
        return resolveUxSecondaryLabelByIssueId(context.action().issueId());
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

    private String resolveUxSecondaryLabelByIssueId(String issueId) {
        if (issueId == null || issueId.isBlank() || !issueId.startsWith("iss-")) {
            return null;
        }
        int lastDash = issueId.lastIndexOf('-');
        if (lastDash <= "iss-".length()) {
            return null;
        }
        String rawLabel = issueId.substring("iss-".length(), lastDash)
                .replace('_', '-')
                .trim();
        if (rawLabel.isBlank() || !ReviewAggregationService.ASPECT_UNKNOWN.equals(reviewAggregationService.normalizeAspect(rawLabel))) {
            return null;
        }
        return rawLabel;
    }

    private List<ReviewAggregationService.AggregatedReview> loadValidationReviews(
            String productCode,
            String uxSecondaryLabel,
            String aspect
    ) {
        List<MaterializedValidationReviewRecord> materializedReviews =
                analysisMaterializationRepository.findValidationReviews(productCode, uxSecondaryLabel, aspect);
        if (materializedReviews != null && !materializedReviews.isEmpty()) {
            return materializedReviews.stream()
                    .map(review -> new ReviewAggregationService.AggregatedReview(
                            review.reviewId(),
                            review.productCode(),
                            review.aspect(),
                            review.content(),
                            review.reviewTime(),
                            normalizeSentiment(review.sentimentPolarity())
                    ))
                    .toList();
        }
        List<ReviewAggregationService.AggregatedReview> allReviews = loadReviewsWithCache(productCode);
        return reviewAggregationService.filterByAspect(allReviews, aspect);
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

    private List<String> wordCloudSourceTexts(MaterializedWordCloudReviewRecord review) {
        List<String> sourceTexts = new ArrayList<>();
        addWordCloudSourceText(sourceTexts, review.standardizedReason());
        addWordCloudSourceText(sourceTexts, review.evidence());
        if (sourceTexts.isEmpty()) {
            addWordCloudSourceText(sourceTexts, review.content());
        }
        return sourceTexts;
    }

    private void addWordCloudSourceText(List<String> sourceTexts, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank() || TaxonomyService.FALLBACK_SECONDARY_LABEL.equals(normalized)) {
            return;
        }
        String stripped = stripWordCloudNoiseTerms(normalized);
        if (!stripped.isBlank()) {
            sourceTexts.add(stripped);
        }
    }

    private String stripWordCloudNoiseTerms(String value) {
        String stripped = value;
        for (String noiseTerm : WORD_TEXT_NOISE_TERMS) {
            stripped = stripped.replace(noiseTerm, " ");
        }
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private List<String> expandWordCloudKeyword(String keyword, ReviewAggregationService.Sentiment sentiment) {
        if (!isLongChinesePhrase(keyword)) {
            return List.of(keyword);
        }

        Set<String> extracted = new LinkedHashSet<>();
        for (String term : WORD_DIMENSION_TERMS) {
            if (keyword.contains(term)) {
                extracted.add(term);
            }
        }
        Set<String> sentimentTerms = sentiment == ReviewAggregationService.Sentiment.NEGATIVE
                ? WORD_ISSUE_TERMS
                : WORD_POSITIVE_TERMS;
        for (String term : sentimentTerms) {
            if (keyword.contains(term)) {
                extracted.add(term);
            }
        }
        return List.copyOf(extracted);
    }

    private boolean shouldSkipWordCloudKeyword(String keyword, ReviewAggregationService.Sentiment sentiment) {
        if (keyword == null || keyword.isBlank() || WORD_STOP_WORDS.contains(keyword)) {
            return true;
        }
        if (keyword.matches("[a-z][a-z\\-]+") && !WORD_ALLOWED_ENGLISH_TERMS.contains(keyword)) {
            return true;
        }
        if (WORD_PHRASE_NOISE_MARKERS.stream().anyMatch(keyword::contains)) {
            return true;
        }
        if (
                sentiment == ReviewAggregationService.Sentiment.NEGATIVE
                        && containsAny(keyword, WORD_POSITIVE_TERMS)
                        && !containsAny(keyword, WORD_ISSUE_TERMS)
        ) {
            return true;
        }
        return sentiment == ReviewAggregationService.Sentiment.POSITIVE
                && containsAny(keyword, WORD_ISSUE_TERMS)
                && !containsAny(keyword, WORD_POSITIVE_TERMS);
    }

    private boolean containsAny(String keyword, Set<String> terms) {
        return terms.stream().anyMatch(keyword::contains);
    }

    private boolean isLongChinesePhrase(String keyword) {
        return keyword != null && keyword.length() > 6 && keyword.matches("[\\p{IsHan}]+");
    }

    private List<KeywordCandidate> balancedWordCloudCandidates(
            Map<ReviewAggregationService.Sentiment, Map<String, KeywordStats>> keywordStatsBySentiment
    ) {
        List<KeywordCandidate> negativeCandidates = new ArrayList<>();
        List<KeywordCandidate> positiveCandidates = new ArrayList<>();
        Set<String> selectedKeywords = new LinkedHashSet<>();

        appendTopCandidates(
                keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.NEGATIVE, Map.of()),
                "负向",
                WORD_CLOUD_SENTIMENT_BUCKET_SIZE,
                selectedKeywords,
                negativeCandidates
        );
        appendTopCandidates(
                keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.POSITIVE, Map.of()),
                "正向",
                WORD_CLOUD_SENTIMENT_BUCKET_SIZE,
                selectedKeywords,
                positiveCandidates
        );

        int remaining = WORD_CLOUD_TOP_N - negativeCandidates.size() - positiveCandidates.size();
        if (remaining > 0 && negativeCandidates.size() < WORD_CLOUD_SENTIMENT_BUCKET_SIZE) {
            int before = positiveCandidates.size();
            appendTopCandidates(
                    keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.POSITIVE, Map.of()),
                    "正向",
                    remaining,
                    selectedKeywords,
                    positiveCandidates
            );
            remaining -= positiveCandidates.size() - before;
        }
        if (remaining > 0 && positiveCandidates.size() < WORD_CLOUD_SENTIMENT_BUCKET_SIZE) {
            int before = negativeCandidates.size();
            appendTopCandidates(
                    keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.NEGATIVE, Map.of()),
                    "负向",
                    remaining,
                    selectedKeywords,
                    negativeCandidates
            );
            remaining -= negativeCandidates.size() - before;
        }
        if (remaining > 0) {
            int before = negativeCandidates.size();
            appendTopCandidates(
                    keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.NEGATIVE, Map.of()),
                    "负向",
                    remaining,
                    selectedKeywords,
                    negativeCandidates
            );
            remaining -= negativeCandidates.size() - before;
        }
        if (remaining > 0) {
            appendTopCandidates(
                    keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.POSITIVE, Map.of()),
                    "正向",
                    remaining,
                    selectedKeywords,
                    positiveCandidates
            );
        }

        List<KeywordCandidate> candidates = interleaveCandidates(negativeCandidates, positiveCandidates);
        if (!candidates.isEmpty()) {
            return candidates;
        }

        List<KeywordCandidate> neutralCandidates = new ArrayList<>();
        appendTopCandidates(
                keywordStatsBySentiment.getOrDefault(ReviewAggregationService.Sentiment.NEUTRAL, Map.of()),
                "中性",
                WORD_CLOUD_TOP_N,
                new LinkedHashSet<>(),
                neutralCandidates
        );
        return neutralCandidates;
    }

    private void appendTopCandidates(
            Map<String, KeywordStats> keywordStats,
            String sentimentTag,
            int limit,
            Set<String> selectedKeywords,
            List<KeywordCandidate> target
    ) {
        if (limit <= 0 || keywordStats.isEmpty()) {
            return;
        }
        keywordStats.entrySet().stream()
                .filter(entry -> !selectedKeywords.contains(entry.getKey()))
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, KeywordStats> entry) -> entry.getValue().frequency())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .forEach(entry -> {
                    selectedKeywords.add(entry.getKey());
                    target.add(new KeywordCandidate(entry.getKey(), entry.getValue().frequency(), sentimentTag));
                });
    }

    private List<KeywordCandidate> interleaveCandidates(
            List<KeywordCandidate> negativeCandidates,
            List<KeywordCandidate> positiveCandidates
    ) {
        List<KeywordCandidate> candidates = new ArrayList<>(negativeCandidates.size() + positiveCandidates.size());
        int maxSize = Math.max(negativeCandidates.size(), positiveCandidates.size());
        for (int index = 0; index < maxSize; index++) {
            if (index < negativeCandidates.size()) {
                candidates.add(negativeCandidates.get(index));
            }
            if (index < positiveCandidates.size()) {
                candidates.add(positiveCandidates.get(index));
            }
        }
        return candidates;
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

    private String inferPartOfSpeech(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "名词";
        }
        if (normalized.matches("[a-z][a-z\\-]+")) {
            return "英文词";
        }
        if (WORD_POSITIVE_TERMS.contains(normalized)) {
            return "形容词";
        }
        if (WORD_ACTION_TERMS.contains(normalized) || WORD_ISSUE_TERMS.contains(normalized)) {
            return "动词";
        }
        return "名词";
    }

    private String inferWordType(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "商品属性";
        }
        if (WORD_ISSUE_TERMS.contains(normalized)) {
            return "问题词";
        }
        if (WORD_POSITIVE_TERMS.contains(normalized)) {
            return "正向评价词";
        }
        if (WORD_DIMENSION_TERMS.contains(normalized)) {
            return "体验维度";
        }
        if (normalized.matches("[a-z][a-z\\-]+")) {
            return "英文词";
        }
        return "商品属性";
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

    private Map<String, MaterializedCompareAspectRecord> toCompareRecordMap(List<MaterializedCompareAspectRecord> records) {
        Map<String, MaterializedCompareAspectRecord> recordMap = new LinkedHashMap<>();
        for (MaterializedCompareAspectRecord record : records) {
            String label = labelOrAspect(record.uxSecondaryLabel(), record.aspect());
            if (record.mentionCount() <= 0 || TaxonomyService.FALLBACK_SECONDARY_LABEL.equals(label)) {
                continue;
            }
            recordMap.put(label, record);
        }
        return recordMap;
    }

    private Map<String, Double> toUxScoreMap(Map<String, MaterializedCompareAspectRecord> records) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, MaterializedCompareAspectRecord> entry : records.entrySet()) {
            scores.put(entry.getKey(), clamp01(entry.getValue().avgSentimentScore()));
        }
        return scores;
    }

    private boolean hasMatchingTaxonomy(String productCode, String comparisonProductCode) {
        Optional<TaxonomyResponse> primaryTaxonomy = taxonomyRepository.findBoundForProduct(productCode);
        Optional<TaxonomyResponse> comparisonTaxonomy = taxonomyRepository.findBoundForProduct(comparisonProductCode);
        if (primaryTaxonomy.isEmpty() || comparisonTaxonomy.isEmpty()) {
            return false;
        }
        TaxonomyResponse primary = primaryTaxonomy.get();
        TaxonomyResponse comparison = comparisonTaxonomy.get();
        if (primary.taxonomyId() == comparison.taxonomyId()) {
            return true;
        }
        String primarySignature = taxonomySignature(primary);
        String comparisonSignature = taxonomySignature(comparison);
        return !primarySignature.isBlank() && primarySignature.equals(comparisonSignature);
    }

    private String taxonomySignature(TaxonomyResponse taxonomy) {
        if (taxonomy == null || taxonomy.primaryLabels() == null || taxonomy.primaryLabels().isEmpty()) {
            return "";
        }
        List<String> labelSignatures = taxonomy.primaryLabels().stream()
                .filter(primary -> primary != null && hasText(primary.labelName()))
                .flatMap(primary -> primary.secondaryLabels() == null
                        ? java.util.stream.Stream.<String>empty()
                        : primary.secondaryLabels().stream()
                                .filter(secondary -> secondary != null && hasText(secondary.labelName()))
                                .map(secondary -> taxonomyLabelSignature(primary, secondary)))
                .sorted()
                .toList();
        if (labelSignatures.isEmpty()) {
            return "";
        }
        return normalizeSignaturePart(taxonomy.productCategory()) + "\n" + String.join("\n", labelSignatures);
    }

    private String taxonomyLabelSignature(UxPrimaryLabelResponse primary, UxSecondaryLabelResponse secondary) {
        String synonyms = secondary.synonyms() == null
                ? ""
                : secondary.synonyms().stream()
                        .filter(this::hasText)
                        .map(this::normalizeSignaturePart)
                        .sorted()
                        .collect(Collectors.joining(","));
        return String.join("|",
                normalizeSignaturePart(primary.labelName()),
                normalizeSignaturePart(secondary.labelName()),
                Boolean.toString(secondary.enabled()),
                synonyms,
                normalizeSignaturePart(secondary.description())
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeSignaturePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveProductName(String productCode) {
        try {
            Optional<String> productName = productRepository.findProductName(productCode);
            return productName == null
                    ? null
                    : productName.filter(name -> !productCode.equals(name)).orElse(null);
        } catch (Exception ex) {
            LOGGER.debug("failed to resolve product display name, productCode={}", productCode, ex);
            return null;
        }
    }

    private double negativeRate(MaterializedCompareAspectRecord record) {
        if (record == null || record.mentionCount() <= 0) {
            return 0D;
        }
        return roundTo4((double) record.negativeCount() / record.mentionCount());
    }

    private String normalizeUxFilter(String uxSecondaryLabel, String fallbackAspect) {
        if (uxSecondaryLabel != null && !uxSecondaryLabel.isBlank()) {
            return uxSecondaryLabel.trim();
        }
        return switch (fallbackAspect == null ? "" : fallbackAspect) {
            case "battery" -> "电池与续航";
            case "bluetooth" -> "连接与稳定性";
            case "noise-canceling" -> "降噪与通透";
            case "comfort" -> "佩戴与人体工学";
            case "microphone" -> "麦克风与通话";
            case ReviewAggregationService.ASPECT_ALL -> ReviewAggregationService.ASPECT_ALL;
            default -> TaxonomyService.FALLBACK_SECONDARY_LABEL;
        };
    }

    private String labelOrAspect(String label, String fallback) {
        if (label == null || label.isBlank()) {
            return fallback == null || fallback.isBlank() ? TaxonomyService.FALLBACK_SECONDARY_LABEL : fallback;
        }
        return label.trim();
    }

    private record KeywordCandidate(String keyword, int frequency, String sentimentTag) {
    }

    private static final class KeywordStats {
        private int frequency;
        private int positiveCount;
        private int neutralCount;
        private int negativeCount;

        private void add(ReviewAggregationService.Sentiment sentiment) {
            frequency++;
            switch (sentiment) {
                case POSITIVE -> positiveCount++;
                case NEGATIVE -> negativeCount++;
                case NEUTRAL -> neutralCount++;
            }
        }

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
