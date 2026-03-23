package com.wh.review.backend.service;

import com.wh.review.backend.persistence.DemoReviewQueryRepository;
import com.wh.review.backend.persistence.DemoReviewQueryRepository.DemoReviewRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DemoReviewAggregationService {

    public static final String DEFAULT_PRODUCT_CODE = "demo-earphone";
    public static final String DEFAULT_TREND_ASPECT = "battery";
    public static final String ASPECT_ALL = "all";
    public static final String ASPECT_UNKNOWN = "unknown";

    private static final Map<String, AspectMeta> ASPECTS = buildAspects();

    private final DemoReviewQueryRepository demoReviewQueryRepository;

    public DemoReviewAggregationService(DemoReviewQueryRepository demoReviewQueryRepository) {
        this.demoReviewQueryRepository = demoReviewQueryRepository;
    }

    public String normalizeProductCode(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return DEFAULT_PRODUCT_CODE;
        }
        return productCode.trim();
    }

    public String normalizeTrendAspect(String aspect) {
        if (aspect == null || aspect.isBlank()) {
            return DEFAULT_TREND_ASPECT;
        }
        String normalized = normalizeAspectCode(aspect);
        return normalized == null ? DEFAULT_TREND_ASPECT : normalized;
    }

    public String normalizeWordCloudAspect(String aspect) {
        if (aspect == null || aspect.isBlank()) {
            return ASPECT_ALL;
        }
        String normalized = normalizeAspectCode(aspect);
        return normalized == null ? ASPECT_ALL : normalized;
    }

    public String aspectDisplayName(String aspect) {
        AspectMeta meta = ASPECTS.get(aspect);
        if (meta == null) {
            return "其他体验";
        }
        return meta.displayName();
    }

    public List<String> allAspectCodes() {
        return List.copyOf(ASPECTS.keySet());
    }

    public List<AggregatedReview> loadReviews(String productCode) {
        String normalizedProductCode = normalizeProductCode(productCode);
        List<DemoReviewRecord> records = demoReviewQueryRepository.findByProductCode(normalizedProductCode);
        List<AggregatedReview> reviews = new ArrayList<>(records.size());
        for (DemoReviewRecord record : records) {
            String aspect = resolveAspect(record.content());
            Sentiment sentiment = resolveSentiment(record.rating());
            reviews.add(new AggregatedReview(
                    record.reviewId(),
                    normalizedProductCode,
                    aspect,
                    record.content(),
                    record.reviewTime(),
                    sentiment
            ));
        }
        reviews.sort(Comparator.comparing(AggregatedReview::reviewTime).thenComparing(AggregatedReview::reviewId));
        return reviews;
    }

    public List<AggregatedReview> filterByAspect(List<AggregatedReview> reviews, String aspect) {
        if (aspect == null || aspect.isBlank() || ASPECT_ALL.equals(aspect)) {
            return reviews;
        }
        return reviews.stream()
                .filter(review -> aspect.equals(review.aspect()))
                .toList();
    }

    public Sentiment resolveSentiment(BigDecimal rating) {
        if (rating == null) {
            return Sentiment.NEUTRAL;
        }
        if (rating.compareTo(new BigDecimal("2.5")) <= 0) {
            return Sentiment.NEGATIVE;
        }
        if (rating.compareTo(new BigDecimal("3.5")) <= 0) {
            return Sentiment.NEUTRAL;
        }
        return Sentiment.POSITIVE;
    }

    private String resolveAspect(String content) {
        if (content == null || content.isBlank()) {
            return ASPECT_UNKNOWN;
        }
        String lower = content.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, AspectMeta> entry : ASPECTS.entrySet()) {
            for (String keyword : entry.getValue().keywords()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return ASPECT_UNKNOWN;
    }

    private String normalizeAspectCode(String aspect) {
        String normalized = aspect.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "battery", "续航", "电池" -> "battery";
            case "bluetooth", "连接", "connectivity", "连接稳定性" -> "bluetooth";
            case "noise_canceling", "noise-canceling", "noisecanceling", "降噪" -> "noise-canceling";
            case "comfort", "佩戴", "舒适度" -> "comfort";
            case "microphone", "call_quality", "call-quality", "通话", "收音" -> "microphone";
            default -> null;
        };
    }

    private static Map<String, AspectMeta> buildAspects() {
        Map<String, AspectMeta> map = new LinkedHashMap<>();
        map.put("battery", new AspectMeta("续航", List.of("battery", "续航", "电池")));
        map.put("bluetooth", new AspectMeta("蓝牙连接", List.of("bluetooth", "连接稳定")));
        map.put("noise-canceling", new AspectMeta("降噪", List.of("noise-canceling", "降噪")));
        map.put("comfort", new AspectMeta("佩戴舒适", List.of("comfort", "舒适")));
        map.put("microphone", new AspectMeta("通话收音", List.of("microphone", "收音", "语音")));
        return map;
    }

    public record AggregatedReview(
            long reviewId,
            String productCode,
            String aspect,
            String content,
            Instant reviewTime,
            Sentiment sentiment
    ) {
    }

    public enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    private record AspectMeta(String displayName, List<String> keywords) {
    }
}
