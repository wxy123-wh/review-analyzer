package com.wh.review.backend.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnalysisMaterializationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertReviewAspect;
    private final SimpleJdbcInsert insertIssueCluster;
    private final SimpleJdbcInsert insertIssueScore;

    public AnalysisMaterializationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertReviewAspect = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("review_aspects")
                .usingColumns("review_id", "aspect", "sentiment_polarity", "sentiment_score", "confidence");
        this.insertIssueCluster = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("issue_clusters")
                .usingColumns(
                        "product_id",
                        "aspect",
                        "title",
                        "keywords",
                        "representative_review_ids",
                        "severity_score"
                )
                .usingGeneratedKeyColumns("id");
        this.insertIssueScore = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("issue_scores")
                .usingColumns(
                        "issue_cluster_id",
                        "negative_rate",
                        "mention_volume",
                        "trend_growth",
                        "competitor_gap",
                        "priority_score",
                        "weight_config"
                );
    }

    public Optional<Instant> findLatestSourceUpdateTime(String productCode) {
        Timestamp timestamp = jdbcTemplate.query(
                """
                SELECT MAX(r.fetched_at) AS latest_fetched_at
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """,
                rs -> rs.next() ? rs.getTimestamp("latest_fetched_at") : null,
                productCode
        );
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
    }

    public boolean hasMaterializedOutputs(String productCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """,
                Integer.class,
                productCode
        );
        return count != null && count > 0;
    }

    @Transactional
    public void replaceOutputs(String productCode, Materialization materialization) {
        Long productId = findProductId(productCode);
        if (productId == null) {
            throw new IllegalStateException("product not found for productCode=" + productCode);
        }

        jdbcTemplate.update(
                """
                UPDATE improvement_actions
                SET issue_cluster_id = NULL
                WHERE issue_cluster_id IN (
                    SELECT id FROM issue_clusters WHERE product_id = ?
                )
                """,
                productId
        );
        jdbcTemplate.update(
                "DELETE FROM issue_scores WHERE issue_cluster_id IN (SELECT id FROM issue_clusters WHERE product_id = ?)",
                productId
        );
        jdbcTemplate.update("DELETE FROM issue_clusters WHERE product_id = ?", productId);
        jdbcTemplate.update(
                "DELETE FROM review_aspects WHERE review_id IN (SELECT id FROM reviews_raw WHERE product_id = ?)",
                productId
        );

        for (ReviewAspectRecord reviewAspect : materialization.reviewAspects()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("review_id", reviewAspect.reviewId());
            payload.put("aspect", reviewAspect.aspect());
            payload.put("sentiment_polarity", reviewAspect.sentimentPolarity());
            payload.put("sentiment_score", reviewAspect.sentimentScore());
            payload.put("confidence", reviewAspect.confidence());
            insertReviewAspect.execute(payload);
        }

        for (IssueClusterRecord cluster : materialization.issueClusters()) {
            Number clusterId = insertIssueCluster.executeAndReturnKey(Map.of(
                    "product_id", productId,
                    "aspect", cluster.aspect(),
                    "title", cluster.title(),
                    "keywords", cluster.keywords(),
                    "representative_review_ids", cluster.representativeReviewIds(),
                    "severity_score", cluster.severityScore()
            ));
            insertIssueScore.execute(Map.of(
                    "issue_cluster_id", clusterId.longValue(),
                    "negative_rate", cluster.negativeRate(),
                    "mention_volume", cluster.mentionVolume(),
                    "trend_growth", cluster.trendGrowth(),
                    "competitor_gap", cluster.competitorGap(),
                    "priority_score", cluster.priorityScore(),
                    "weight_config", cluster.weightConfig()
            ));
        }
    }

    private Long findProductId(String productCode) {
        return jdbcTemplate.query(
                "SELECT id FROM products WHERE product_code = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                productCode
        );
    }

    public record Materialization(
            List<ReviewAspectRecord> reviewAspects,
            List<IssueClusterRecord> issueClusters
    ) {
    }

    public record ReviewAspectRecord(
            long reviewId,
            String aspect,
            String sentimentPolarity,
            BigDecimal sentimentScore,
            BigDecimal confidence
    ) {
    }

    public record IssueClusterRecord(
            String aspect,
            String title,
            String keywords,
            String representativeReviewIds,
            BigDecimal severityScore,
            BigDecimal negativeRate,
            BigDecimal mentionVolume,
            BigDecimal trendGrowth,
            BigDecimal competitorGap,
            BigDecimal priorityScore,
            String weightConfig
    ) {
    }
}
