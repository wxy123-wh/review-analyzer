package review.backend.data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReviewRecord> findByProductCode(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT r.id,
                       p.product_code,
                       r.rating,
                       r.content,
                       r.review_time
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                ORDER BY r.review_time ASC, r.id ASC
                """,
                (rs, rowNum) -> {
                    Timestamp reviewTimeTs = rs.getTimestamp("review_time");
                    Instant reviewTime = reviewTimeTs == null ? Instant.EPOCH : reviewTimeTs.toInstant();
                    BigDecimal rating = rs.getBigDecimal("rating");
                    return new ReviewRecord(
                            rs.getLong("id"),
                            rs.getString("product_code"),
                            rating,
                            rs.getString("content"),
                            reviewTime
                    );
                },
                productCode
        );
    }

    public int countByProductCode(String productCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                """,
                Integer.class,
                productCode
        );
        return count == null ? 0 : count;
    }

    public List<RecentReviewRecord> findRecentByProductCode(String productCode, int limit) {
        return jdbcTemplate.query(
                """
                SELECT r.source_review_id,
                       p.product_name,
                       r.rating,
                       r.content,
                       r.review_time
                FROM reviews_raw r
                JOIN products p ON p.id = r.product_id
                WHERE p.product_code = ?
                ORDER BY COALESCE(r.review_time, r.fetched_at) DESC, r.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Timestamp reviewTimeTs = rs.getTimestamp("review_time");
                    return new RecentReviewRecord(
                            rs.getString("source_review_id"),
                            rs.getString("product_name"),
                            rs.getBigDecimal("rating"),
                            rs.getString("content"),
                            reviewTimeTs == null ? null : reviewTimeTs.toInstant()
                    );
                },
                productCode,
                Math.max(1, limit)
        );
    }

    public record ReviewRecord(
            long reviewId,
            String productCode,
            BigDecimal rating,
            String content,
            Instant reviewTime
    ) {
    }

    public record RecentReviewRecord(
            String sourceReviewId,
            String productName,
            BigDecimal rating,
            String content,
            Instant reviewTime
    ) {
    }
}
