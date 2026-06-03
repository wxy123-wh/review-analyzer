package com.wh.reputation.analysis;

import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisQueryService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public AnalysisQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AspectAnalysisResponseDto aspects(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        StringBuilder sql = new StringBuilder("""
                select a.id as aspectId,
                       a.name as aspectName,
                       sum(case when r.id is not null then 1 else 0 end) as volume,
                       sum(case when r.id is not null and rar.sentiment_label = 'POS' then 1 else 0 end) as posCnt,
                       sum(case when r.id is not null and rar.sentiment_label = 'NEU' then 1 else 0 end) as neuCnt,
                       sum(case when r.id is not null and rar.sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from aspect a
                left join review_aspect_result rar on rar.aspect_id = a.id
                left join review r on r.id = rar.review_id
                 and r.product_id = ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(productId);
        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        sql.append("""
                group by a.id, a.name
                order by a.id asc
                """);

        List<AspectAnalysisItemDto> items = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long volume = rs.getLong("volume");
            long posCnt = rs.getLong("posCnt");
            long neuCnt = rs.getLong("neuCnt");
            long negCnt = rs.getLong("negCnt");
            return new AspectAnalysisItemDto(
                    rs.getLong("aspectId"),
                    rs.getString("aspectName"),
                    volume,
                    rate(posCnt, volume),
                    rate(neuCnt, volume),
                    rate(negCnt, volume)
            );
        }, params.toArray());

        return new AspectAnalysisResponseDto(items);
    }

    public TrendResponseDto trend(Long productId, Long aspectId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        StringBuilder sql;
        List<Object> params = new ArrayList<>();
        if (aspectId == null) {
            sql = new StringBuilder("""
                    select date(coalesce(r.review_time, r.created_at)) as d,
                           count(*) as cnt,
                           sum(case when r.overall_sentiment_label = 'POS' then 1 else 0 end) as posCnt,
                           sum(case when r.overall_sentiment_label = 'NEU' then 1 else 0 end) as neuCnt,
                           sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                    from review r
                    where r.product_id = ?
                    """);
            params.add(productId);
        } else {
            sql = new StringBuilder("""
                    select date(coalesce(r.review_time, r.created_at)) as d,
                           count(*) as cnt,
                           sum(case when rar.sentiment_label = 'POS' then 1 else 0 end) as posCnt,
                           sum(case when rar.sentiment_label = 'NEU' then 1 else 0 end) as neuCnt,
                           sum(case when rar.sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                    from review_aspect_result rar
                    join review r on r.id = rar.review_id
                    where r.product_id = ?
                      and rar.aspect_id = ?
                    """);
            params.add(productId);
            params.add(aspectId);
        }

        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        sql.append("""
                group by d
                order by d asc
                """);

        List<TrendPointDto> series = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long count = rs.getLong("cnt");
            long pos = rs.getLong("posCnt");
            long neu = rs.getLong("neuCnt");
            long neg = rs.getLong("negCnt");
            double negRate = rate(neg, count);
            return new TrendPointDto(
                    DATE_FORMAT.format(rs.getDate("d").toLocalDate()),
                    count,
                    pos,
                    neu,
                    neg,
                    negRate
            );
        }, params.toArray());

        return new TrendResponseDto(series);
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }
}
