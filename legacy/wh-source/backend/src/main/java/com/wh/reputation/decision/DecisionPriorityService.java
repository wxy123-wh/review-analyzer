package com.wh.reputation.decision;

import com.wh.reputation.analysis.KeywordAnalysisService;
import com.wh.reputation.analysis.KeywordStatDto;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.persistence.AspectEntity;
import com.wh.reputation.persistence.AspectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DecisionPriorityService {
    private final JdbcTemplate jdbcTemplate;
    private final AspectRepository aspectRepository;
    private final KeywordAnalysisService keywordAnalysisService;

    public DecisionPriorityService(JdbcTemplate jdbcTemplate, AspectRepository aspectRepository, KeywordAnalysisService keywordAnalysisService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aspectRepository = aspectRepository;
        this.keywordAnalysisService = keywordAnalysisService;
    }

    public PriorityResponseDto priorities(Long productId, LocalDate start, LocalDate end, Integer topN) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        int limit = topN == null ? 10 : topN;
        if (limit < 1 || limit > 50) {
            throw new BadRequestException("数量必须在 1 到 50 之间");
        }

        DateWindow window = resolveWindow(productId, start, end);
        if (window == null) {
            return new PriorityResponseDto(List.of());
        }

        Map<Long, AspectNegStats> currentAspectStats = loadAspectNegStats(productId, window.currentStart(), window.currentEndExclusive());
        Map<Long, AspectNegStats> prevAspectStats = loadAspectNegStats(productId, window.prevStart(), window.prevEndExclusive());

        List<AspectEntity> aspects = aspectRepository.findAll().stream()
                .sorted(Comparator.comparingLong(AspectEntity::getId))
                .toList();

        List<PriorityItemDto> items = new ArrayList<>();
        for (AspectEntity aspect : aspects) {
            AspectNegStats cur = currentAspectStats.getOrDefault(aspect.getId(), new AspectNegStats(0, 0));
            AspectNegStats prev = prevAspectStats.getOrDefault(aspect.getId(), new AspectNegStats(0, 0));

            long volume = cur.volume();
            double negRate = cur.negRate();
            double growth = computeGrowth(negRate, prev.negRate());
            double priority = computePriority(negRate, growth, volume);
            items.add(new PriorityItemDto("ASPECT", aspect.getId(), aspect.getName(), priority, negRate, growth, volume));

            List<String> candidateKeywords = keywordAnalysisService.keywords(productId, aspect.getId(), window.currentStartDate(), window.currentEndDate(), 20)
                    .items().stream()
                    .map(KeywordStatDto::keyword)
                    .toList();
            if (candidateKeywords.isEmpty()) {
                continue;
            }

            Map<String, KeywordNegStats> currentKeywordStats = loadKeywordNegStats(productId, aspect.getId(), window.currentStartDate(), window.currentEndDate(), candidateKeywords);
            Map<String, KeywordNegStats> prevKeywordStats = loadKeywordNegStats(productId, aspect.getId(), window.prevStartDate(), window.prevEndDate(), candidateKeywords);
            for (String kw : candidateKeywords) {
                KeywordNegStats curKw = currentKeywordStats.getOrDefault(kw, new KeywordNegStats(0, 0));
                KeywordNegStats prevKw = prevKeywordStats.getOrDefault(kw, new KeywordNegStats(0, 0));
                long kwVolume = curKw.freq();
                double kwNegRate = curKw.negRate();
                double kwGrowth = computeGrowth(kwNegRate, prevKw.negRate());
                double kwPriority = computePriority(kwNegRate, kwGrowth, kwVolume);
                items.add(new PriorityItemDto("KEYWORD", aspect.getId(), kw, kwPriority, kwNegRate, kwGrowth, kwVolume));
            }
        }

        List<PriorityItemDto> top = items.stream()
                .filter(i -> i.volume() > 0)
                .sorted((a, b) -> Double.compare(b.priority(), a.priority()))
                .limit(limit)
                .toList();
        return new PriorityResponseDto(top);
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

        LocalDate currentStartDate = start == null ? minDate : start;
        LocalDate currentEndDate = end == null ? maxDate : end;
        if (currentEndDate.isBefore(currentStartDate)) {
            throw new BadRequestException("结束日期不能早于开始日期");
        }

        long days = ChronoUnit.DAYS.between(currentStartDate, currentEndDate) + 1;
        LocalDate prevEndDate = currentStartDate.minusDays(1);
        LocalDate prevStartDate = currentStartDate.minusDays(days);

        return new DateWindow(currentStartDate, currentEndDate, prevStartDate, prevEndDate);
    }

    private Map<Long, AspectNegStats> loadAspectNegStats(Long productId, LocalDateTime start, LocalDateTime endExclusive) {
        StringBuilder sql = new StringBuilder("""
                select rar.aspect_id as aspectId,
                       count(*) as volume,
                       sum(case when rar.sentiment_label = 'NEG' then 1 else 0 end) as negVolume
                from review_aspect_result rar
                join review r on r.id = rar.review_id
                where r.product_id = ?
                """);
        List<Object> params = new ArrayList<>();
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

        Map<Long, AspectNegStats> map = new HashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            long volume = rs.getLong("volume");
            long negVolume = rs.getLong("negVolume");
            map.put(rs.getLong("aspectId"), new AspectNegStats(volume, negVolume));
        }, params.toArray());
        return map;
    }

    private Map<String, KeywordNegStats> loadKeywordNegStats(Long productId, Long aspectId, LocalDate start, LocalDate end, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Map.of();
        }
        Map<String, KeywordAnalysisService.KeywordFreq> stats = keywordAnalysisService.computeStatsMap(productId, aspectId, start, end);
        Map<String, KeywordNegStats> map = new HashMap<>();
        for (String kw : keywords) {
            KeywordAnalysisService.KeywordFreq freq = stats.get(kw);
            if (freq == null) {
                continue;
            }
            map.put(kw, new KeywordNegStats(freq.freq(), freq.negFreq()));
        }
        return map;
    }

    private static double computeGrowth(double currentNegRate, double prevNegRate) {
        if (prevNegRate <= 0.0) {
            return 1.0;
        }
        return currentNegRate / prevNegRate;
    }

    private static double computePriority(double negRate, double growth, long volume) {
        if (volume <= 0) {
            return 0.0;
        }
        return negRate * growth * Math.log(1.0 + volume);
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

    private record AspectNegStats(long volume, long negVolume) {
        double negRate() {
            if (volume <= 0) {
                return 0.0;
            }
            return (double) negVolume / volume;
        }
    }

    private record KeywordNegStats(int freq, int negFreq) {
        double negRate() {
            if (freq <= 0) {
                return 0.0;
            }
            return (double) negFreq / freq;
        }
    }
}
