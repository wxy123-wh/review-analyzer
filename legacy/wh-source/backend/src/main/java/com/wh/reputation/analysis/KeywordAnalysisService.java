package com.wh.reputation.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.persistence.AspectEntity;
import com.wh.reputation.persistence.AspectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KeywordAnalysisService {
    private final JdbcTemplate jdbcTemplate;
    private final AspectRepository aspectRepository;
    private final SentimentLexicon sentimentLexicon;
    private final Stopwords stopwords;
    private final ObjectMapper objectMapper;

    public KeywordAnalysisService(
            JdbcTemplate jdbcTemplate,
            AspectRepository aspectRepository,
            SentimentLexicon sentimentLexicon,
            Stopwords stopwords,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aspectRepository = aspectRepository;
        this.sentimentLexicon = sentimentLexicon;
        this.stopwords = stopwords;
        this.objectMapper = objectMapper;
    }

    public KeywordsResponseDto keywords(Long productId, Long aspectId, LocalDate start, LocalDate end, Integer topN) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        int limit = topN == null ? 20 : topN;
        if (limit < 1 || limit > 200) {
            throw new BadRequestException("关键词数量必须在 1 到 200 之间");
        }

        Map<String, KeywordFreq> stats = computeStatsMap(productId, aspectId, start, end);
        List<KeywordStatDto> items = stats.entrySet().stream()
                .map(e -> new KeywordStatDto(e.getKey(), e.getValue().freq(), e.getValue().negFreq()))
                .sorted((a, b) -> {
                    int c1 = Integer.compare(b.negFreq(), a.negFreq());
                    if (c1 != 0) {
                        return c1;
                    }
                    int c2 = Integer.compare(b.freq(), a.freq());
                    if (c2 != 0) {
                        return c2;
                    }
                    return a.keyword().compareTo(b.keyword());
                })
                .limit(limit)
                .toList();
        return new KeywordsResponseDto(items);
    }

    public Map<String, KeywordFreq> computeStatsMap(Long productId, Long aspectId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql;
        if (aspectId == null) {
            sql = new StringBuilder("""
                    select r.content_clean as contentClean,
                           r.overall_sentiment_label as sentimentLabel
                    from review r
                    where r.product_id = ?
                    """);
            params.add(productId);
        } else {
            sql = new StringBuilder("""
                    select r.content_clean as contentClean,
                           rar.sentiment_label as sentimentLabel
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

        List<ReviewContentRow> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ReviewContentRow(
                rs.getString("contentClean"),
                rs.getString("sentimentLabel")
        ), params.toArray());

        List<String> dictionary = buildDictionary();
        Set<String> stopwordSet = stopwords == null ? Set.of() : stopwords.words();

        Map<String, KeywordFreq> stats = new HashMap<>();
        for (ReviewContentRow row : rows) {
            Map<String, Integer> counts = KeywordExtractor.extractCounts(row.contentClean(), dictionary, stopwordSet);
            if (counts.isEmpty()) {
                continue;
            }
            boolean isNeg = "NEG".equalsIgnoreCase(row.sentimentLabel());
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                KeywordFreq freq = stats.computeIfAbsent(entry.getKey(), k -> new KeywordFreq());
                freq.add(entry.getValue(), isNeg);
            }
        }
        return stats;
    }

    private List<String> buildDictionary() {
        Set<String> dict = new HashSet<>();
        for (AspectEntity aspect : aspectRepository.findAll()) {
            addAspectKeywords(dict, aspect.getKeywordsJson());
        }
        if (sentimentLexicon != null) {
            dict.addAll(Optional.ofNullable(sentimentLexicon.pos()).orElse(List.of()));
            dict.addAll(Optional.ofNullable(sentimentLexicon.neg()).orElse(List.of()));
        }
        dict.removeIf(s -> s == null || s.isBlank());

        List<String> list = new ArrayList<>(dict);
        list.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return list;
    }

    private void addAspectKeywords(Set<String> dict, String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(keywordsJson);
        } catch (Exception e) {
            return;
        }
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                dict.add(item.asText());
                continue;
            }
            if (item.isObject()) {
                String kw = item.path("keyword").asText(null);
                if (kw != null && !kw.isBlank()) {
                    dict.add(kw);
                }
            }
        }
    }

    public static class KeywordFreq {
        private int freq;
        private int negFreq;

        public int freq() {
            return freq;
        }

        public int negFreq() {
            return negFreq;
        }

        void add(int count, boolean isNeg) {
            this.freq += count;
            if (isNeg) {
                this.negFreq += count;
            }
        }
    }

    private record ReviewContentRow(String contentClean, String sentimentLabel) {}
}
