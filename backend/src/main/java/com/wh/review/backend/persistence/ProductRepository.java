package com.wh.review.backend.persistence;

import java.util.Map;
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
        Long existingId = findProductId(productCode);
        if (existingId != null) {
            return existingId;
        }

        try {
            Number key = insertProduct.executeAndReturnKey(Map.of(
                    "product_code", productCode,
                    "product_name", productCode,
                    "brand", "unknown"
            ));
            return key.longValue();
        } catch (DuplicateKeyException ex) {
            Long id = findProductId(productCode);
            if (id == null) {
                throw ex;
            }
            return id;
        }
    }

    private Long findProductId(String productCode) {
        return jdbcTemplate.query(
                "SELECT id FROM products WHERE product_code = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                productCode
        );
    }
}
