package com.wh.reputation.dashboard;

import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.decision.DecisionPriorityService;
import com.wh.reputation.decision.PriorityItemDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final DecisionPriorityService decisionPriorityService;

    public DashboardService(JdbcTemplate jdbcTemplate, DecisionPriorityService decisionPriorityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.decisionPriorityService = decisionPriorityService;
    }

    public DashboardOverviewDto overview(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
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

        String countSql = """
                select count(*) as total,
                       sum(case when r.overall_sentiment_label = 'POS' then 1 else 0 end) as posCnt,
                       sum(case when r.overall_sentiment_label = 'NEU' then 1 else 0 end) as neuCnt,
                       sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from review r
                """ + where;

        CountRow countRow = jdbcTemplate.queryForObject(countSql, (rs, rowNum) -> new CountRow(
                rs.getLong("total"),
                rs.getLong("posCnt"),
                rs.getLong("neuCnt"),
                rs.getLong("negCnt")
        ), params.toArray());

        long total = countRow == null ? 0 : countRow.total();
        double posRate = countRow == null ? 0.0 : rate(countRow.posCnt(), total);
        double neuRate = countRow == null ? 0.0 : rate(countRow.neuCnt(), total);
        double negRate = countRow == null ? 0.0 : rate(countRow.negCnt(), total);

        String trendSql = """
                select date(coalesce(r.review_time, r.created_at)) as d,
                       count(*) as cnt,
                       sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from review r
                """ + where + """
                group by d
                order by d asc
                """;

        List<DashboardTrendPointDto> trend = jdbcTemplate.query(trendSql, (rs, rowNum) -> {
            long count = rs.getLong("cnt");
            long neg = rs.getLong("negCnt");
            return new DashboardTrendPointDto(
                    DATE_FORMAT.format(rs.getDate("d").toLocalDate()),
                    count,
                    rate(neg, count)
            );
        }, params.toArray());

        List<PriorityItemDto> topPriorities = decisionPriorityService.priorities(productId, start, end, 10).items();
        return new DashboardOverviewDto(total, posRate, neuRate, negRate, trend, topPriorities);
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private record CountRow(long total, long posCnt, long neuCnt, long negCnt) {}
}
