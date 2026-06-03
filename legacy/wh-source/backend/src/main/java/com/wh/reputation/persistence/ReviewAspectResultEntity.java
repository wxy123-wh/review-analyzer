package com.wh.reputation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_aspect_result")
public class ReviewAspectResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "aspect_id", nullable = false)
    private AspectEntity aspect;

    @Column(name = "hit_keywords_json", nullable = false, columnDefinition = "json")
    private String hitKeywordsJson;

    @Column(name = "sentiment_label", nullable = false, length = 8)
    private String sentimentLabel;

    @Column(name = "sentiment_score", nullable = false)
    private Double sentimentScore;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewAspectResultEntity() {}

    public ReviewAspectResultEntity(
            ReviewEntity review,
            AspectEntity aspect,
            String hitKeywordsJson,
            String sentimentLabel,
            Double sentimentScore,
            Double confidence,
            LocalDateTime createdAt
    ) {
        this.review = review;
        this.aspect = aspect;
        this.hitKeywordsJson = hitKeywordsJson;
        this.sentimentLabel = sentimentLabel;
        this.sentimentScore = sentimentScore;
        this.confidence = confidence;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ReviewEntity getReview() {
        return review;
    }

    public AspectEntity getAspect() {
        return aspect;
    }

    public String getHitKeywordsJson() {
        return hitKeywordsJson;
    }

    public String getSentimentLabel() {
        return sentimentLabel;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public Double getConfidence() {
        return confidence;
    }
}

