package review.backend.data;

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

    private Long findProductId(String productCode) {
        return jdbcTemplate.query(
                "SELECT id FROM products WHERE product_code = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                productCode
        );
    }
}
