package com.wh.review.backend.service;

import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.CompareItem;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.IssueItem;
import com.wh.review.backend.dto.TrendPoint;
import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.dto.ValidationItem;
import com.wh.review.backend.dto.ValidationResponse;
import com.wh.review.backend.dto.WordCloudItem;
import com.wh.review.backend.dto.WordCloudResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
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
    private static final String NO_DATA_NOTICE = "当前暂无可分析的演示评论，请先初始化演示数据。";
    private static final String QUERY_FAILURE_NOTICE = "评论洞察正在更新，请稍后刷新重试。";
    private static final String ACTION_NOT_FOUND_NOTICE = "未找到对应改进动作，请确认动作编号。";
    private static final String NO_ACTION_NOTICE = "当前暂无改进动作，请先创建动作后查看验证结果。";
    private static final int WORD_CLOUD_TOP_N = 24;
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z][A-Za-z\\-]{2,}");
    private static final Set<String> WORD_STOP_WORDS = Set.of(
            "批次", "整体", "表现", "体验", "场景", "使用", "连续", "明显", "日常", "需要", "影响", "希望", "优化"
    );
    private static final Map<String, String> WORD_ALIAS = Map.ofEntries(
            Map.entry("battery", "续航"),
            Map.entry("bluetooth", "蓝牙"),
            Map.entry("connectivity", "连接"),
            Map.entry("noise-canceling", "降噪"),
            Map.entry("noise_canceling", "降噪"),
            Map.entry("comfort", "舒适"),
            Map.entry("microphone", "收音")
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightQueryService.class);

    private final ActionService actionService;
    private final DemoReviewAggregationService demoReviewAggregationService;

    public InsightQueryService(ActionService actionService, DemoReviewAggregationService demoReviewAggregationService) {
        this.actionService = actionService;
        this.demoReviewAggregationService = demoReviewAggregationService;
    }

    public List<IssueItem> listIssues(String productCode) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        try {
            List<DemoReviewAggregationService.AggregatedReview> reviews =
                    demoReviewAggregationService.loadReviews(normalizedProductCode);
            if (reviews.isEmpty()) {
                return List.of(buildFallbackIssue("iss-demo-no-data", NO_DATA_NOTICE));
            }

            Map<String, List<DemoReviewAggregationService.AggregatedReview>> groupedByAspect = new HashMap<>();
            for (DemoReviewAggregationService.AggregatedReview review : reviews) {
                groupedByAspect.computeIfAbsent(review.aspect(), key -> new ArrayList<>()).add(review);
            }

            int maxMentionCount = groupedByAspect.values().stream()
                    .mapToInt(List::size)
                    .max()
                    .orElse(1);

            List<IssueItem> items = new ArrayList<>();
            for (Map.Entry<String, List<DemoReviewAggregationService.AggregatedReview>> entry : groupedByAspect.entrySet()) {
                String aspect = entry.getKey();
                if (DemoReviewAggregationService.ASPECT_UNKNOWN.equals(aspect)) {
                    continue;
                }
                IssueFactors factors = buildIssueFactors(aspect, entry.getValue(), maxMentionCount);
                if (factors.negativeRate() <= 0) {
                    continue;
                }
                items.add(new IssueItem(
                        factors.issueId(),
                        factors.title(),
                        factors.aspect(),
                        factors.priorityScore(),
                        factors.evidenceSummary()
                ));
            }

            if (items.isEmpty()) {
                return List.of(buildFallbackIssue("iss-demo-empty", "评论情绪整体平稳，当前未识别到高优先级问题。"));
            }

            items.sort(Comparator.comparingDouble(IssueItem::priorityScore).reversed());
            return items;
        } catch (Exception ex) {
            LOGGER.warn("failed to aggregate issue list from demo reviews, productCode={}", normalizedProductCode, ex);
            return List.of(buildFallbackIssue("iss-demo-fallback", QUERY_FAILURE_NOTICE));
        }
    }

    public CompareResponse compare(String productCode) {
        List<CompareItem> items = List.of(
                new CompareItem("audio", 0.82, 0.78, roundTo4(0.82 - 0.78)),
                new CompareItem("noise_canceling", 0.76, 0.81, roundTo4(0.76 - 0.81)),
                new CompareItem("battery", 0.71, 0.84, roundTo4(0.71 - 0.84)),
                new CompareItem("connectivity", 0.64, 0.80, roundTo4(0.64 - 0.80))
        );
        return new CompareResponse(productCode, items);
    }

    public TrendResponse trends(String productCode, String aspect) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = demoReviewAggregationService.normalizeTrendAspect(aspect);

        try {
            List<DemoReviewAggregationService.AggregatedReview> reviews = demoReviewAggregationService.filterByAspect(
                    demoReviewAggregationService.loadReviews(normalizedProductCode),
                    normalizedAspect
            );
            if (reviews.isEmpty()) {
                return new TrendResponse(normalizedProductCode, normalizedAspect, List.of(), NO_DATA_NOTICE);
            }

            Map<String, PeriodStats> periodStats = new TreeMap<>();
            for (DemoReviewAggregationService.AggregatedReview review : reviews) {
                String period = toIsoWeekPeriod(review.reviewTime());
                PeriodStats stats = periodStats.computeIfAbsent(period, key -> new PeriodStats());
                stats.mentionCount++;
                if (review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE) {
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

            return new TrendResponse(normalizedProductCode, normalizedAspect, points, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to aggregate trend data from demo reviews, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new TrendResponse(normalizedProductCode, normalizedAspect, List.of(), QUERY_FAILURE_NOTICE);
        }
    }

    public WordCloudResponse wordCloud(String productCode, String aspect) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        String normalizedAspect = demoReviewAggregationService.normalizeWordCloudAspect(aspect);

        try {
            List<DemoReviewAggregationService.AggregatedReview> reviews = demoReviewAggregationService.filterByAspect(
                    demoReviewAggregationService.loadReviews(normalizedProductCode),
                    normalizedAspect
            );
            if (reviews.isEmpty()) {
                return new WordCloudResponse(normalizedProductCode, normalizedAspect, List.of(), NO_DATA_NOTICE);
            }

            Map<String, KeywordStats> keywordStats = new HashMap<>();
            for (DemoReviewAggregationService.AggregatedReview review : reviews) {
                Matcher matcher = WORD_TOKEN_PATTERN.matcher(review.content() == null ? "" : review.content());
                while (matcher.find()) {
                    String keyword = normalizeWordCloudKeyword(matcher.group());
                    if (keyword == null || WORD_STOP_WORDS.contains(keyword)) {
                        continue;
                    }
                    KeywordStats stats = keywordStats.computeIfAbsent(keyword, key -> new KeywordStats());
                    stats.frequency++;
                    switch (review.sentiment()) {
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

            return new WordCloudResponse(normalizedProductCode, normalizedAspect, items, null);
        } catch (Exception ex) {
            LOGGER.warn(
                    "failed to aggregate wordcloud data from demo reviews, productCode={}, aspect={}",
                    normalizedProductCode,
                    normalizedAspect,
                    ex
            );
            return new WordCloudResponse(normalizedProductCode, normalizedAspect, List.of(), QUERY_FAILURE_NOTICE);
        }
    }

    public ValidationResponse validation(String actionId) {
        if (actionId != null && !actionId.isBlank()) {
            return actionService.findById(actionId)
                    .map(action -> {
                        ValidationItem item = buildValidationFromAction(action);
                        return new ValidationResponse(List.of(item), null);
                    })
                    .orElseGet(() -> new ValidationResponse(List.of(), ACTION_NOT_FOUND_NOTICE));
        }

        List<ValidationItem> items = actionService.listAll().stream()
                .map(this::buildValidationFromAction)
                .toList();
        if (items.isEmpty()) {
            return new ValidationResponse(List.of(), NO_ACTION_NOTICE);
        }
        return new ValidationResponse(items, null);
    }

    private ValidationItem buildValidationFromAction(ActionResponse action) {
        String productCode = demoReviewAggregationService.normalizeProductCode(action.productCode());
        String aspect = resolveAspectByIssueId(action.issueId());
        String aspectName = aspect == null ? "综合问题" : demoReviewAggregationService.aspectDisplayName(aspect);
        try {
            List<DemoReviewAggregationService.AggregatedReview> reviews =
                    demoReviewAggregationService.loadReviews(productCode);
            List<DemoReviewAggregationService.AggregatedReview> scopedReviews =
                    demoReviewAggregationService.filterByAspect(reviews, aspect);
            if (scopedReviews.size() < 2) {
                return new ValidationItem(
                        action.actionId(),
                        0D,
                        0D,
                        0D,
                        buildValidationInsufficientDataSummary(action, aspectName, scopedReviews.size())
                );
            }

            int splitPoint = Math.max(1, scopedReviews.size() / 2);
            List<DemoReviewAggregationService.AggregatedReview> before = scopedReviews.subList(0, splitPoint);
            List<DemoReviewAggregationService.AggregatedReview> after = scopedReviews.subList(splitPoint, scopedReviews.size());
            if (after.isEmpty()) {
                after = before;
            }

            double beforeNegativeRate = computeNegativeRate(before);
            double afterNegativeRate = computeNegativeRate(after);
            double improvementRate = roundTo4(beforeNegativeRate - afterNegativeRate);
            String summary = buildValidationSummary(
                    action,
                    aspectName,
                    beforeNegativeRate,
                    afterNegativeRate,
                    improvementRate,
                    before.size(),
                    after.size()
            );

            return new ValidationItem(
                    action.actionId(),
                    beforeNegativeRate,
                    afterNegativeRate,
                    improvementRate,
                    summary
            );
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

    private IssueFactors buildIssueFactors(
            String aspect,
            List<DemoReviewAggregationService.AggregatedReview> reviews,
            int maxMentionCount
    ) {
        int mentionCount = reviews.size();
        int negativeCount = (int) reviews.stream()
                .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                .count();

        double negativeRate = mentionCount == 0 ? 0D : (double) negativeCount / mentionCount;
        double mentionVolume = maxMentionCount == 0 ? 0D : (double) mentionCount / maxMentionCount;

        int splitPoint = Math.max(1, mentionCount / 2);
        List<DemoReviewAggregationService.AggregatedReview> previousWindow = reviews.subList(0, splitPoint);
        List<DemoReviewAggregationService.AggregatedReview> recentWindow = reviews.subList(splitPoint, mentionCount);
        if (recentWindow.isEmpty()) {
            recentWindow = previousWindow;
        }
        double previousNegativeRate = computeNegativeRate(previousWindow);
        double recentNegativeRate = computeNegativeRate(recentWindow);
        double trendDelta = recentNegativeRate - previousNegativeRate;
        double trendGrowth = clamp01(Math.max(0D, trendDelta));
        double competitorGap = clamp01(roundTo4(negativeRate * 0.7D + trendGrowth * 0.3D));
        double priorityScore = roundTo4(
                W_NEGATIVE_RATE * negativeRate
                        + W_MENTION_VOLUME * mentionVolume
                        + W_TREND_GROWTH * trendGrowth
                        + W_COMPETITOR_GAP * competitorGap
        );

        String title = resolveIssueTitle(aspect);
        String issueId = "iss-demo-" + aspect.replace('-', '_');
        String evidenceSummary = buildIssueEvidenceSummary(
                demoReviewAggregationService.aspectDisplayName(aspect),
                mentionCount,
                negativeCount,
                negativeRate,
                trendDelta
        );

        return new IssueFactors(
                issueId,
                title,
                aspect,
                priorityScore,
                evidenceSummary,
                negativeRate
        );
    }

    private IssueItem buildFallbackIssue(String issueId, String notice) {
        return new IssueItem(
                issueId,
                "评论洞察数据准备中",
                "general",
                0D,
                notice
        );
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
            int afterSampleCount
    ) {
        String actionName = actionDisplayName(action.actionName());
        double beforePct = roundTo2(beforeNegativeRate * 100);
        double afterPct = roundTo2(afterNegativeRate * 100);
        double deltaPct = roundTo2(Math.abs(improvementRate) * 100);

        if (improvementRate > 0.005D) {
            return "动作「" + actionName + "」在" + aspectName + "维度演示评论中（前"
                    + beforeSampleCount + "条、后" + afterSampleCount + "条），负向占比由 "
                    + beforePct + "% 降至 " + afterPct + "%，改善 " + deltaPct + "%。";
        }
        if (improvementRate < -0.005D) {
            return "动作「" + actionName + "」在" + aspectName + "维度演示评论中（前"
                    + beforeSampleCount + "条、后" + afterSampleCount + "条），负向占比由 "
                    + beforePct + "% 升至 " + afterPct + "%，上升 " + deltaPct + "%，建议继续跟进。";
        }
        return "动作「" + actionName + "」在" + aspectName + "维度演示评论中（前"
                + beforeSampleCount + "条、后" + afterSampleCount + "条），负向占比基本持平（"
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
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    private double roundTo4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private double roundTo2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record IssueFactors(
            String issueId,
            String title,
            String aspect,
            double priorityScore,
            String evidenceSummary,
            double negativeRate
    ) {
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
}
