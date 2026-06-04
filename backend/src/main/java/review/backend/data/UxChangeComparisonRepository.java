package review.backend.data;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class UxChangeComparisonRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insertCheckpoint;

    public UxChangeComparisonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertCheckpoint = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("ux_change_checkpoints")
                .usingColumns(
                        "product_code",
                        "change_date",
                        "window_preset",
                        "before_start",
                        "before_end",
                        "after_start",
                        "after_end",
                        "created_at"
                )
                .usingGeneratedKeyColumns("id");
    }

    public UxChangeCheckpoint save(
            String productCode,
            LocalDate changeDate,
            String windowPreset,
            LocalDate beforeStart,
            LocalDate beforeEnd,
            LocalDate afterStart,
            LocalDate afterEnd
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_code", productCode);
        payload.put("change_date", Date.valueOf(changeDate));
        payload.put("window_preset", windowPreset);
        payload.put("before_start", Date.valueOf(beforeStart));
        payload.put("before_end", Date.valueOf(beforeEnd));
        payload.put("after_start", Date.valueOf(afterStart));
        payload.put("after_end", Date.valueOf(afterEnd));
        payload.put("created_at", Timestamp.from(Instant.now()));
        Number key = insertCheckpoint.executeAndReturnKey(payload);
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("ux change checkpoint insert succeeded but row was not found"));
    }

    public Optional<UxChangeCheckpoint> findById(long id) {
        List<UxChangeCheckpoint> rows = jdbcTemplate.query(
                """
                SELECT id,
                       product_code,
                       change_date,
                       window_preset,
                       before_start,
                       before_end,
                       after_start,
                       after_end,
                       created_at
                FROM ux_change_checkpoints
                WHERE id = ?
                """,
                (rs, rowNum) -> new UxChangeCheckpoint(
                        rs.getLong("id"),
                        rs.getString("product_code"),
                        rs.getDate("change_date").toLocalDate(),
                        rs.getString("window_preset"),
                        rs.getDate("before_start").toLocalDate(),
                        rs.getDate("before_end").toLocalDate(),
                        rs.getDate("after_start").toLocalDate(),
                        rs.getDate("after_end").toLocalDate(),
                        rs.getTimestamp("created_at").toInstant()
                ),
                id
        );
        return rows.stream().findFirst();
    }

    public List<UxChangeCheckpoint> findByProductCode(String productCode) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       product_code,
                       change_date,
                       window_preset,
                       before_start,
                       before_end,
                       after_start,
                       after_end,
                       created_at
                FROM ux_change_checkpoints
                WHERE product_code = ?
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new UxChangeCheckpoint(
                        rs.getLong("id"),
                        rs.getString("product_code"),
                        rs.getDate("change_date").toLocalDate(),
                        rs.getString("window_preset"),
                        rs.getDate("before_start").toLocalDate(),
                        rs.getDate("before_end").toLocalDate(),
                        rs.getDate("after_start").toLocalDate(),
                        rs.getDate("after_end").toLocalDate(),
                        rs.getTimestamp("created_at").toInstant()
                ),
                productCode
        );
    }

    public record UxChangeCheckpoint(
            long checkpointId,
            String productCode,
            LocalDate changeDate,
            String windowPreset,
            LocalDate beforeStart,
            LocalDate beforeEnd,
            LocalDate afterStart,
            LocalDate afterEnd,
            Instant createdAt
    ) {
    }
}
