package com.wh.review.backend.service;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.IssueClusterRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.Materialization;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.ReviewAspectRecord;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobService {

    /**
     * v1 contract hardening:
     * analysis job 是未来唯一的分析结果物化入口，查询侧应消费 job 产出的结果。
     * 当前实现基于受控演示评论同步执行，并将结果物化到既有分析表中。
     */

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";
    private static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    private static final BigDecimal CONFIDENCE_DEFAULT = new BigDecimal("0.9500");
    private static final double W_NEGATIVE_RATE = 0.35D;
    private static final double W_MENTION_VOLUME = 0.25D;
    private static final double W_TREND_GROWTH = 0.20D;
    private static final double W_COMPETITOR_GAP = 0.20D;

    private final AnalysisJobRepository analysisJobRepository;
    private final DemoReviewAggregationService demoReviewAggregationService;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;

    public AnalysisJobService(
            AnalysisJobRepository analysisJobRepository,
            DemoReviewAggregationService demoReviewAggregationService,
            AnalysisMaterializationRepository analysisMaterializationRepository
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.demoReviewAggregationService = demoReviewAggregationService;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
    }

    public AnalysisJobResponse createJob(String productCode) {
        String normalizedProductCode = demoReviewAggregationService.normalizeProductCode(productCode);
        Optional<AnalysisJobResponse> reusableJob = findReusableJob(normalizedProductCode);
        if (reusableJob.isPresent()) {
            return reusableJob.get();
        }

        AnalysisJobResponse queuedJob = analysisJobRepository.create(normalizedProductCode, STATUS_QUEUED, Instant.now());
        AnalysisJobResponse runningJob = analysisJobRepository.markRunning(queuedJob.jobId());

        try {
            List<DemoReviewAggregationService.AggregatedReview> reviews =
                    demoReviewAggregationService.loadReviews(normalizedProductCode);
            if (reviews.isEmpty()) {
                throw new IllegalStateException("no reviews found for productCode=" + normalizedProductCode);
            }
            analysisMaterializationRepository.replaceOutputs(
                    normalizedProductCode,
                    buildMaterialization(reviews)
            );
            return analysisJobRepository.markSucceeded(runningJob.jobId(), Instant.now());
        } catch (RuntimeException ex) {
            return analysisJobRepository.markFailed(
                    runningJob.jobId(),
                    Instant.now(),
                    sanitizeError(normalizedProductCode, ex)
            );
        }
    }

    public Optional<AnalysisJobResponse> findJob(String jobId) {
        return analysisJobRepository.findById(jobId);
    }

    private Optional<AnalysisJobResponse> findReusableJob(String productCode) {
        Optional<AnalysisJobResponse> latestSucceededJob = analysisJobRepository.findLatestSucceededForProduct(productCode);
        if (latestSucceededJob.isEmpty()) {
            return Optional.empty();
        }

        AnalysisJobResponse job = latestSucceededJob.get();
        if (job.finishedAt() == null) {
            return Optional.empty();
        }

        Optional<Instant> latestSourceUpdateTime = analysisMaterializationRepository.findLatestSourceUpdateTime(productCode);
        if (latestSourceUpdateTime.isEmpty()) {
            return Optional.empty();
        }
        if (latestSourceUpdateTime.get().isAfter(job.finishedAt())) {
            return Optional.empty();
        }
        if (!analysisMaterializationRepository.hasMaterializedOutputs(productCode)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    private Materialization buildMaterialization(List<DemoReviewAggregationService.AggregatedReview> reviews) {
        List<ReviewAspectRecord> reviewAspects = reviews.stream()
                .map(review -> new ReviewAspectRecord(
                        review.reviewId(),
                        review.aspect(),
                        sentimentPolarity(review.sentiment()),
                        sentimentScore(review.sentiment()),
                        CONFIDENCE_DEFAULT
                ))
                .toList();

        Map<String, List<DemoReviewAggregationService.AggregatedReview>> groupedByAspect = new HashMap<>();
        for (DemoReviewAggregationService.AggregatedReview review : reviews) {
            groupedByAspect.computeIfAbsent(review.aspect(), ignored -> new ArrayList<>()).add(review);
        }

        int maxMentionCount = groupedByAspect.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);

        List<IssueClusterRecord> issueClusters = new ArrayList<>();
        for (Map.Entry<String, List<DemoReviewAggregationService.AggregatedReview>> entry : groupedByAspect.entrySet()) {
            String aspect = entry.getKey();
            if (DemoReviewAggregationService.ASPECT_UNKNOWN.equals(aspect)) {
                continue;
            }

            List<DemoReviewAggregationService.AggregatedReview> aspectReviews = entry.getValue().stream()
                    .sorted(Comparator.comparing(DemoReviewAggregationService.AggregatedReview::reviewTime)
                            .thenComparing(DemoReviewAggregationService.AggregatedReview::reviewId))
                    .toList();

            int mentionCount = aspectReviews.size();
            long negativeCount = aspectReviews.stream()
                    .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                    .count();
            if (negativeCount == 0L) {
                continue;
            }

            double negativeRate = round4((double) negativeCount / mentionCount);
            double mentionVolume = round4((double) mentionCount / maxMentionCount);
            int splitPoint = Math.max(1, mentionCount / 2);
            List<DemoReviewAggregationService.AggregatedReview> previousWindow = aspectReviews.subList(0, splitPoint);
            List<DemoReviewAggregationService.AggregatedReview> recentWindow = aspectReviews.subList(splitPoint, mentionCount);
            if (recentWindow.isEmpty()) {
                recentWindow = previousWindow;
            }
            double previousNegativeRate = computeNegativeRate(previousWindow);
            double recentNegativeRate = computeNegativeRate(recentWindow);
            double trendGrowth = clamp01(Math.max(0D, recentNegativeRate - previousNegativeRate));
            double competitorGap = clamp01(round4(negativeRate * 0.7D + trendGrowth * 0.3D));
            double priorityScore = round4(
                    W_NEGATIVE_RATE * negativeRate
                            + W_MENTION_VOLUME * mentionVolume
                            + W_TREND_GROWTH * trendGrowth
                            + W_COMPETITOR_GAP * competitorGap
            );

            issueClusters.add(new IssueClusterRecord(
                    aspect,
                    issueTitle(aspect),
                    keywords(aspect, aspectReviews),
                    representativeReviewIds(aspectReviews),
                    decimal(negativeRate),
                    decimal(negativeRate),
                    decimal(mentionVolume),
                    decimal(trendGrowth),
                    decimal(competitorGap),
                    decimal(priorityScore),
                    weightConfigJson()
            ));
        }

        return new Materialization(reviewAspects, issueClusters);
    }

    private String sentimentPolarity(DemoReviewAggregationService.Sentiment sentiment) {
        return switch (sentiment) {
            case NEGATIVE -> SENTIMENT_NEGATIVE;
            case NEUTRAL -> SENTIMENT_NEUTRAL;
            case POSITIVE -> SENTIMENT_POSITIVE;
        };
    }

    private BigDecimal sentimentScore(DemoReviewAggregationService.Sentiment sentiment) {
        return switch (sentiment) {
            case NEGATIVE -> new BigDecimal("0.1500");
            case NEUTRAL -> new BigDecimal("0.5000");
            case POSITIVE -> new BigDecimal("0.8500");
        };
    }

    private double computeNegativeRate(List<DemoReviewAggregationService.AggregatedReview> reviews) {
        if (reviews.isEmpty()) {
            return 0D;
        }
        long negativeCount = reviews.stream()
                .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                .count();
        return round4((double) negativeCount / reviews.size());
    }

    private String issueTitle(String aspect) {
        return switch (aspect) {
            case "battery" -> "续航体验波动";
            case "bluetooth" -> "蓝牙连接稳定性不足";
            case "noise-canceling" -> "降噪效果一致性不足";
            case "comfort" -> "佩戴舒适度反馈分化";
            case "microphone" -> "通话收音表现待优化";
            default -> "综合体验反馈待优化";
        };
    }

    private String keywords(String aspect, List<DemoReviewAggregationService.AggregatedReview> reviews) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(aspect);
        values.add(demoReviewAggregationService.aspectDisplayName(aspect));
        for (DemoReviewAggregationService.AggregatedReview review : reviews) {
            values.addAll(extractKeywords(review.content()));
            if (values.size() >= 4) {
                break;
            }
        }
        return String.join(",", values.stream().limit(4).toList());
    }

    private List<String> extractKeywords(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return List.of(content.replace('，', ' ').replace('。', ' ').split("\\s+"))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() >= 2)
                .limit(3)
                .toList();
    }

    private String representativeReviewIds(List<DemoReviewAggregationService.AggregatedReview> reviews) {
        return reviews.stream()
                .filter(review -> review.sentiment() == DemoReviewAggregationService.Sentiment.NEGATIVE)
                .limit(3)
                .map(review -> String.valueOf(review.reviewId()))
                .reduce((left, right) -> left + "," + right)
                .orElseGet(() -> reviews.stream()
                        .limit(3)
                        .map(review -> String.valueOf(review.reviewId()))
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""));
    }

    private String weightConfigJson() {
        return "{" +
                "\"negativeRate\":" + decimal(W_NEGATIVE_RATE) + "," +
                "\"mentionVolume\":" + decimal(W_MENTION_VOLUME) + "," +
                "\"trendGrowth\":" + decimal(W_TREND_GROWTH) + "," +
                "\"competitorGap\":" + decimal(W_COMPETITOR_GAP) +
                "}";
    }

    private BigDecimal decimal(double value) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.4f", value));
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private String sanitizeError(String productCode, RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return STATUS_FAILED.toLowerCase() + " analysis for productCode=" + productCode;
        }
        return message;
    }
}
