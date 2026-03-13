package com.wh.review.backend.persistence;

import com.wh.review.backend.dto.SyncJobResponse;
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
public class SyncJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertSyncJob;

    private final RowMapper<SyncJobResponse> mapper = (rs, rowNum) -> new SyncJobResponse(
            String.valueOf(rs.getLong("id")),
            rs.getString("provider"),
            rs.getString("platform"),
            rs.getString("target_product_code"),
            rs.getString("status"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getInt("fetched_count"),
            rs.getString("error_message")
    );

    public SyncJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSyncJob = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("sync_jobs")
                .usingColumns(
                        "provider",
                        "platform",
                        "target_product_code",
                        "status",
                        "fetched_count",
                        "started_at",
                        "finished_at",
                        "error_message"
                )
                .usingGeneratedKeyColumns("id");
    }

    public SyncJobResponse create(SyncJobResponse response, Instant finishedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", response.provider());
        payload.put("platform", response.platform());
        payload.put("target_product_code", response.targetProductCode());
        payload.put("status", response.status());
        payload.put("fetched_count", response.fetchedCount());
        payload.put("started_at", Timestamp.from(response.startedAt()));
        payload.put("error_message", response.errorMessage());
        payload.put("finished_at", finishedAt == null ? null : Timestamp.from(finishedAt));

        Number key = insertSyncJob.executeAndReturnKey(payload);
        return findById(String.valueOf(key.longValue()))
                .orElseThrow(() -> new IllegalStateException("sync job insert succeeded but row was not found"));
    }

    public Optional<SyncJobResponse> findById(String jobId) {
        Long id = parseId(jobId);
        if (id == null) {
            return Optional.empty();
        }

        List<SyncJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id, provider, platform, target_product_code, status, fetched_count, started_at, error_message
                FROM sync_jobs
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
