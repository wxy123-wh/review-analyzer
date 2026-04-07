package com.wh.review.backend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ValidationMetricsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SimpleJdbcInsert insertValidationMetrics;

    public ValidationMetricsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.insertValidationMetrics = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("validation_metrics")
                .usingColumns(
                        "action_id",
                        "window_start",
                        "window_end",
                        "before_metrics",
                        "after_metrics",
                        "conclusion",
                        "calculated_at"
                );
    }

    public Optional<ValidationSnapshot> findLatestByActionId(long actionId) {
        List<ValidationSnapshot> rows = jdbcTemplate.query(
                """
                SELECT action_id,
                       window_start,
                       window_end,
                       before_metrics,
                       after_metrics,
                       conclusion,
                       calculated_at
                FROM validation_metrics
                WHERE action_id = ?
                ORDER BY calculated_at DESC, id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new ValidationSnapshot(
                        rs.getLong("action_id"),
                        rs.getTimestamp("window_start").toInstant(),
                        rs.getTimestamp("window_end").toInstant(),
                        parseMetrics(rs.getString("before_metrics")),
                        parseMetrics(rs.getString("after_metrics")),
                        rs.getString("conclusion"),
                        rs.getTimestamp("calculated_at").toInstant()
                ),
                actionId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public void save(ValidationSnapshot snapshot) {
        insertValidationMetrics.execute(Map.of(
                "action_id", snapshot.actionId(),
                "window_start", Timestamp.from(snapshot.windowStart()),
                "window_end", Timestamp.from(snapshot.windowEnd()),
                "before_metrics", toJson(snapshot.beforeMetrics()),
                "after_metrics", toJson(snapshot.afterMetrics()),
                "conclusion", snapshot.conclusion(),
                "calculated_at", Timestamp.from(snapshot.calculatedAt())
        ));
    }

    private MetricsPayload parseMetrics(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new MetricsPayload(
                    node.path("sampleCount").asInt(0),
                    node.path("negativeCount").asInt(0),
                    node.path("negativeRate").asDouble(0D),
                    textOrNull(node, "aspect"),
                    textOrNull(node, "boundaryAt")
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse validation metrics payload", ex);
        }
    }

    private String toJson(MetricsPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleCount", payload.sampleCount());
        body.put("negativeCount", payload.negativeCount());
        body.put("negativeRate", payload.negativeRate());
        body.put("aspect", payload.aspect());
        body.put("boundaryAt", payload.boundaryAtIso());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize validation metrics payload", ex);
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record ValidationSnapshot(
            long actionId,
            Instant windowStart,
            Instant windowEnd,
            MetricsPayload beforeMetrics,
            MetricsPayload afterMetrics,
            String conclusion,
            Instant calculatedAt
    ) {
    }

    public record MetricsPayload(
            int sampleCount,
            int negativeCount,
            double negativeRate,
            String aspect,
            String boundaryAtIso
    ) {
    }
}
