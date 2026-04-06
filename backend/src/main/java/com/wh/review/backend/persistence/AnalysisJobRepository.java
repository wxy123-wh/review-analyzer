package com.wh.review.backend.persistence;

import com.wh.review.backend.dto.AnalysisJobResponse;
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
            rs.getString("error_message")
    );

    public AnalysisJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertAnalysisJob = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("analysis_jobs")
                .usingColumns("product_code", "status", "started_at", "finished_at", "error_message")
                .usingGeneratedKeyColumns("id");
    }

    public AnalysisJobResponse create(String productCode, String status, Instant startedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_code", productCode);
        payload.put("status", status);
        payload.put("started_at", Timestamp.from(startedAt));
        payload.put("finished_at", null);
        payload.put("error_message", null);
        Number key = insertAnalysisJob.executeAndReturnKey(payload);
        return findById(String.valueOf(key.longValue()))
                .orElseThrow(() -> new IllegalStateException("analysis job insert succeeded but row was not found"));
    }

    public AnalysisJobResponse markRunning(String jobId) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                "UPDATE analysis_jobs SET status = ?, finished_at = NULL, error_message = NULL WHERE id = ?",
                STATUS_RUNNING,
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markSucceeded(String jobId, Instant finishedAt) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                "UPDATE analysis_jobs SET status = ?, finished_at = ?, error_message = NULL WHERE id = ?",
                STATUS_SUCCEEDED,
                Timestamp.from(finishedAt),
                id
        );
        return findExisting(jobId);
    }

    public AnalysisJobResponse markFailed(String jobId, Instant finishedAt, String errorMessage) {
        long id = requireId(jobId);
        jdbcTemplate.update(
                "UPDATE analysis_jobs SET status = ?, finished_at = ?, error_message = ? WHERE id = ?",
                STATUS_FAILED,
                Timestamp.from(finishedAt),
                errorMessage,
                id
        );
        return findExisting(jobId);
    }

    public Optional<AnalysisJobResponse> findLatestSucceededForProduct(String productCode) {
        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
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

    public Optional<AnalysisJobResponse> findById(String jobId) {
        Long id = parseId(jobId);
        if (id == null) {
            return Optional.empty();
        }

        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at, finished_at, error_message
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
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

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
