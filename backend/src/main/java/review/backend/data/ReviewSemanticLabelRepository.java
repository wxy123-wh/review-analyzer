package review.backend.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewSemanticLabelRepository {

    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    private static final String NO_ISSUE_LABEL = "无明显问题";

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertSemanticLabel;

    public ReviewSemanticLabelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSemanticLabel = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("review_semantic_labels")
                .usingColumns(
                        "review_id",
                        "aspect",
                        "sentiment_polarity",
                        "confidence",
                        "taxonomy_id",
                        "taxonomy_version",
                        "ux_primary_label",
                        "ux_secondary_label",
                        "standardized_reason",
                        "evidence",
                        "negative_intensity_score"
                );
    }

    public void replaceForProduct(long productId, List<SemanticLabelRecord> labels) {
        jdbcTemplate.update(
                "DELETE FROM review_semantic_labels WHERE review_id IN (SELECT id FROM reviews_raw WHERE product_id = ?)",
                productId
        );
        for (SemanticLabelRecord label : labels) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("review_id", label.reviewId());
            payload.put("aspect", label.aspect());
            payload.put("sentiment_polarity", label.sentimentPolarity());
            payload.put("confidence", label.confidence());
            payload.put("taxonomy_id", label.taxonomyId());
            payload.put("taxonomy_version", label.taxonomyVersion());
            payload.put("ux_primary_label", label.uxPrimaryLabel());
            payload.put("ux_secondary_label", label.uxSecondaryLabel());
            payload.put("standardized_reason", label.standardizedReason());
            payload.put("evidence", label.evidence());
            payload.put("negative_intensity_score", label.negativeIntensityScore());
            insertSemanticLabel.execute(payload);
        }
    }

    public List<PositiveInsightAggregate> findPositiveInsightAggregates(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT
                    sl.aspect,
                    sl.ux_primary_label,
                    sl.ux_secondary_label,
                    MIN(COALESCE(NULLIF(sl.standardized_reason, ''), sl.ux_secondary_label)) AS standardized_reason,
                    COUNT(*) AS mention_count,
                    AVG(COALESCE(sl.confidence, 0.0)) AS avg_confidence,
                    SUM(CASE WHEN sl.sentiment_polarity = 'POSITIVE' THEN 1 ELSE 0 END) AS positive_count
                FROM review_semantic_labels sl
                JOIN reviews_raw r ON r.id = sl.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                  AND sl.sentiment_polarity = 'POSITIVE'
                  AND sl.ux_secondary_label IS NOT NULL
                  AND sl.ux_secondary_label <> ''
                  AND sl.ux_secondary_label <> ?
                GROUP BY sl.aspect, sl.ux_primary_label, sl.ux_secondary_label
                ORDER BY mention_count DESC, avg_confidence DESC, sl.ux_secondary_label ASC
                """,
                (rs, rowNum) -> new PositiveInsightAggregate(
                        rs.getString("aspect"),
                        rs.getString("ux_primary_label"),
                        rs.getString("ux_secondary_label"),
                        rs.getString("standardized_reason"),
                        rs.getInt("mention_count"),
                        rs.getInt("positive_count"),
                        rs.getDouble("avg_confidence")
                ),
                productCode,
                NO_ISSUE_LABEL
        );
    }

    public List<String> findEvidence(String productCode, String uxSecondaryLabel, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(productCode);
        args.add(SENTIMENT_POSITIVE);
        args.add(uxSecondaryLabel);

        return jdbcTemplate.query(
                """
                SELECT COALESCE(NULLIF(sl.evidence, ''), r.content) AS evidence
                FROM review_semantic_labels sl
                JOIN reviews_raw r ON r.id = sl.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                  AND sl.sentiment_polarity = ?
                  AND sl.ux_secondary_label = ?
                ORDER BY sl.confidence DESC, r.review_time ASC, r.id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getString("evidence"),
                args.get(0),
                args.get(1),
                args.get(2),
                Math.max(1, limit)
        );
    }

    public int countPositiveLabels(String productCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM review_semantic_labels sl
                JOIN reviews_raw r ON r.id = sl.review_id
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                  AND sl.sentiment_polarity = 'POSITIVE'
                  AND sl.ux_secondary_label IS NOT NULL
                  AND sl.ux_secondary_label <> ''
                  AND sl.ux_secondary_label <> ?
                """,
                Integer.class,
                productCode,
                NO_ISSUE_LABEL
        );
        return count == null ? 0 : count;
    }

    public record SemanticLabelRecord(
            long reviewId,
            String aspect,
            String sentimentPolarity,
            BigDecimal confidence,
            String uxPrimaryLabel,
            String uxSecondaryLabel,
            String standardizedReason,
            String evidence,
            int negativeIntensityScore,
            long taxonomyId,
            int taxonomyVersion
    ) {
    }

    public record PositiveInsightAggregate(
            String aspect,
            String uxPrimaryLabel,
            String uxSecondaryLabel,
            String standardizedReason,
            int mentionCount,
            int positiveCount,
            double avgConfidence
    ) {
    }
}
