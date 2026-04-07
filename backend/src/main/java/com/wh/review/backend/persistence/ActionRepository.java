package com.wh.review.backend.persistence;

import com.wh.review.backend.dto.ActionCreateRequest;
import com.wh.review.backend.dto.ActionResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

    private final RowMapper<ActionValidationContext> actionValidationContextMapper = (rs, rowNum) -> {
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp launchedAtTs = rs.getTimestamp("launched_at");
        Instant createdAt = createdAtTs == null ? Instant.now() : createdAtTs.toInstant();
        Instant launchedAt = launchedAtTs == null ? null : launchedAtTs.toInstant();
        Long issueClusterId = rs.getObject("issue_cluster_id") == null ? null : rs.getLong("issue_cluster_id");
        return new ActionValidationContext(
                new ActionResponse(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("product_code"),
                        rs.getString("issue_ref"),
                        rs.getString("action_name"),
                        rs.getString("action_desc"),
                        normalizeStatus(rs.getString("status")),
                        createdAt
                ),
                issueClusterId,
                launchedAt,
                rs.getString("aspect")
        );
    };

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
                .usingColumns("product_id", "issue_cluster_id", "issue_ref", "action_name", "action_desc", "launched_at", "status")
                .usingGeneratedKeyColumns("id");
    }

    public ActionResponse create(ActionCreateRequest request) {
        long productId = productRepository.ensureProductId(request.productCode());
        IssueClusterRef issueCluster = findIssueClusterRef(productId, request.issueId()).orElse(null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", productId);
        payload.put("issue_cluster_id", issueCluster == null ? null : issueCluster.clusterId());
        payload.put("issue_ref", request.issueId());
        payload.put("action_name", request.actionName());
        payload.put("action_desc", request.actionDesc());
        payload.put(
                "launched_at",
                issueCluster == null ? null : resolveDefaultLaunchedAt(productId, issueCluster.aspect()).map(Timestamp::from).orElse(null)
        );
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

    public Optional<ActionValidationContext> findValidationContextById(String actionId) {
        Long id = parseId(actionId);
        if (id == null) {
            return Optional.empty();
        }

        List<ActionValidationContext> rows = jdbcTemplate.query(
                validationContextSelect() + " WHERE a.id = ?",
                actionValidationContextMapper,
                id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public List<ActionValidationContext> listValidationContexts() {
        return jdbcTemplate.query(
                validationContextSelect() + " ORDER BY a.created_at DESC",
                actionValidationContextMapper
        );
    }

    public void updateLaunchContext(String actionId, Instant launchedAt) {
        Long id = parseId(actionId);
        if (id == null || launchedAt == null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE improvement_actions SET launched_at = COALESCE(launched_at, ?) WHERE id = ?",
                Timestamp.from(launchedAt),
                id
        );
    }

    private String validationContextSelect() {
        return """
                SELECT a.id,
                       p.product_code,
                       a.issue_ref,
                       a.action_name,
                       a.action_desc,
                       a.status,
                       a.created_at,
                       a.issue_cluster_id,
                       a.launched_at,
                       ic.aspect
                FROM improvement_actions a
                JOIN products p ON p.id = a.product_id
                LEFT JOIN issue_clusters ic ON ic.id = a.issue_cluster_id
                """;
    }

    private Optional<IssueClusterRef> findIssueClusterRef(long productId, String issueId) {
        Long clusterId = parseIssueClusterId(issueId);
        if (clusterId == null) {
            return Optional.empty();
        }
        List<IssueClusterRef> rows = jdbcTemplate.query(
                "SELECT id, aspect FROM issue_clusters WHERE id = ? AND product_id = ?",
                (rs, rowNum) -> new IssueClusterRef(rs.getLong("id"), rs.getString("aspect")),
                clusterId,
                productId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    private Optional<Instant> resolveDefaultLaunchedAt(long productId, String aspect) {
        if (aspect == null || aspect.isBlank()) {
            return Optional.empty();
        }
        List<Instant> reviewTimes = new ArrayList<>(jdbcTemplate.query(
                """
                SELECT r.review_time
                FROM review_aspects ra
                JOIN reviews_raw r ON r.id = ra.review_id
                WHERE r.product_id = ?
                  AND ra.aspect = ?
                ORDER BY r.review_time ASC, r.id ASC
                """,
                (rs, rowNum) -> rs.getTimestamp("review_time").toInstant(),
                productId,
                aspect
        ));
        if (reviewTimes.size() < 2) {
            return reviewTimes.isEmpty() ? Optional.empty() : Optional.of(reviewTimes.getFirst());
        }
        return Optional.of(reviewTimes.get(reviewTimes.size() / 2));
    }

    private String normalizeStatus(String status) {
        return status == null ? STATUS_PLANNED : status.toUpperCase(Locale.ROOT);
    }

    private Long parseIssueClusterId(String issueId) {
        if (issueId == null || issueId.isBlank()) {
            return null;
        }
        int lastDash = issueId.lastIndexOf('-');
        if (lastDash < 0 || lastDash == issueId.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(issueId.substring(lastDash + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
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

    public record ActionValidationContext(
            ActionResponse action,
            Long issueClusterId,
            Instant launchedAt,
            String aspect
    ) {
    }

    private record IssueClusterRef(long clusterId, String aspect) {
    }
}
