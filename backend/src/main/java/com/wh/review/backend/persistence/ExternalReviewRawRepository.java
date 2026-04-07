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
public class ExternalReviewRawRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final SimpleJdbcInsert insertReview;

    public ExternalReviewRawRepository(JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
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
                        "demo_data_version",
                        "provider",
                        "platform",
                        "external_product_code",
                        "sync_job_id",
                        "external_dedupe_key",
                        "fetch_metadata"
                );
    }

    public ExternalReviewPersistenceResult upsertReviews(
            String provider,
            String platform,
            String productCode,
            long syncJobId,
            List<ExternalRawReview> reviews
    ) {
        long productId = productRepository.ensureProductId(productCode);
        String source = provider + ":" + platform;

        int insertedCount = 0;
        int updatedCount = 0;
        for (ExternalRawReview review : reviews) {
            int changedRows = updateReview(source, review, productId, syncJobId);
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
                payload.put("review_time", review.reviewTime() == null ? null : Timestamp.from(review.reviewTime()));
                payload.put("anonymized_author_id", review.anonymizedAuthorId());
                payload.put("demo_data_version", null);
                payload.put("provider", provider);
                payload.put("platform", platform);
                payload.put("external_product_code", productCode);
                payload.put("sync_job_id", syncJobId);
                payload.put("external_dedupe_key", review.dedupeKey());
                payload.put("fetch_metadata", review.fetchMetadata());
                insertReview.execute(payload);
                insertedCount++;
            } catch (DuplicateKeyException ex) {
                updatedCount += updateReview(source, review, productId, syncJobId);
            }
        }

        Integer totalCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reviews_raw
                WHERE provider = ?
                  AND platform = ?
                  AND external_product_code = ?
                """,
                Integer.class,
                provider,
                platform,
                productCode
        );

        return new ExternalReviewPersistenceResult(
                productCode,
                productId,
                insertedCount,
                updatedCount,
                totalCount == null ? 0 : totalCount
        );
    }

    private int updateReview(String source, ExternalRawReview review, long productId, long syncJobId) {
        return jdbcTemplate.update(
                """
                UPDATE reviews_raw
                SET product_id = ?,
                    rating = ?,
                    content = ?,
                    review_time = ?,
                    anonymized_author_id = ?,
                    demo_data_version = NULL,
                    provider = ?,
                    platform = ?,
                    external_product_code = ?,
                    sync_job_id = ?,
                    external_dedupe_key = ?,
                    fetch_metadata = ?,
                    fetched_at = CURRENT_TIMESTAMP
                WHERE source = ?
                  AND source_review_id = ?
                """,
                productId,
                review.rating(),
                review.content(),
                review.reviewTime() == null ? null : Timestamp.from(review.reviewTime()),
                review.anonymizedAuthorId(),
                review.provider(),
                review.platform(),
                review.externalProductCode(),
                syncJobId,
                review.dedupeKey(),
                review.fetchMetadata(),
                source,
                review.sourceReviewId()
        );
    }

    public record ExternalRawReview(
            String provider,
            String platform,
            String externalProductCode,
            String sourceReviewId,
            String dedupeKey,
            BigDecimal rating,
            String content,
            Instant reviewTime,
            String anonymizedAuthorId,
            String fetchMetadata
    ) {
    }

    public record ExternalReviewPersistenceResult(
            String productCode,
            long productId,
            int insertedCount,
            int updatedCount,
            int totalCount
    ) {
    }
}
