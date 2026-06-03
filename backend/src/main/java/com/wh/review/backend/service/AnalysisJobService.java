package com.wh.review.backend.service;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.IssueClusterRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.Materialization;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.ReviewAspectRecord;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import com.wh.review.backend.persistence.ReviewSemanticLabelRepository.SemanticLabelRecord;
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
    private final ReviewAggregationService reviewAggregationService;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final NlpReviewAnalysisClient nlpReviewAnalysisClient;

    public AnalysisJobService(
            AnalysisJobRepository analysisJobRepository,
            ReviewAggregationService reviewAggregationService,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            NlpReviewAnalysisClient nlpReviewAnalysisClient
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.reviewAggregationService = reviewAggregationService;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.nlpReviewAnalysisClient = nlpReviewAnalysisClient;
    }

    public AnalysisJobResponse createJob(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        if (normalizedProductCode == null || normalizedProductCode.isBlank()) {
            normalizedProductCode = productCode == null ? "" : productCode.trim();
        }
        Optional<AnalysisJobResponse> reusableJob = findReusableJob(normalizedProductCode);
        if (reusableJob.isPresent()) {
            return reusableJob.get();
        }

        AnalysisJobResponse queuedJob = analysisJobRepository.create(normalizedProductCode, STATUS_QUEUED, Instant.now());
        AnalysisJobResponse runningJob = analysisJobRepository.markRunning(queuedJob.jobId());

        try {
            List<ReviewAggregationService.AggregatedReview> reviews =
                    reviewAggregationService.loadReviews(normalizedProductCode);
            if (reviews.isEmpty()) {
                throw new IllegalStateException("no reviews found for productCode=" + normalizedProductCode);
            }
            NlpReviewAnalysisClient.AnalyzeResult nlpResult = nlpReviewAnalysisClient.analyze(
                    runningJob.jobId(),
                    normalizedProductCode,
                    reviews.stream().map(ReviewAggregationService.AggregatedReview::content).toList()
            );
            AnalysisExecution execution = buildAnalysisExecution(reviews, nlpResult);
            analysisMaterializationRepository.replaceOutputs(
                    normalizedProductCode,
                    buildMaterialization(execution.reviews())
            );
            return analysisJobRepository.markSucceeded(runningJob.jobId(), Instant.now(), execution.degradedMessage());
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

    private AnalysisExecution buildAnalysisExecution(
            List<ReviewAggregationService.AggregatedReview> sourceReviews,
            NlpReviewAnalysisClient.AnalyzeResult nlpResult
    ) {
        if (!nlpResult.isSuccess()) {
            return new AnalysisExecution(localFallbackAnalysis(sourceReviews), nlpResult.degradedMessage());
        }

        try {
            return new AnalysisExecution(nlpAnalysis(sourceReviews, nlpResult.response()), nlpResult.degradedMessage());
        } catch (IllegalStateException ex) {
            return new AnalysisExecution(
                    localFallbackAnalysis(sourceReviews),
                    "degraded:nlp_invalid_response:" + sanitizeContractMessage(ex.getMessage())
            );
        }
    }

    private List<AnalyzedReview> localFallbackAnalysis(List<ReviewAggregationService.AggregatedReview> sourceReviews) {
        return sourceReviews.stream()
                .map(review -> new AnalyzedReview(
                        review.reviewId(),
                        review.aspect(),
                        review.content(),
                        review.reviewTime(),
                        sentimentPolarity(review.sentiment()),
                        sentimentScore(review.sentiment()),
                        CONFIDENCE_DEFAULT,
                        defaultUxPrimaryLabel(review.aspect()),
                        defaultUxSecondaryLabel(review.aspect()),
                        defaultStandardizedReason(review.aspect(), sentimentPolarity(review.sentiment())),
                        evidence(review.content()),
                        defaultNegativeIntensityScore(sentimentPolarity(review.sentiment()))
                ))
                .toList();
    }

    private List<AnalyzedReview> nlpAnalysis(
            List<ReviewAggregationService.AggregatedReview> sourceReviews,
            NlpReviewAnalysisClient.AnalyzeResponse response
    ) {
        List<AnalyzedReview> analyzedReviews = new ArrayList<>(sourceReviews.size());
        for (NlpReviewAnalysisClient.AspectSentiment aspectSentiment : response.aspectSentiments()) {
            ReviewAggregationService.AggregatedReview review = sourceReviews.get(aspectSentiment.reviewIndex());
            String normalizedAspect = normalizeNlpAspect(aspectSentiment.aspect());
            if (ReviewAggregationService.ASPECT_UNKNOWN.equals(normalizedAspect)) {
                throw new IllegalStateException("unsupported nlp aspect=" + aspectSentiment.aspect());
            }
            String polarity = normalizePolarity(aspectSentiment.polarity());
            analyzedReviews.add(new AnalyzedReview(
                    review.reviewId(),
                    normalizedAspect,
                    review.content(),
                    review.reviewTime(),
                    polarity,
                    sentimentScore(polarity),
                    decimal(clampConfidence(aspectSentiment.confidence())),
                    normalizedLabel(aspectSentiment.uxPrimaryLabel(), defaultUxPrimaryLabel(normalizedAspect)),
                    normalizedLabel(aspectSentiment.uxSecondaryLabel(), defaultUxSecondaryLabel(normalizedAspect)),
                    normalizedLabel(aspectSentiment.standardizedReason(), defaultStandardizedReason(normalizedAspect, polarity)),
                    normalizedLabel(aspectSentiment.evidence(), evidence(review.content())),
                    clampNegativeIntensity(aspectSentiment.negativeIntensityScore(), defaultNegativeIntensityScore(polarity))
            ));
        }
        analyzedReviews.sort(Comparator.comparing(AnalyzedReview::reviewTime).thenComparing(AnalyzedReview::reviewId));
        return analyzedReviews;
    }

    private Materialization buildMaterialization(List<AnalyzedReview> reviews) {
        List<ReviewAspectRecord> reviewAspects = reviews.stream()
                .map(review -> new ReviewAspectRecord(
                        review.reviewId(),
                        review.aspect(),
                        review.sentimentPolarity(),
                        review.sentimentScore(),
                        review.confidence()
                ))
                .toList();

        List<SemanticLabelRecord> semanticLabels = reviews.stream()
                .map(review -> new SemanticLabelRecord(
                        review.reviewId(),
                        review.aspect(),
                        review.sentimentPolarity(),
                        review.confidence(),
                        review.uxPrimaryLabel(),
                        review.uxSecondaryLabel(),
                        review.standardizedReason(),
                        review.evidence(),
                        review.negativeIntensityScore()
                ))
                .toList();

        Map<String, List<AnalyzedReview>> groupedByAspect = new HashMap<>();
        for (AnalyzedReview review : reviews) {
            groupedByAspect.computeIfAbsent(review.aspect(), ignored -> new ArrayList<>()).add(review);
        }

        int maxMentionCount = groupedByAspect.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);

        List<IssueClusterRecord> issueClusters = new ArrayList<>();
        for (Map.Entry<String, List<AnalyzedReview>> entry : groupedByAspect.entrySet()) {
            String aspect = entry.getKey();
            if (ReviewAggregationService.ASPECT_UNKNOWN.equals(aspect)) {
                continue;
            }

            List<AnalyzedReview> aspectReviews = entry.getValue().stream()
                    .sorted(Comparator.comparing(AnalyzedReview::reviewTime)
                            .thenComparing(AnalyzedReview::reviewId))
                    .toList();

            int mentionCount = aspectReviews.size();
            long negativeCount = aspectReviews.stream()
                    .filter(review -> SENTIMENT_NEGATIVE.equals(review.sentimentPolarity()))
                    .count();
            if (negativeCount == 0L) {
                continue;
            }

            double negativeRate = round4((double) negativeCount / mentionCount);
            double mentionVolume = round4((double) mentionCount / maxMentionCount);
            int splitPoint = Math.max(1, mentionCount / 2);
            List<AnalyzedReview> previousWindow = aspectReviews.subList(0, splitPoint);
            List<AnalyzedReview> recentWindow = aspectReviews.subList(splitPoint, mentionCount);
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

        return new Materialization(reviewAspects, semanticLabels, issueClusters);
    }

    private String sentimentPolarity(ReviewAggregationService.Sentiment sentiment) {
        return switch (sentiment) {
            case NEGATIVE -> SENTIMENT_NEGATIVE;
            case NEUTRAL -> SENTIMENT_NEUTRAL;
            case POSITIVE -> SENTIMENT_POSITIVE;
        };
    }

    private String normalizePolarity(String polarity) {
        if (polarity == null || polarity.isBlank()) {
            throw new IllegalStateException("nlp polarity is blank");
        }
        return switch (polarity.trim().toUpperCase(java.util.Locale.ROOT)) {
            case SENTIMENT_NEGATIVE -> SENTIMENT_NEGATIVE;
            case SENTIMENT_NEUTRAL -> SENTIMENT_NEUTRAL;
            case SENTIMENT_POSITIVE -> SENTIMENT_POSITIVE;
            default -> throw new IllegalStateException("unsupported nlp polarity=" + polarity);
        };
    }

    private BigDecimal sentimentScore(ReviewAggregationService.Sentiment sentiment) {
        return switch (sentiment) {
            case NEGATIVE -> new BigDecimal("0.1500");
            case NEUTRAL -> new BigDecimal("0.5000");
            case POSITIVE -> new BigDecimal("0.8500");
        };
    }

    private BigDecimal sentimentScore(String sentimentPolarity) {
        return switch (sentimentPolarity) {
            case SENTIMENT_NEGATIVE -> new BigDecimal("0.1500");
            case SENTIMENT_NEUTRAL -> new BigDecimal("0.5000");
            case SENTIMENT_POSITIVE -> new BigDecimal("0.8500");
            default -> throw new IllegalStateException("unsupported sentiment polarity=" + sentimentPolarity);
        };
    }

    private double computeNegativeRate(List<AnalyzedReview> reviews) {
        if (reviews.isEmpty()) {
            return 0D;
        }
        long negativeCount = reviews.stream()
                .filter(review -> SENTIMENT_NEGATIVE.equals(review.sentimentPolarity()))
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

    private String normalizeNlpAspect(String aspect) {
        String normalizedAspect = reviewAggregationService.normalizeAspect(aspect);
        if (normalizedAspect == null || normalizedAspect.isBlank()) {
            String rawAspect = aspect == null ? "" : aspect.trim();
            normalizedAspect = switch (rawAspect) {
                case "battery", "bluetooth", "noise-canceling", "comfort", "microphone" -> rawAspect;
                default -> ReviewAggregationService.ASPECT_UNKNOWN;
            };
        }
        return normalizedAspect;
    }

    private String defaultUxPrimaryLabel(String aspect) {
        return switch (aspect) {
            case "battery", "bluetooth" -> "产品硬件";
            case "noise-canceling", "microphone" -> "声音表现";
            case "comfort" -> "产品体验";
            default -> "无明显问题";
        };
    }

    private String defaultUxSecondaryLabel(String aspect) {
        return switch (aspect) {
            case "battery" -> "电池与续航";
            case "bluetooth" -> "连接与稳定性";
            case "noise-canceling" -> "降噪与通透";
            case "comfort" -> "佩戴与人体工学";
            case "microphone" -> "麦克风与通话";
            default -> "无明显问题";
        };
    }

    private String defaultStandardizedReason(String aspect, String sentimentPolarity) {
        if (SENTIMENT_POSITIVE.equals(sentimentPolarity)) {
            return switch (aspect) {
                case "battery" -> "续航持久";
                case "bluetooth" -> "连接稳定";
                case "noise-canceling" -> "降噪明显";
                case "comfort" -> "佩戴舒适";
                case "microphone" -> "通话清晰";
                default -> "无明显问题";
            };
        }
        if (SENTIMENT_NEGATIVE.equals(sentimentPolarity)) {
            return switch (aspect) {
                case "battery" -> "续航不足";
                case "bluetooth" -> "蓝牙断连";
                case "noise-canceling" -> "降噪不足";
                case "comfort" -> "佩戴不适";
                case "microphone" -> "通话不清晰";
                default -> "综合体验问题";
            };
        }
        return "无明显问题";
    }

    private int defaultNegativeIntensityScore(String sentimentPolarity) {
        return SENTIMENT_NEGATIVE.equals(sentimentPolarity) ? 3 : 1;
    }

    private int clampNegativeIntensity(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(1, Math.min(5, value));
    }

    private String normalizedLabel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String evidence(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    private String keywords(String aspect, List<AnalyzedReview> reviews) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(aspect);
        String displayName = reviewAggregationService.aspectDisplayName(aspect);
        if (displayName != null && !displayName.isBlank()) {
            values.add(displayName);
        }
        for (AnalyzedReview review : reviews) {
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

    private String representativeReviewIds(List<AnalyzedReview> reviews) {
        return reviews.stream()
                .filter(review -> SENTIMENT_NEGATIVE.equals(review.sentimentPolarity()))
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

    private double clampConfidence(Double value) {
        if (value == null) {
            return CONFIDENCE_DEFAULT.doubleValue();
        }
        return clamp01(value);
    }

    private String sanitizeError(String productCode, RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return STATUS_FAILED.toLowerCase() + " analysis for productCode=" + productCode;
        }
        return message;
    }

    private String sanitizeContractMessage(String message) {
        if (message == null || message.isBlank()) {
            return "invalid-response";
        }
        return message.replaceAll("\\s+", "-").toLowerCase(java.util.Locale.ROOT);
    }

    private record AnalysisExecution(List<AnalyzedReview> reviews, String degradedMessage) {
    }

    private record AnalyzedReview(
            long reviewId,
            String aspect,
            String content,
            Instant reviewTime,
            String sentimentPolarity,
            BigDecimal sentimentScore,
            BigDecimal confidence,
            String uxPrimaryLabel,
            String uxSecondaryLabel,
            String standardizedReason,
            String evidence,
            int negativeIntensityScore
    ) {
    }
}
