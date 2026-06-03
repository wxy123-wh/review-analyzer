package com.wh.reputation.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TopicAnalysisService {
    private static final int DEFAULT_TOPIC_COUNT = 3;

    private final JdbcTemplate jdbcTemplate;
    private final TokenizationService tokenizationService;
    private final ObjectMapper objectMapper;
    private final NlpClient nlpClient;

    public TopicAnalysisService(JdbcTemplate jdbcTemplate, TokenizationService tokenizationService,
            ObjectMapper objectMapper, NlpClient nlpClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizationService = tokenizationService;
        this.objectMapper = objectMapper;
        this.nlpClient = nlpClient;
    }

    @Transactional
    public TopicsResponseDto topics(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        TopicsResponseDto cached = loadLatest(productId, start, end);
        if (cached != null && cached.topicCount() > 0) {
            return cached;
        }

        return recompute(productId, start, end);
    }

    @Transactional
    public TopicsResponseDto recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        List<ReviewRow> rows = loadReviewRows(productId, start, end);
        if (rows.isEmpty()) {
            return new TopicsResponseDto(0, List.of());
        }

        List<NlpClient.ReviewDoc> docs = new ArrayList<>(rows.size());
        for (ReviewRow row : rows) {
            List<String> tokens = parseTokensOrTokenize(row.tokensJson(), row.contentClean());
            docs.add(new NlpClient.ReviewDoc(row.id(), row.contentClean(), tokens, null));
        }

        List<NlpClient.TopicItem> results = nlpClient.topics(docs, DEFAULT_TOPIC_COUNT);
        if (results.isEmpty()) {
            return new TopicsResponseDto(0, List.of());
        }

        String topicsJson = toJson(results);
        jdbcTemplate.update("""
                insert into topic_result (product_id, start_date, end_date, topic_count, topics_json, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                productId,
                toSqlDate(start),
                toSqlDate(end),
                results.size(),
                topicsJson,
                Timestamp.valueOf(LocalDateTime.now()));

        // Map NlpClient.TopicItem to internal TopicItemDto if needed, but assuming they
        // align enough or I can just use existing DTO
        // Actually TopicItemDto in file matches the structure (id, keyWords, weight,
        // evidenceIds) roughly.
        // NlpClient.TopicItem has topicId, keyWords, weight, evidenceIds.
        // Existing TopicItemDto: (int topicId, List<String> topWords, double weight,
        // List<Long> evidenceReviewIds)

        List<TopicItemDto> responseItems = results.stream()
                .map(t -> new TopicItemDto(t.topicId(), t.keyWords(), t.weight(), t.evidenceIds()))
                .toList();

        return new TopicsResponseDto(responseItems.size(), responseItems);
    }

    private TopicsResponseDto loadLatest(Long productId, LocalDate start, LocalDate end) {
        String sql = """
                select tr.topic_count as topicCount,
                       tr.topics_json as topicsJson
                from topic_result tr
                where tr.product_id = ?
                  and tr.start_date <=> ?
                  and tr.end_date <=> ?
                order by tr.created_at desc
                limit 1
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, productId, toSqlDate(start), toSqlDate(end));
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Integer topicCount = (Integer) row.get("topicCount");
        String topicsJson = (String) row.get("topicsJson");
        if (topicCount == null || topicCount <= 0 || topicsJson == null || topicsJson.isBlank()) {
            return null;
        }

        List<TopicItemDto> items;
        try {
            items = objectMapper.readValue(topicsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }
        return new TopicsResponseDto(topicCount, items == null ? List.of() : items);
    }

    private List<ReviewRow> loadReviewRows(Long productId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson
                from review r
                where r.product_id = ?
                """);
        params.add(productId);

        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        sql.append(" order by r.id asc");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("contentClean"),
                rs.getString("tokensJson")), params.toArray());
    }

    private List<String> parseTokensOrTokenize(String tokensJson, String contentClean) {
        List<String> tokens = parseJsonArray(tokensJson);
        if (!tokens.isEmpty()) {
            return tokens;
        }
        return tokenizationService.tokenize(contentClean);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(json, new TypeReference<>() {
            });
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("数据序列化失败", e);
        }
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private record ReviewRow(Long id, String contentClean, String tokensJson) {
    }

}
