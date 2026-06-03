package com.wh.reputation.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.persistence.AspectEntity;
import com.wh.reputation.persistence.AspectRepository;
import com.wh.reputation.persistence.ReviewAspectResultEntity;
import com.wh.reputation.persistence.ReviewAspectResultRepository;
import com.wh.reputation.persistence.ReviewEntity;
import com.wh.reputation.persistence.ReviewRepository;
import com.wh.reputation.alert.AlertService;
import com.wh.reputation.decision.SuggestionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ReviewAnalysisService {
    private final ReviewRepository reviewRepository;
    private final AspectRepository aspectRepository;
    private final ReviewAspectResultRepository reviewAspectResultRepository;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final TokenizationService tokenizationService;
    private final TopicAnalysisService topicAnalysisService;
    private final ClusterAnalysisService clusterAnalysisService;
    private final AlertService alertService;
    private final SuggestionService suggestionService;
    private final ObjectMapper objectMapper;

    public ReviewAnalysisService(
            ReviewRepository reviewRepository,
            AspectRepository aspectRepository,
            ReviewAspectResultRepository reviewAspectResultRepository,
            SentimentAnalyzer sentimentAnalyzer,
            TokenizationService tokenizationService,
            TopicAnalysisService topicAnalysisService,
            ClusterAnalysisService clusterAnalysisService,
            AlertService alertService,
            SuggestionService suggestionService,
            ObjectMapper objectMapper
    ) {
        this.reviewRepository = reviewRepository;
        this.aspectRepository = aspectRepository;
        this.reviewAspectResultRepository = reviewAspectResultRepository;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.tokenizationService = tokenizationService;
        this.topicAnalysisService = topicAnalysisService;
        this.clusterAnalysisService = clusterAnalysisService;
        this.alertService = alertService;
        this.suggestionService = suggestionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void analyzeReviewIds(Collection<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return;
        }

        List<ReviewEntity> reviews = reviewRepository.findAllById(reviewIds);
        if (reviews.isEmpty()) {
            return;
        }

        analyzeReviews(reviews);
        reviewRepository.flush();

        Set<Long> productIds = new LinkedHashSet<>();
        for (ReviewEntity review : reviews) {
            if (review.getProduct() != null && review.getProduct().getId() != null) {
                productIds.add(review.getProduct().getId());
            }
        }
        for (Long productId : productIds) {
            topicAnalysisService.recompute(productId, null, null);
            clusterAnalysisService.recompute(productId, null, null);
            alertService.recompute(productId, null, null);
            suggestionService.recompute(productId, null, null);
        }
    }

    @Transactional
    public void analyzeByProduct(Long productId, LocalDate start, LocalDate end) {
        Objects.requireNonNull(productId, "productId");

        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();
        List<Long> reviewIds = reviewRepository.findIdsForAnalysis(productId, startTime, endExclusive);
        if (reviewIds.isEmpty()) {
            return;
        }

        List<ReviewEntity> reviews = reviewRepository.findAllById(reviewIds);
        if (reviews.isEmpty()) {
            return;
        }

        analyzeReviews(reviews);
        reviewRepository.flush();

        topicAnalysisService.recompute(productId, start, end);
        clusterAnalysisService.recompute(productId, start, end);
        alertService.recompute(productId, start, end);
        suggestionService.recompute(productId, start, end);
    }

    private void analyzeReviews(List<ReviewEntity> reviews) {
        List<AspectEntity> aspects = aspectRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        LocalDateTime now = LocalDateTime.now();

        List<Long> reviewIds = reviews.stream().map(ReviewEntity::getId).toList();
        reviewAspectResultRepository.deleteByReviewIdIn(reviewIds);

        List<ReviewAspectResultEntity> resultsToSave = new ArrayList<>();
        for (ReviewEntity review : reviews) {
            String content = review.getContentClean();
            SentimentResult sentiment = sentimentAnalyzer.analyze(content);
            review.setOverallSentimentLabel(sentiment.label());
            review.setOverallSentimentScore(sentiment.score());
            review.setTokensJson(toJson(tokenizationService.tokenize(content)));

            for (AspectEntity aspect : aspects) {
                AspectMatch match = matchAspect(content, aspect.getKeywordsJson());
                if (match.hitKeywords().isEmpty()) {
                    continue;
                }

                String hitKeywordsJson = toJson(match.hitKeywords());
                resultsToSave.add(new ReviewAspectResultEntity(
                        review,
                        aspect,
                        hitKeywordsJson,
                        sentiment.label(),
                        sentiment.score(),
                        match.confidence(),
                        now
                ));
            }
        }

        reviewAspectResultRepository.saveAll(resultsToSave);
    }

    private AspectMatch matchAspect(String content, String keywordsJson) {
        if (content == null || content.isBlank() || keywordsJson == null || keywordsJson.isBlank()) {
            return new AspectMatch(List.of(), 0.0);
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(keywordsJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("维度关键词配置格式错误：" + keywordsJson, e);
        }
        if (node == null || !node.isArray()) {
            return new AspectMatch(List.of(), 0.0);
        }

        LinkedHashSet<String> hitKeywords = new LinkedHashSet<>();
        double sumWeight = 0.0;
        for (JsonNode item : node) {
            KeywordDef def = parseKeywordDef(item);
            if (def == null) {
                continue;
            }
            if (!content.contains(def.keyword())) {
                continue;
            }
            if (hitKeywords.add(def.keyword())) {
                sumWeight += def.weight();
            }
        }

        double confidence = Math.min(1.0, sumWeight / 5.0);
        return new AspectMatch(new ArrayList<>(hitKeywords), confidence);
    }

    private KeywordDef parseKeywordDef(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isTextual()) {
            String keyword = item.asText();
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            return new KeywordDef(keyword, 1.0);
        }
        if (!item.isObject()) {
            return null;
        }
        String keyword = item.path("keyword").asText(null);
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        double weight = item.hasNonNull("weight") ? item.get("weight").asDouble(1.0) : 1.0;
        if (weight <= 0) {
            weight = 1.0;
        }
        return new KeywordDef(keyword, weight);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("数据序列化失败", e);
        }
    }

    private record KeywordDef(String keyword, double weight) {}

    private record AspectMatch(List<String> hitKeywords, double confidence) {}
}
