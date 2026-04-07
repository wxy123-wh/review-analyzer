package com.wh.review.backend.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

    public List<MaterializedIssueRecord> findIssues(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT
                    ic.id AS cluster_id,
                    ic.aspect,
                    ic.title,
                    s.priority_score,
                    s.negative_rate,
                    s.trend_growth,
                    COALESCE(stats.mention_count, 0) AS mention_count,
                    COALESCE(stats.negative_count, 0) AS negative_count
                FROM issue_clusters ic
                JOIN products p ON p.id = ic.product_id
                JOIN issue_scores s ON s.issue_cluster_id = ic.id
                LEFT JOIN (
                    SELECT
                        r.product_id,
                        ra.aspect,
                        COUNT(*) AS mention_count,
                        SUM(CASE WHEN ra.sentiment_polarity = 'NEGATIVE' THEN 1 ELSE 0 END) AS negative_count
                    FROM review_aspects ra
                    JOIN reviews_raw r ON r.id = ra.review_id
                    GROUP BY r.product_id, ra.aspect
                ) stats ON stats.product_id = ic.product_id AND stats.aspect = ic.aspect
                WHERE p.product_code = ?
                ORDER BY s.priority_score DESC, ic.id ASC
                """,
                (rs, rowNum) -> new MaterializedIssueRecord(
                        rs.getLong("cluster_id"),
                        rs.getString("aspect"),
                        rs.getString("title"),
                        rs.getDouble("priority_score"),
                        rs.getDouble("negative_rate"),
                        rs.getDouble("trend_growth"),
                        rs.getInt("mention_count"),
                        rs.getInt("negative_count")
                ),
                productCode
        );
    }

    public List<MaterializedTrendReviewRecord> findTrendReviews(String productCode, String aspect) {
        List<Object> args = new ArrayList<>();
        args.add(productCode);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    r.review_time,
                    ra.sentiment_polarity
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """);
        if (aspect != null && !aspect.isBlank()) {
            sql.append(" AND ra.aspect = ?");
            args.add(aspect);
        }
        sql.append(" ORDER BY r.review_time ASC, r.id ASC");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new MaterializedTrendReviewRecord(
                        rs.getTimestamp("review_time").toInstant(),
                        rs.getString("sentiment_polarity")
                ),
                args.toArray()
        );
    }

    public List<MaterializedCompareAspectRecord> findCompareAspectScores(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT
                    ra.aspect,
                    COUNT(*) AS mention_count,
                    AVG(COALESCE(ra.sentiment_score, 0.5000)) AS avg_sentiment_score
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                GROUP BY ra.aspect
                ORDER BY ra.aspect ASC
                """,
                (rs, rowNum) -> new MaterializedCompareAspectRecord(
                        rs.getString("aspect"),
                        rs.getInt("mention_count"),
                        rs.getDouble("avg_sentiment_score")
                ),
                productCode
        );
    }

    public List<MaterializedWordCloudReviewRecord> findWordCloudReviews(String productCode, String aspect) {
        List<Object> args = new ArrayList<>();
        args.add(productCode);

        StringBuilder sql = new StringBuilder("""
                SELECT r.content, ra.sentiment_polarity
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """);
        if (aspect != null && !aspect.isBlank()) {
            sql.append(" AND ra.aspect = ?");
            args.add(aspect);
        }
        sql.append(" ORDER BY r.review_time ASC, r.id ASC");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new MaterializedWordCloudReviewRecord(
                        rs.getString("content"),
                        rs.getString("sentiment_polarity")
                ),
                args.toArray()
        );
    }

    public Optional<TopIssueScoreBreakdown> findTopIssueScoreBreakdown(String productCode) {
        List<TopIssueScoreBreakdown> rows = jdbcTemplate.query(
                """
                SELECT ic.title,
                       ic.aspect,
                       s.negative_rate,
                       s.mention_volume,
                       s.trend_growth,
                       s.competitor_gap,
                       s.priority_score
                FROM issue_clusters ic
                JOIN products p ON p.id = ic.product_id
                JOIN issue_scores s ON s.issue_cluster_id = ic.id
                WHERE p.product_code = ?
                ORDER BY s.priority_score DESC, ic.id ASC
                LIMIT 1
                """,
                (rs, rowNum) -> new TopIssueScoreBreakdown(
                        rs.getString("title"),
                        rs.getString("aspect"),
                        rs.getDouble("negative_rate"),
                        rs.getDouble("mention_volume"),
                        rs.getDouble("trend_growth"),
                        rs.getDouble("competitor_gap"),
                        rs.getDouble("priority_score")
                ),
                productCode
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
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

    public record MaterializedIssueRecord(
            long clusterId,
            String aspect,
            String title,
            double priorityScore,
            double negativeRate,
            double trendGrowth,
            int mentionCount,
            int negativeCount
    ) {
    }

    public record MaterializedTrendReviewRecord(
            Instant reviewTime,
            String sentimentPolarity
    ) {
    }

    public record MaterializedCompareAspectRecord(
            String aspect,
            int mentionCount,
            double avgSentimentScore
    ) {
    }

    public record MaterializedWordCloudReviewRecord(
            String content,
            String sentimentPolarity
    ) {
    }

    public record TopIssueScoreBreakdown(
            String title,
            String aspect,
            double negativeRate,
            double mentionVolume,
            double trendGrowth,
            double competitorGap,
            double priorityScore
    ) {
    }
}
