package review.backend.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class DataQualityRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SimpleJdbcInsert insertRun;

    public DataQualityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.insertRun = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("data_quality_runs")
                .usingColumns(
                        "product_code",
                        "raw_count",
                        "cleaned_count",
                        "removed_count",
                        "html_cleaned_count",
                        "exact_duplicate_count",
                        "empty_content_count",
                        "invalid_json_count",
                        "placeholder_content_count",
                        "summary_json"
                );
    }

    public void save(String productCode, Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return;
        }
        insertRun.execute(Map.of(
                "product_code", productCode,
                "raw_count", intValue(summary, "rawCount"),
                "cleaned_count", intValue(summary, "cleanedCount"),
                "removed_count", intValue(summary, "removedCount"),
                "html_cleaned_count", intValue(summary, "htmlCleanedCount"),
                "exact_duplicate_count", intValue(summary, "exactDuplicateCount"),
                "empty_content_count", intValue(summary, "emptyContentCount"),
                "invalid_json_count", intValue(summary, "invalidJsonCount"),
                "placeholder_content_count", intValue(summary, "placeholderContentCount"),
                "summary_json", toJson(summary)
        ));
    }

    public Optional<DataQualityRun> findLatest(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT product_code,
                       raw_count,
                       cleaned_count,
                       removed_count,
                       html_cleaned_count,
                       exact_duplicate_count,
                       empty_content_count,
                       invalid_json_count,
                       placeholder_content_count,
                       imported_at
                FROM data_quality_runs
                WHERE product_code = ?
                ORDER BY imported_at DESC, id DESC
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new DataQualityRun(
                            rs.getString("product_code"),
                            rs.getInt("raw_count"),
                            rs.getInt("cleaned_count"),
                            rs.getInt("removed_count"),
                            rs.getInt("html_cleaned_count"),
                            rs.getInt("exact_duplicate_count"),
                            rs.getInt("empty_content_count"),
                            rs.getInt("invalid_json_count"),
                            rs.getInt("placeholder_content_count"),
                            rs.getTimestamp("imported_at").toInstant()
                    ));
                },
                productCode
        );
    }

    private int intValue(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String toJson(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    public record DataQualityRun(
            String productCode,
            int rawCount,
            int cleanedCount,
            int removedCount,
            int htmlCleanedCount,
            int exactDuplicateCount,
            int emptyContentCount,
            int invalidJsonCount,
            int placeholderContentCount,
            Instant importedAt
    ) {
    }
}
