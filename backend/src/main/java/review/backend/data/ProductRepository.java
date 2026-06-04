package review.backend.data;

import review.backend.api.dto.ImportedProductHistoryItem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertProduct;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertProduct = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("products")
                .usingColumns("product_code", "product_name", "brand")
                .usingGeneratedKeyColumns("id");
    }

    public long ensureProductId(String productCode) {
        return ensureProductId(productCode, null);
    }

    public List<ImportedProductHistoryItem> findImportedProductHistory(int limit) {
        return jdbcTemplate.query(
                """
                SELECT p.product_code,
                       p.product_name,
                       COUNT(DISTINCT r.id) AS imported_review_count,
                       COUNT(DISTINCT ra.review_id) AS analyzed_review_count,
                       COUNT(DISTINCT rsl.review_id) AS semantic_label_count,
                       CASE WHEN ptb.product_code IS NULL THEN FALSE ELSE TRUE END AS taxonomy_bound,
                       MAX(r.fetched_at) AS latest_imported_at,
                       p.created_at,
                       (
                           SELECT aj.status
                           FROM analysis_jobs aj
                           WHERE aj.product_code = p.product_code
                           ORDER BY COALESCE(aj.finished_at, aj.started_at) DESC, aj.id DESC
                           LIMIT 1
                       ) AS latest_analysis_status
                FROM products p
                JOIN reviews_raw r ON r.product_id = p.id
                LEFT JOIN review_aspects ra ON ra.review_id = r.id
                LEFT JOIN review_semantic_labels rsl ON rsl.review_id = r.id
                LEFT JOIN product_taxonomy_bindings ptb ON ptb.product_code = p.product_code
                GROUP BY p.id, p.product_code, p.product_name, p.created_at, ptb.product_code
                ORDER BY MAX(r.fetched_at) DESC, p.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    int analyzedReviewCount = rs.getInt("analyzed_review_count");
                    int semanticLabelCount = rs.getInt("semantic_label_count");
                    return new ImportedProductHistoryItem(
                            rs.getString("product_code"),
                            normalizeProductName(rs.getString("product_code"), rs.getString("product_name")),
                            rs.getInt("imported_review_count"),
                            analyzedReviewCount,
                            analyzedReviewCount > 0 && semanticLabelCount > 0,
                            rs.getBoolean("taxonomy_bound"),
                            normalizeOptionalProductName(rs.getString("latest_analysis_status")),
                            instantOrNull(rs.getTimestamp("latest_imported_at")),
                            instantOrNull(rs.getTimestamp("created_at"))
                    );
                },
                Math.max(1, Math.min(limit, 100))
        );
    }

    public Optional<String> findProductName(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT product_name FROM products WHERE product_code = ?",
                rs -> rs.next() ? Optional.ofNullable(normalizeOptionalProductName(rs.getString("product_name"))) : Optional.empty(),
                productCode.trim()
        );
    }

    public long ensureProductId(String productCode, String productName) {
        Long existingId = findProductId(productCode);
        if (existingId != null) {
            updateProductNameIfPresent(existingId, productCode, productName);
            return existingId;
        }

        String normalizedProductName = normalizeProductName(productCode, productName);
        try {
            Number key = insertProduct.executeAndReturnKey(Map.of(
                    "product_code", productCode,
                    "product_name", normalizedProductName,
                    "brand", "unknown"
            ));
            return key.longValue();
        } catch (DuplicateKeyException ex) {
            Long id = findProductId(productCode);
            if (id == null) {
                throw ex;
            }
            updateProductNameIfPresent(id, productCode, productName);
            return id;
        }
    }

    private void updateProductNameIfPresent(long productId, String productCode, String productName) {
        String normalizedProductName = normalizeOptionalProductName(productName);
        if (normalizedProductName == null) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE products
                SET product_name = ?
                WHERE id = ?
                  AND (product_name = ? OR product_name IS NULL OR TRIM(product_name) = '')
                """,
                normalizedProductName,
                productId,
                productCode
        );
    }

    private String normalizeProductName(String productCode, String productName) {
        String normalizedProductName = normalizeOptionalProductName(productName);
        return normalizedProductName == null ? productCode : normalizedProductName;
    }

    private String normalizeOptionalProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        return productName.trim();
    }

    private Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long findProductId(String productCode) {
        return jdbcTemplate.query(
                "SELECT id FROM products WHERE product_code = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                productCode
        );
    }
}
