package com.wh.review.backend.persistence;

import com.wh.review.backend.dto.AnalysisJobResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
            rs.getTimestamp("started_at").toInstant()
    );

    public AnalysisJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertAnalysisJob = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("analysis_jobs")
                .usingColumns("product_code", "status", "started_at")
                .usingGeneratedKeyColumns("id");
    }

    public AnalysisJobResponse create(String productCode, String status, Instant startedAt) {
        Number key = insertAnalysisJob.executeAndReturnKey(
                java.util.Map.of(
                        "product_code", productCode,
                        "status", status,
                        "started_at", Timestamp.from(startedAt)
                )
        );
        return findById(String.valueOf(key.longValue()))
                .orElseThrow(() -> new IllegalStateException("analysis job insert succeeded but row was not found"));
    }

    public Optional<AnalysisJobResponse> findById(String jobId) {
        Long id = parseId(jobId);
        if (id == null) {
            return Optional.empty();
        }

        List<AnalysisJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, product_code, status, started_at
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
