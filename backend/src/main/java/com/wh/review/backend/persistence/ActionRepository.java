package com.wh.review.backend.persistence;

import com.wh.review.backend.dto.ActionCreateRequest;
import com.wh.review.backend.dto.ActionResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ActionRepository {

    private static final String STATUS_PLANNED = "PLANNED";

    private static final String ACTION_SELECT = """
            SELECT a.id,
                   p.product_code,
                   a.issue_ref,
                   a.action_name,
                   a.action_desc,
                   a.status,
                   a.created_at
            FROM improvement_actions a
            JOIN products p ON p.id = a.product_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final SimpleJdbcInsert insertAction;

    private final RowMapper<ActionResponse> actionMapper = (rs, rowNum) -> {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTs == null ? Instant.now() : createdAtTs.toInstant();
        String status = rs.getString("status");
        String normalizedStatus = status == null ? STATUS_PLANNED : status.toUpperCase(Locale.ROOT);
        return new ActionResponse(
                String.valueOf(rs.getLong("id")),
                rs.getString("product_code"),
                rs.getString("issue_ref"),
                rs.getString("action_name"),
                rs.getString("action_desc"),
                normalizedStatus,
                createdAt
        );
    };

    public ActionRepository(JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
        this.insertAction = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("improvement_actions")
                .usingColumns("product_id", "issue_ref", "action_name", "action_desc", "status")
                .usingGeneratedKeyColumns("id");
    }

    public ActionResponse create(ActionCreateRequest request) {
        long productId = productRepository.ensureProductId(request.productCode());
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", productId);
        payload.put("issue_ref", request.issueId());
        payload.put("action_name", request.actionName());
        payload.put("action_desc", request.actionDesc());
        payload.put("status", STATUS_PLANNED);

        Number key = insertAction.executeAndReturnKey(payload);
        return findById(String.valueOf(key.longValue()))
                .orElseThrow(() -> new IllegalStateException("action insert succeeded but row was not found"));
    }

    public Optional<ActionResponse> findById(String actionId) {
        Long id = parseId(actionId);
        if (id == null) {
            return Optional.empty();
        }

        List<ActionResponse> rows = jdbcTemplate.query(
                ACTION_SELECT + " WHERE a.id = ?",
                actionMapper,
                id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public List<ActionResponse> listAll() {
        return jdbcTemplate.query(
                ACTION_SELECT + " ORDER BY a.created_at DESC",
                actionMapper
        );
    }

    private Long parseId(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(actionId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
