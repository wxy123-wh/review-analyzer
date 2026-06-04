package review.backend.data;

import review.backend.api.dto.TaxonomyResponse;
import review.backend.api.dto.UxPrimaryLabelResponse;
import review.backend.api.dto.UxSecondaryLabelResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TaxonomyRepository {

    private static final String DEFAULT_TAXONOMY_NAME = "默认通用 UX 标签";
    private static final String DEFAULT_PRODUCT_CATEGORY = "general-product";
    private static final String LEGACY_DEFAULT_PRODUCT_CATEGORY = "general";

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertTaxonomy;
    private final SimpleJdbcInsert insertPrimaryLabel;
    private final SimpleJdbcInsert insertSecondaryLabel;

    public TaxonomyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertTaxonomy = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("ux_taxonomies")
                .usingColumns("name", "product_category", "version", "active")
                .usingGeneratedKeyColumns("id");
        this.insertPrimaryLabel = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("ux_primary_labels")
                .usingColumns("taxonomy_id", "label_name", "sort_order")
                .usingGeneratedKeyColumns("id");
        this.insertSecondaryLabel = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("ux_secondary_labels")
                .usingColumns(
                        "taxonomy_id",
                        "primary_label_id",
                        "label_name",
                        "synonyms",
                        "description",
                        "sort_order",
                        "enabled"
                )
                .usingGeneratedKeyColumns("id");
    }

    public List<TaxonomyResponse> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM (
                    SELECT id,
                           ROW_NUMBER() OVER (
                               PARTITION BY CASE
                                   WHEN name = ? AND product_category IN (?, ?) THEN ?
                                   ELSE product_category || ':' || name
                               END
                               ORDER BY CASE
                                   WHEN name = ? AND product_category = ? THEN 0
                                   WHEN name = ? AND product_category = ? THEN 1
                                   ELSE 2
                               END ASC,
                               version DESC,
                               id DESC
                           ) AS taxonomy_rank
                    FROM ux_taxonomies
                    WHERE active = TRUE
                ) ranked_taxonomies
                WHERE taxonomy_rank = 1
                ORDER BY id ASC
                """,
                (rs, rowNum) -> rs.getLong("id"),
                DEFAULT_TAXONOMY_NAME,
                DEFAULT_PRODUCT_CATEGORY,
                LEGACY_DEFAULT_PRODUCT_CATEGORY,
                DEFAULT_TAXONOMY_NAME,
                DEFAULT_TAXONOMY_NAME,
                DEFAULT_PRODUCT_CATEGORY,
                DEFAULT_TAXONOMY_NAME,
                LEGACY_DEFAULT_PRODUCT_CATEGORY
        ).stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<TaxonomyResponse> findById(long taxonomyId) {
        List<TaxonomyHeader> headers = jdbcTemplate.query(
                """
                SELECT id, name, product_category, version, active
                FROM ux_taxonomies
                WHERE id = ?
                """,
                (rs, rowNum) -> new TaxonomyHeader(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("product_category"),
                        rs.getInt("version"),
                        rs.getBoolean("active")
                ),
                taxonomyId
        );
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        TaxonomyHeader header = headers.getFirst();
        return Optional.of(new TaxonomyResponse(
                header.id(),
                header.name(),
                header.productCategory(),
                header.version(),
                header.active(),
                findPrimaryLabels(header.id())
        ));
    }

    public Optional<TaxonomyResponse> findBoundForProduct(String productCode) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT taxonomy_id
                FROM product_taxonomy_bindings
                WHERE product_code = ?
                """,
                (rs, rowNum) -> rs.getLong("taxonomy_id"),
                productCode
        );
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findById(ids.getFirst());
    }

    public Optional<TaxonomyResponse> findDefault() {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM ux_taxonomies
                WHERE active = TRUE
                  AND name = ?
                  AND product_category IN (?, ?)
                ORDER BY CASE WHEN product_category = ? THEN 0 ELSE 1 END ASC,
                         version DESC,
                         id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                DEFAULT_TAXONOMY_NAME,
                DEFAULT_PRODUCT_CATEGORY,
                LEGACY_DEFAULT_PRODUCT_CATEGORY,
                DEFAULT_PRODUCT_CATEGORY
        );
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findById(ids.getFirst());
    }

    @Transactional
    public TaxonomyResponse create(
            String name,
            String productCategory,
            int version,
            List<PrimaryLabelDraft> primaryLabels
    ) {
        deactivateCurrentVersion(name, productCategory);
        Number taxonomyKey = insertTaxonomy.executeAndReturnKey(Map.of(
                "name", name,
                "product_category", productCategory,
                "version", version,
                "active", true
        ));
        long taxonomyId = taxonomyKey.longValue();
        for (int primaryIndex = 0; primaryIndex < primaryLabels.size(); primaryIndex++) {
            PrimaryLabelDraft primary = primaryLabels.get(primaryIndex);
            Number primaryKey = insertPrimaryLabel.executeAndReturnKey(Map.of(
                    "taxonomy_id", taxonomyId,
                    "label_name", primary.labelName(),
                    "sort_order", primaryIndex
            ));
            long primaryId = primaryKey.longValue();
            for (int secondaryIndex = 0; secondaryIndex < primary.secondaryLabels().size(); secondaryIndex++) {
                SecondaryLabelDraft secondary = primary.secondaryLabels().get(secondaryIndex);
                Map<String, Object> payload = new HashMap<>();
                payload.put("taxonomy_id", taxonomyId);
                payload.put("primary_label_id", primaryId);
                payload.put("label_name", secondary.labelName());
                payload.put("synonyms", String.join(",", secondary.synonyms()));
                payload.put("description", secondary.description());
                payload.put("sort_order", secondaryIndex);
                payload.put("enabled", secondary.enabled());
                insertSecondaryLabel.execute(payload);
            }
        }
        return findById(taxonomyId)
                .orElseThrow(() -> new IllegalStateException("taxonomy insert succeeded but row was not found"));
    }

    public void deactivateDuplicateDefaults(long activeTaxonomyId) {
        jdbcTemplate.update(
                """
                UPDATE ux_taxonomies
                SET active = FALSE
                WHERE active = TRUE
                  AND name = ?
                  AND product_category IN (?, ?)
                  AND id <> ?
                """,
                DEFAULT_TAXONOMY_NAME,
                DEFAULT_PRODUCT_CATEGORY,
                LEGACY_DEFAULT_PRODUCT_CATEGORY,
                activeTaxonomyId
        );
    }

    private void deactivateCurrentVersion(String name, String productCategory) {
        jdbcTemplate.update(
                """
                UPDATE ux_taxonomies
                SET active = FALSE
                WHERE active = TRUE
                  AND name = ?
                  AND product_category = ?
                """,
                name,
                productCategory
        );
    }

    @Transactional
    public void bindProduct(String productCode, long taxonomyId) {
        jdbcTemplate.update("DELETE FROM product_taxonomy_bindings WHERE product_code = ?", productCode);
        jdbcTemplate.update(
                """
                INSERT INTO product_taxonomy_bindings (product_code, taxonomy_id, bound_at)
                VALUES (?, ?, ?)
                """,
                productCode,
                taxonomyId,
                Timestamp.from(Instant.now())
        );
    }

    private List<UxPrimaryLabelResponse> findPrimaryLabels(long taxonomyId) {
        List<PrimaryRow> rows = jdbcTemplate.query(
                """
                SELECT pl.id AS primary_id,
                       pl.label_name AS primary_name,
                       pl.sort_order AS primary_sort,
                       sl.id AS secondary_id,
                       sl.label_name AS secondary_name,
                       sl.synonyms,
                       sl.description,
                       sl.sort_order AS secondary_sort,
                       sl.enabled
                FROM ux_primary_labels pl
                LEFT JOIN ux_secondary_labels sl ON sl.primary_label_id = pl.id
                WHERE pl.taxonomy_id = ?
                ORDER BY pl.sort_order ASC, pl.id ASC, sl.sort_order ASC, sl.id ASC
                """,
                this::toPrimaryRow,
                taxonomyId
        );
        Map<Long, PrimaryAccumulator> grouped = new LinkedHashMap<>();
        for (PrimaryRow row : rows) {
            PrimaryAccumulator primary = grouped.computeIfAbsent(
                    row.primaryId(),
                    ignored -> new PrimaryAccumulator(row.primaryId(), row.primaryName(), row.primarySort())
            );
            if (row.secondaryId() != null) {
                primary.secondaryLabels().add(new UxSecondaryLabelResponse(
                        row.secondaryId(),
                        row.secondaryName(),
                        splitSynonyms(row.synonyms()),
                        row.description(),
                        row.secondarySort(),
                        row.enabled()
                ));
            }
        }
        return grouped.values().stream()
                .map(primary -> new UxPrimaryLabelResponse(
                        primary.id(),
                        primary.labelName(),
                        primary.sortOrder(),
                        primary.secondaryLabels()
                ))
                .toList();
    }

    private PrimaryRow toPrimaryRow(ResultSet rs, int rowNum) throws SQLException {
        long secondaryId = rs.getLong("secondary_id");
        Long nullableSecondaryId = rs.wasNull() ? null : secondaryId;
        return new PrimaryRow(
                rs.getLong("primary_id"),
                rs.getString("primary_name"),
                rs.getInt("primary_sort"),
                nullableSecondaryId,
                rs.getString("secondary_name"),
                rs.getString("synonyms"),
                rs.getString("description"),
                rs.getInt("secondary_sort"),
                rs.getBoolean("enabled")
        );
    }

    private List<String> splitSynonyms(String synonyms) {
        if (synonyms == null || synonyms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(synonyms.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private record TaxonomyHeader(long id, String name, String productCategory, int version, boolean active) {
    }

    private record PrimaryRow(
            long primaryId,
            String primaryName,
            int primarySort,
            Long secondaryId,
            String secondaryName,
            String synonyms,
            String description,
            int secondarySort,
            boolean enabled
    ) {
    }

    private record PrimaryAccumulator(
            long id,
            String labelName,
            int sortOrder,
            List<UxSecondaryLabelResponse> secondaryLabels
    ) {
        private PrimaryAccumulator(long id, String labelName, int sortOrder) {
            this(id, labelName, sortOrder, new ArrayList<>());
        }
    }

    public record PrimaryLabelDraft(String labelName, List<SecondaryLabelDraft> secondaryLabels) {
    }

    public record SecondaryLabelDraft(
            String labelName,
            List<String> synonyms,
            String description,
            boolean enabled
    ) {
    }
}
