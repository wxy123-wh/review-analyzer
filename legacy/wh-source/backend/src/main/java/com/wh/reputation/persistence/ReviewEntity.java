package com.wh.reputation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review")
public class ReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private PlatformEntity platform;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "review_id_raw", length = 128)
    private String reviewIdRaw;

    private Integer rating;

    @Lob
    @Column(name = "content_raw", nullable = false)
    private String contentRaw;

    @Lob
    @Column(name = "content_clean", nullable = false)
    private String contentClean;

    @Column(name = "tokens_json", columnDefinition = "json")
    private String tokensJson;

    @Column(name = "review_time")
    private LocalDateTime reviewTime;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(name = "overall_sentiment_label", nullable = false, length = 8)
    private String overallSentimentLabel;

    @Column(name = "overall_sentiment_score", nullable = false)
    private Double overallSentimentScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewEntity() {}

    public ReviewEntity(
            PlatformEntity platform,
            ProductEntity product,
            String reviewIdRaw,
            Integer rating,
            String contentRaw,
            String contentClean,
            LocalDateTime reviewTime,
            Integer likeCount,
            String hash,
            String overallSentimentLabel,
            Double overallSentimentScore,
            LocalDateTime createdAt
    ) {
        this.platform = platform;
        this.product = product;
        this.reviewIdRaw = reviewIdRaw;
        this.rating = rating;
        this.contentRaw = contentRaw;
        this.contentClean = contentClean;
        this.reviewTime = reviewTime;
        this.likeCount = likeCount;
        this.hash = hash;
        this.overallSentimentLabel = overallSentimentLabel;
        this.overallSentimentScore = overallSentimentScore;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PlatformEntity getPlatform() {
        return platform;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public String getReviewIdRaw() {
        return reviewIdRaw;
    }

    public Integer getRating() {
        return rating;
    }

    public String getContentRaw() {
        return contentRaw;
    }

    public String getContentClean() {
        return contentClean;
    }

    public String getTokensJson() {
        return tokensJson;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public String getHash() {
        return hash;
    }

    public String getOverallSentimentLabel() {
        return overallSentimentLabel;
    }

    public Double getOverallSentimentScore() {
        return overallSentimentScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setOverallSentimentLabel(String overallSentimentLabel) {
        this.overallSentimentLabel = overallSentimentLabel;
    }

    public void setOverallSentimentScore(Double overallSentimentScore) {
        this.overallSentimentScore = overallSentimentScore;
    }

    public void setTokensJson(String tokensJson) {
        this.tokensJson = tokensJson;
    }
}
