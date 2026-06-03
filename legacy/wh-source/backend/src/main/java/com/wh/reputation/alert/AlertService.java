package com.wh.reputation.alert;

import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AlertService {
    private static final double DEFAULT_THRESHOLD = 0.10;
    private static final int DEFAULT_WINDOW_DAYS = 7;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public AlertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        DateWindow window = resolveWindow(productId, start, end);
        if (window == null) {
            return;
        }

        WindowStats overallCurrent = loadOverallStats(productId, window.currentStart(), window.currentEndExclusive());
        WindowStats overallPrev = loadOverallStats(productId, window.prevStart(), window.prevEndExclusive());
        maybeInsertAlert(productId, "negRate", null, window, overallCurrent.negRate(), overallPrev.negRate(), DEFAULT_THRESHOLD);

        Map<Long, WindowStats> aspectCurrent = loadAspectStats(productId, window.currentStart(), window.currentEndExclusive());
        Map<Long, WindowStats> aspectPrev = loadAspectStats(productId, window.prevStart(), window.prevEndExclusive());
        for (Map.Entry<Long, WindowStats> entry : aspectCurrent.entrySet()) {
            Long aspectId = entry.getKey();
            WindowStats cur = entry.getValue();
            if (cur.total() <= 0) {
                continue;
            }
            WindowStats prev = aspectPrev.getOrDefault(aspectId, new WindowStats(0, 0));
            maybeInsertAlert(productId, "negRate", aspectId, window, cur.negRate(), prev.negRate(), DEFAULT_THRESHOLD);
        }
    }

    public AlertsResponseDto list(Long productId, String status) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null && !normalizedStatus.equals("new") && !normalizedStatus.equals("ack")) {
            throw new BadRequestException("状态参数不正确");
        }

        if (countAlerts(productId) == 0) {
            recompute(productId, null, null);
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select a.id as id,
                       a.metric as metric,
                       a.aspect_id as aspectId,
                       a.window_start as windowStart,
                       a.window_end as windowEnd,
                       a.current_value as currentValue,
                       a.prev_value as prevValue,
                       a.threshold as threshold,
                       a.status as status,
                       a.created_at as createdAt
                from alert a
                where a.product_id = ?
                """);
        params.add(productId);

        if (normalizedStatus != null) {
            sql.append(" and a.status = ?");
            params.add(normalizedStatus);
        }
        sql.append(" order by a.created_at desc, a.id desc");

        List<AlertItemDto> items = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            LocalDate ws = rs.getDate("windowStart").toLocalDate();
            LocalDate we = rs.getDate("windowEnd").toLocalDate();
            Timestamp createdAt = rs.getTimestamp("createdAt");
            return new AlertItemDto(
                    rs.getLong("id"),
                    rs.getString("metric"),
                    (Long) rs.getObject("aspectId"),
                    DATE_FORMAT.format(ws),
                    DATE_FORMAT.format(we),
                    rs.getDouble("currentValue"),
                    rs.getDouble("prevValue"),
                    rs.getDouble("threshold"),
                    rs.getString("status"),
                    createdAt == null ? null : DATETIME_FORMAT.format(createdAt.toLocalDateTime())
            );
        }, params.toArray());

        return new AlertsResponseDto(items);
    }

    @Transactional
    public boolean ack(Long id) {
        if (id == null) {
            throw new BadRequestException("缺少预警编号");
        }
        int updated = jdbcTemplate.update("update alert set status = 'ack' where id = ?", id);
        if (updated <= 0) {
            throw new NotFoundException("未找到预警记录");
        }
        return true;
    }

    private long countAlerts(Long productId) {
        Long cnt = jdbcTemplate.queryForObject("select count(*) from alert where product_id = ?", Long.class, productId);
        return cnt == null ? 0 : cnt;
    }

    private void maybeInsertAlert(
            Long productId,
            String metric,
            Long aspectId,
            DateWindow window,
            double currentValue,
            double prevValue,
            double threshold
    ) {
        if (window == null) {
            return;
        }

        double diff = currentValue - prevValue;
        if (diff < threshold) {
            return;
        }

        Long exists = jdbcTemplate.queryForObject("""
                        select count(*)
                        from alert a
                        where a.product_id = ?
                          and a.metric = ?
                          and a.aspect_id <=> ?
                          and a.window_start = ?
                          and a.window_end = ?
                        """,
                Long.class,
                productId,
                metric,
                aspectId,
                toSqlDate(window.currentStartDate()),
                toSqlDate(window.currentEndDate())
        );
        if (exists != null && exists > 0) {
            return;
        }

        jdbcTemplate.update("""
                        insert into alert (product_id, metric, aspect_id, window_start, window_end, current_value, prev_value, threshold, status, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 'new', ?)
                        """,
                productId,
                metric,
                aspectId,
                toSqlDate(window.currentStartDate()),
                toSqlDate(window.currentEndDate()),
                currentValue,
                prevValue,
                threshold,
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private DateWindow resolveWindow(Long productId, LocalDate start, LocalDate end) {
        LocalDate minDate = jdbcTemplate.queryForObject(
                "select min(date(coalesce(review_time, created_at))) from review where product_id = ?",
                (rs, rowNum) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate(),
                productId
        );
        LocalDate maxDate = jdbcTemplate.queryForObject(
                "select max(date(coalesce(review_time, created_at))) from review where product_id = ?",
                (rs, rowNum) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate(),
                productId
        );
        if (minDate == null || maxDate == null) {
            return null;
        }

        LocalDate currentEnd = end == null ? maxDate : end;
        if (currentEnd.isAfter(maxDate)) {
            currentEnd = maxDate;
        }

        LocalDate currentStart;
        if (start != null) {
            currentStart = start;
        } else {
            currentStart = currentEnd.minusDays(DEFAULT_WINDOW_DAYS - 1L);
            if (currentStart.isBefore(minDate)) {
                currentStart = minDate;
            }
        }

        if (currentEnd.isBefore(currentStart)) {
            throw new BadRequestException("结束日期不能早于开始日期");
        }

        long days = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1L;
        LocalDate prevEnd = currentStart.minusDays(1);
        LocalDate prevStart = currentStart.minusDays(days);

        return new DateWindow(currentStart, currentEnd, prevStart, prevEnd);
    }

    private WindowStats loadOverallStats(Long productId, LocalDateTime start, LocalDateTime endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where r.product_id = ?");
        params.add(productId);
        if (start != null) {
            where.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(start));
        }
        if (endExclusive != null) {
            where.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        String sql = """
                select count(*) as total,
                       sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from review r
                """ + where;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new WindowStats(
                rs.getLong("total"),
                rs.getLong("negCnt")
        ), params.toArray());
    }

    private Map<Long, WindowStats> loadAspectStats(Long productId, LocalDateTime start, LocalDateTime endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select rar.aspect_id as aspectId,
                       count(*) as total,
                       sum(case when rar.sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from review_aspect_result rar
                join review r on r.id = rar.review_id
                where r.product_id = ?
                """);
        params.add(productId);
        if (start != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(start));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        sql.append(" group by rar.aspect_id");

        Map<Long, WindowStats> map = new HashMap<>();
        jdbcTemplate.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> map.put(
                rs.getLong("aspectId"),
                new WindowStats(rs.getLong("total"), rs.getLong("negCnt"))
        ), params.toArray());
        return map;
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private record WindowStats(long total, long negCount) {
        double negRate() {
            if (total <= 0) {
                return 0.0;
            }
            return (double) negCount / total;
        }
    }

    private record DateWindow(LocalDate currentStartDate, LocalDate currentEndDate, LocalDate prevStartDate, LocalDate prevEndDate) {
        LocalDateTime currentStart() {
            return currentStartDate == null ? null : currentStartDate.atStartOfDay();
        }

        LocalDateTime currentEndExclusive() {
            return currentEndDate == null ? null : currentEndDate.plusDays(1).atStartOfDay();
        }

        LocalDateTime prevStart() {
            return prevStartDate == null ? null : prevStartDate.atStartOfDay();
        }

        LocalDateTime prevEndExclusive() {
            return prevEndDate == null ? null : prevEndDate.plusDays(1).atStartOfDay();
        }
    }
}
