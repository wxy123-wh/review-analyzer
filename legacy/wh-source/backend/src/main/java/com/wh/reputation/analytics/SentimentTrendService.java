package com.wh.reputation.analytics;

import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SentimentTrendService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public SentimentTrendService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SentimentTrendResponseDto sentimentTrend(
            Long productId,
            LocalDate start,
            LocalDate end,
            String granularity
    ) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        String normalizedGranularity = normalizeGranularityOrDefault(granularity);
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where r.product_id = ?");
        params.add(productId);
        if (startTime != null) {
            where.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            where.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        where.append(" ");

        String reviewTimeExpr = "coalesce(r.review_time, r.created_at)";
        String groupDateExpr = switch (normalizedGranularity) {
            case "day" -> "date(" + reviewTimeExpr + ")";
            case "week" ->
                    "date_sub(date(" + reviewTimeExpr + "), interval weekday(date(" + reviewTimeExpr + ")) day)";
            default -> throw new BadRequestException("时间粒度参数不正确（仅支持按天或按周）");
        };

        String sql = """
                select %s as d,
                       sum(case when r.overall_sentiment_label = 'POS' then 1 else 0 end) as posCnt,
                       sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt,
                       sum(case when r.overall_sentiment_label = 'NEU' then 1 else 0 end) as neuCnt,
                       count(*) as total
                from review r
                """.formatted(groupDateExpr) + where + """
                group by d
                order by d asc
                """;

        List<SentimentTrendPointDto> points = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long total = rs.getLong("total");
            long pos = rs.getLong("posCnt");
            long neg = rs.getLong("negCnt");
            long neu = rs.getLong("neuCnt");
            return new SentimentTrendPointDto(
                    DATE_FORMAT.format(rs.getDate("d").toLocalDate()),
                    pos,
                    neg,
                    neu,
                    total,
                    round3(rate(pos, total)),
                    round3(rate(neg, total))
            );
        }, params.toArray());

        long totalReviews = points.stream().mapToLong(SentimentTrendPointDto::total).sum();
        return new SentimentTrendResponseDto(points, new SentimentTrendMetaDto(normalizedGranularity, totalReviews));
    }

    private static String normalizeGranularityOrDefault(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "day";
        }
        String normalized = granularity.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "day", "week" -> normalized;
            default -> throw new BadRequestException("时间粒度参数不正确（仅支持按天或按周）");
        };
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }
}
