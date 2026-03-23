package com.wh.review.backend.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class DemoReviewSeedRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final SimpleJdbcInsert insertReview;
    private final SimpleJdbcInsert insertSeedVersion;

    public DemoReviewSeedRepository(JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
        this.insertReview = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("reviews_raw")
                .usingColumns(
                        "source",
                        "source_review_id",
                        "product_id",
                        "rating",
                        "content",
                        "review_time",
                        "anonymized_author_id",
                        "demo_data_version"
                );
        this.insertSeedVersion = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("demo_seed_versions")
                .usingColumns("seed_key", "product_code", "data_version", "target_count", "last_seeded_at");
    }

    public SeedResult upsertDemoReviews(
            String seedKey,
            String source,
            String productCode,
            String dataVersion,
            List<DemoReviewSeedItem> reviews
    ) {
        long productId = productRepository.ensureProductId(productCode);

        int insertedCount = 0;
        int updatedCount = 0;
        for (DemoReviewSeedItem review : reviews) {
            int changedRows = updateReview(source, review, productId, dataVersion);
            if (changedRows > 0) {
                updatedCount += changedRows;
                continue;
            }

            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("source", source);
                payload.put("source_review_id", review.sourceReviewId());
                payload.put("product_id", productId);
                payload.put("rating", review.rating());
                payload.put("content", review.content());
                payload.put("review_time", Timestamp.from(review.reviewTime()));
                payload.put("anonymized_author_id", review.anonymizedAuthorId());
                payload.put("demo_data_version", dataVersion);
                insertReview.execute(payload);
                insertedCount++;
            } catch (DuplicateKeyException ex) {
                updatedCount += updateReview(source, review, productId, dataVersion);
            }
        }

        upsertSeedVersion(seedKey, productCode, dataVersion, reviews.size());

        Integer totalCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reviews_raw
                WHERE source = ? AND product_id = ?
                """,
                Integer.class,
                source,
                productId
        );

        return new SeedResult(
                productCode,
                productId,
                insertedCount,
                updatedCount,
                totalCount == null ? 0 : totalCount
        );
    }

    private int updateReview(String source, DemoReviewSeedItem review, long productId, String dataVersion) {
        return jdbcTemplate.update(
                """
                UPDATE reviews_raw
                SET product_id = ?,
                    rating = ?,
                    content = ?,
                    review_time = ?,
                    anonymized_author_id = ?,
                    demo_data_version = ?,
                    fetched_at = CURRENT_TIMESTAMP
                WHERE source = ?
                  AND source_review_id = ?
                """,
                productId,
                review.rating(),
                review.content(),
                Timestamp.from(review.reviewTime()),
                review.anonymizedAuthorId(),
                dataVersion,
                source,
                review.sourceReviewId()
        );
    }

    private void upsertSeedVersion(String seedKey, String productCode, String dataVersion, int targetCount) {
        int changedRows = jdbcTemplate.update(
                """
                UPDATE demo_seed_versions
                SET data_version = ?,
                    target_count = ?,
                    last_seeded_at = CURRENT_TIMESTAMP
                WHERE seed_key = ?
                  AND product_code = ?
                """,
                dataVersion,
                targetCount,
                seedKey,
                productCode
        );

        if (changedRows > 0) {
            return;
        }

        try {
            insertSeedVersion.execute(Map.of(
                    "seed_key", seedKey,
                    "product_code", productCode,
                    "data_version", dataVersion,
                    "target_count", targetCount,
                    "last_seeded_at", Timestamp.from(Instant.now())
            ));
        } catch (DuplicateKeyException ex) {
            jdbcTemplate.update(
                    """
                    UPDATE demo_seed_versions
                    SET data_version = ?,
                        target_count = ?,
                        last_seeded_at = CURRENT_TIMESTAMP
                    WHERE seed_key = ?
                      AND product_code = ?
                    """,
                    dataVersion,
                    targetCount,
                    seedKey,
                    productCode
            );
        }
    }

    public record DemoReviewSeedItem(
            String sourceReviewId,
            BigDecimal rating,
            String content,
            Instant reviewTime,
            String anonymizedAuthorId
    ) {
    }

    public record SeedResult(
            String productCode,
            long productId,
            int insertedCount,
            int updatedCount,
            int totalCount
    ) {
    }
}
