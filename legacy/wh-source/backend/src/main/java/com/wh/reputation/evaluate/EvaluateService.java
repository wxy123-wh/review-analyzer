package com.wh.reputation.evaluate;

import com.wh.reputation.analysis.AnalysisQueryService;
import com.wh.reputation.analysis.AspectAnalysisItemDto;
import com.wh.reputation.analysis.KeywordAnalysisService;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class EvaluateService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final AnalysisQueryService analysisQueryService;
    private final KeywordAnalysisService keywordAnalysisService;

    public EvaluateService(
            JdbcTemplate jdbcTemplate,
            AnalysisQueryService analysisQueryService,
            KeywordAnalysisService keywordAnalysisService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.analysisQueryService = analysisQueryService;
        this.keywordAnalysisService = keywordAnalysisService;
    }

    public BeforeAfterResponseDto beforeAfter(Long eventId) {
        if (eventId == null) {
            throw new BadRequestException("缺少事件编号");
        }

        EventRow event = loadEvent(eventId);

        LocalDate afterStart = event.startDate();
        LocalDate afterEnd = event.endDate();
        long days = ChronoUnit.DAYS.between(afterStart, afterEnd) + 1L;

        LocalDate beforeEnd = afterStart.minusDays(1);
        LocalDate beforeStart = afterStart.minusDays(days);

        WindowOverallStats beforeOverall = loadOverallStats(event.productId(), beforeStart, beforeEnd);
        WindowOverallStats afterOverall = loadOverallStats(event.productId(), afterStart, afterEnd);

        List<BeforeAfterAspectDto> beforeAspects = toAspectNegRates(analysisQueryService.aspects(event.productId(), beforeStart, beforeEnd).items());
        List<BeforeAfterAspectDto> afterAspects = toAspectNegRates(analysisQueryService.aspects(event.productId(), afterStart, afterEnd).items());

        List<KeywordChangeDto> keywordChanges = computeKeywordChanges(event.productId(), beforeStart, beforeEnd, afterStart, afterEnd, 20);

        return new BeforeAfterResponseDto(
                new BeforeAfterEventDto(event.id(), event.name(), event.type(), DATE_FORMAT.format(afterStart), DATE_FORMAT.format(afterEnd)),
                new BeforeAfterWindowDto(beforeOverall.reviewCount(), beforeOverall.negRate(), beforeAspects),
                new BeforeAfterWindowDto(afterOverall.reviewCount(), afterOverall.negRate(), afterAspects),
                keywordChanges
        );
    }

    private EventRow loadEvent(Long eventId) {
        List<EventRow> rows = jdbcTemplate.query("""
                        select e.id as id,
                               e.product_id as productId,
                               e.name as name,
                               e.type as type,
                               e.start_date as startDate,
                               e.end_date as endDate
                        from `event` e
                        where e.id = ?
                        """,
                (rs, rowNum) -> new EventRow(
                        rs.getLong("id"),
                        rs.getLong("productId"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getDate("startDate").toLocalDate(),
                        rs.getDate("endDate").toLocalDate()
                ),
                eventId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("未找到事件");
        }
        return rows.get(0);
    }

    private WindowOverallStats loadOverallStats(Long productId, LocalDate start, LocalDate end) {
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

        String sql = """
                select count(*) as total,
                       sum(case when r.overall_sentiment_label = 'NEG' then 1 else 0 end) as negCnt
                from review r
                """ + where;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            long total = rs.getLong("total");
            long neg = rs.getLong("negCnt");
            double negRate = total <= 0 ? 0.0 : (double) neg / total;
            return new WindowOverallStats(total, negRate);
        }, params.toArray());
    }

    private static List<BeforeAfterAspectDto> toAspectNegRates(List<AspectAnalysisItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(i -> i != null && i.aspectId() != null)
                .map(i -> new BeforeAfterAspectDto(i.aspectId(), i.negRate()))
                .sorted(Comparator.comparingLong(BeforeAfterAspectDto::aspectId))
                .toList();
    }

    private List<KeywordChangeDto> computeKeywordChanges(
            Long productId,
            LocalDate beforeStart,
            LocalDate beforeEnd,
            LocalDate afterStart,
            LocalDate afterEnd,
            int limit
    ) {
        Map<String, KeywordAnalysisService.KeywordFreq> beforeMap = keywordAnalysisService.computeStatsMap(productId, null, beforeStart, beforeEnd);
        Map<String, KeywordAnalysisService.KeywordFreq> afterMap = keywordAnalysisService.computeStatsMap(productId, null, afterStart, afterEnd);

        if (beforeMap.isEmpty() && afterMap.isEmpty()) {
            return List.of();
        }

        Set<String> keys = new HashSet<>();
        keys.addAll(beforeMap.keySet());
        keys.addAll(afterMap.keySet());

        List<KeywordChangeDto> items = new ArrayList<>();
        for (String k : keys) {
            int beforeFreq = Optional.ofNullable(beforeMap.get(k)).map(KeywordAnalysisService.KeywordFreq::freq).orElse(0);
            int afterFreq = Optional.ofNullable(afterMap.get(k)).map(KeywordAnalysisService.KeywordFreq::freq).orElse(0);
            int diff = afterFreq - beforeFreq;
            if (beforeFreq == 0 && afterFreq == 0) {
                continue;
            }
            items.add(new KeywordChangeDto(k, beforeFreq, afterFreq, diff));
        }

        return items.stream()
                .sorted((a, b) -> {
                    int c1 = Integer.compare(Math.abs(b.diff()), Math.abs(a.diff()));
                    if (c1 != 0) {
                        return c1;
                    }
                    int c2 = Integer.compare(b.afterFreq(), a.afterFreq());
                    if (c2 != 0) {
                        return c2;
                    }
                    int c3 = Integer.compare(b.keyword().length(), a.keyword().length());
                    if (c3 != 0) {
                        return c3;
                    }
                    return a.keyword().compareTo(b.keyword());
                })
                .limit(limit)
                .toList();
    }

    private record WindowOverallStats(long reviewCount, double negRate) {}

    private record EventRow(Long id, Long productId, String name, String type, LocalDate startDate, LocalDate endDate) {}
}
