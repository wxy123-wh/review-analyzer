package review.backend.data;

import review.backend.api.dto.AnalysisJobResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertAnalysisJob;

    private final RowMapper<AnalysisJobResponse> mapper = (rs, rowNum) -> new AnalysisJobResponse(
            String.valueOf(rs.getLong("id")),
            rs.getString("product_code"),
            rs.getString("status"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
            rs.getString("error_message"),
            rs.getInt("total_review_count"),
            rs.getInt("processed_review_count"),
            rs.getInt("progress_percent"),
            rs.getString("current_stage"),
            rs.getInt("materialized_review_count"),
            rs.getInt("semantic_label_count"),
            rs.getInt("issue_cluster_count"),
            rs.getBoolean("downstream_ready")
    );

    public AnalysisJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertAnalysisJob = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("analysis_jobs")
                .usingColumns(
                        "product_code",
                        "status",
                        "started_at",
                        "finished_at",
                        "error_message",
                        "taxonomy_id",
                        "taxonomy_version",
                        "total_review_count",
                        "processed_review_count",
                        "progress_percent",
                        "current_stage",
                        "materialized_review_count",
                        "semantic_label_count",
                        "issue_cluster_count",
                        "downstream_ready"
                )
                .usingGeneratedKeyColumns("id");
    }

    public AnalysisJobResponse create(String productCode, String status, Instant startedAt) {
        return create(productCode, status, startedAt, null, null);
    }

    public AnalysisJobResponse create(
            String productCode,
            String status,
            Instant startedAt,
            Long taxonomyId,
            Integer taxonomyVersion
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_code", productCode);
        payload.put("status", status);
        payload.put("started_at", Timestamp.from(startedAt));
        payload.put("finished_at", null);
        payload.put("error_message", null);
        payload.put("taxonomy_id", taxonomyId);
        payload.put("taxonomy_version", taxonomyVersion);
        payload.put("total_review_count", 0);
        payload.put("processed_review_count", 0);
        payload.put("progress_percent", 0);
        payload.put("current_stage", "等待开始");
        payload.put("materialized_review_count", 0);
        payload.put("semantic_label_count", 0);
        payload.put("issue_cluster_count", 0);
        payload.put("downstream_ready", false);
        Number key = insertAnalysisJob.executeAndReturnKey(payload);
        return findById(String.valueOf(key.longValue()))
                .orElseThrow(() -> new IllegalStateException("analysis job insert succeeded but row was not found"));
    }

    public AnalysisJobResponse markRunning(String jobId) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                """
                UPDATE analysis_jobs
                SET status = ?,
                    finished_at = NULL,
                    error_message = NULL,
                    progress_percent = CASE WHEN progress_percent < 5 THEN 5 ELSE progress_percent END,
                    current_stage = ?,
                    materialized_review_count = 0,
                    semantic_label_count = 0,
                    issue_cluster_count = 0,
                    downstream_ready = FALSE
                WHERE id = ?
                """,
                STATUS_RUNNING,
                "正在读取评论",
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markMaterialized(
            String jobId,
            int materializedReviewCount,
            int semanticLabelCount,
            int issueClusterCount
    ) {
        long id = requireId(jobId);
        int materialized = Math.max(0, materializedReviewCount);
        int semanticLabels = Math.max(0, semanticLabelCount);
        int issueClusters = Math.max(0, issueClusterCount);
        boolean downstreamReady = materialized > 0 && semanticLabels > 0;
        jdbcTemplate.update(
                """
                UPDATE analysis_jobs
                SET materialized_review_count = ?,
                    semantic_label_count = ?,
                    issue_cluster_count = ?,
                    downstream_ready = ?,
                    current_stage = ?
                WHERE id = ?
                """,
                materialized,
                semanticLabels,
                issueClusters,
                downstreamReady,
                downstreamReady
                        ? "分析结果已写入 " + materialized + " 条语义评论"
                        : "LLM 已结束，但下游物化数据未写入",
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markProgress(String jobId, int totalReviewCount, int processedReviewCount, String currentStage) {
        long id = requireId(jobId);
        int total = Math.max(0, totalReviewCount);
        int processed = Math.max(0, Math.min(processedReviewCount, total == 0 ? processedReviewCount : total));
        int progress = computeProgressPercent(total, processed);
        String stage = currentStage == null || currentStage.isBlank() ? "LLM 分析中" : currentStage.trim();
        jdbcTemplate.update(
                """
                UPDATE analysis_jobs
                SET total_review_count = ?,
                    processed_review_count = ?,
                    progress_percent = ?,
                    current_stage = ?
                WHERE id = ?
                """,
                total,
                processed,
                progress,
                stage,
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markSucceeded(String jobId, Instant finishedAt) {
        return markSucceeded(jobId, finishedAt, null);
    }

    public AnalysisJobResponse markSucceeded(String jobId, Instant finishedAt, String errorMessage) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                """
                UPDATE analysis_jobs
                SET status = ?,
                    finished_at = ?,
                    error_message = ?,
                    processed_review_count = CASE
                        WHEN total_review_count > 0 THEN total_review_count
                        ELSE processed_review_count
                    END,
                    progress_percent = 100,
                    current_stage = CASE
                        WHEN downstream_ready = TRUE THEN current_stage
                        ELSE ?
                    END
                WHERE id = ?
                """,
                STATUS_SUCCEEDED,
                Timestamp.from(finishedAt),
                errorMessage,
                "分析完成，但下游数据未就绪",
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markFailed(String jobId, Instant finishedAt, String errorMessage) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                """
                UPDATE analysis_jobs
                SET status = ?,
                    finished_at = ?,
                    error_message = ?,
                    progress_percent = 100,
                    current_stage = ?,
                    downstream_ready = FALSE
                WHERE id = ?
                """,
                STATUS_FAILED,
                Timestamp.from(finishedAt),
                errorMessage,
                "分析失败",
                id
        );
        return findExisting(jobId);
    }

    public Optional<AnalysisJobResponse> findLatestSucceededForProduct(String productCode) {
        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
                     , total_review_count, processed_review_count, progress_percent, current_stage
                     , materialized_review_count, semantic_label_count, issue_cluster_count, downstream_ready
                FROM analysis_jobs
                WHERE product_code = ?
                  AND status = ?
                ORDER BY finished_at DESC, id DESC
                LIMIT 1
                """,
                mapper,
                productCode,
                STATUS_SUCCEEDED
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public Optional<AnalysisJobResponse> findLatest() {
        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
                     , total_review_count, processed_review_count, progress_percent, current_stage
                     , materialized_review_count, semantic_label_count, issue_cluster_count, downstream_ready
                FROM analysis_jobs
                ORDER BY COALESCE(finished_at, started_at) DESC, id DESC
                LIMIT 1
                """,
                mapper
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public Optional<AnalysisJobResponse> findLatestForProduct(String productCode) {
        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
                     , total_review_count, processed_review_count, progress_percent, current_stage
                     , materialized_review_count, semantic_label_count, issue_cluster_count, downstream_ready
                FROM analysis_jobs
                WHERE product_code = ?
                ORDER BY COALESCE(finished_at, started_at) DESC, id DESC
                LIMIT 1
                """,
                mapper,
                productCode
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public boolean hasActiveJobForProduct(String productCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM analysis_jobs
                WHERE product_code = ?
                  AND status IN (?, ?)
                """,
                Integer.class,
                productCode,
                STATUS_QUEUED,
                STATUS_RUNNING
        );
        return count != null && count > 0;
    }

    public Optional<AnalysisJobResponse> findById(String jobId) {
        Long id = parseId(jobId);
        if (id == null) {
            return Optional.empty();
        }

        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
                     , total_review_count, processed_review_count, progress_percent, current_stage
                     , materialized_review_count, semantic_label_count, issue_cluster_count, downstream_ready
                FROM analysis_jobs
                WHERE id = ?
                """,
                mapper,
                id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    private AnalysisJobResponse findExisting(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new IllegalStateException("analysis job row was not found after update, jobId=" + jobId));
    }

    private long requireId(String jobId) {
        Long parsedId = parseId(jobId);
        if (parsedId == null) {
            throw new IllegalArgumentException("analysis job id is invalid: " + jobId);
        }
        return parsedId;
    }

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    private int computeProgressPercent(int totalReviewCount, int processedReviewCount) {
        if (totalReviewCount <= 0) {
            return 8;
        }
        if (processedReviewCount <= 0) {
            return 12;
        }
        int percent = 12 + (int) Math.floor((processedReviewCount * 80D) / totalReviewCount);
        return Math.max(12, Math.min(92, percent));
    }

    private Long parseId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(jobId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
