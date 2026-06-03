package com.wh.reputation.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.analysis.Stopwords;
import com.wh.reputation.analysis.TokenizationService;
import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class WordcloudService {
    private static final int DEFAULT_TOP_N = 80;
    private static final int MAX_TOP_N = 200;
    private static final int MAX_SCAN_REVIEWS = 20_000;

    private static final Pattern VALID_TOKEN = Pattern.compile("^[\\p{IsHan}A-Za-z0-9]+$");
    private static final Pattern ONLY_NUMBER = Pattern.compile("^\\d+$");

    private static final Set<String> BUILTIN_STOPWORDS = Set.of(
            "一个", "一些", "一种", "一样", "一直", "一般", "不会", "不太", "不是", "不了", "不错", "不行", "不过", "不然", "不算",
            "东西", "两个", "个人", "今天", "以前", "但是", "作为", "使用", "比如", "比较", "这个", "那个", "这样", "那样", "这些",
            "觉得", "感觉", "还是", "还有", "然后", "因为", "所以", "如果", "已经", "可以", "可能", "没有", "起来", "一下",
            "非常", "特别", "基本", "就是", "时候", "现在", "之前", "之后", "整体", "体验", "推荐", "购买", "入手", "下单",
            "产品", "商品", "客服", "快递", "物流", "包装"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TokenizationService tokenizationService;
    private final Stopwords stopwords;
    private final ObjectMapper objectMapper;

    public WordcloudService(
            JdbcTemplate jdbcTemplate,
            TokenizationService tokenizationService,
            Stopwords stopwords,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizationService = tokenizationService;
        this.stopwords = stopwords;
        this.objectMapper = objectMapper;
    }

    public WordcloudResponseDto wordcloud(
            Long productId,
            LocalDate start,
            LocalDate end,
            Long aspectId,
            String sentiment,
            Integer topN
    ) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        int limit = topN == null ? DEFAULT_TOP_N : topN;
        if (limit < 1 || limit > MAX_TOP_N) {
            throw new BadRequestException("关键词数量必须在 1 到 " + MAX_TOP_N + " 之间");
        }
        String sentimentFilter = normalizeSentimentOrNull(sentiment);

        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<ReviewRow> rows;
        if (aspectId == null) {
            rows = loadReviewRows(productId, startTime, endExclusive, sentimentFilter, MAX_SCAN_REVIEWS);
        } else {
            List<Long> reviewIds = loadReviewIdsByAspect(productId, aspectId, startTime, endExclusive, sentimentFilter, MAX_SCAN_REVIEWS);
            rows = loadReviewRowsByIds(reviewIds);
        }

        Set<String> stopwordSet = buildStopwordSet();
        Map<String, Integer> freq = new HashMap<>();
        for (ReviewRow row : rows) {
            List<String> tokens = parseTokensOrTokenize(row.tokensJson(), row.contentClean());
            if (tokens.isEmpty()) {
                continue;
            }
            for (String token : tokens) {
                String normalized = normalizeToken(token);
                if (normalized.isBlank()) {
                    continue;
                }
                if (stopwordSet.contains(normalized)) {
                    continue;
                }
                freq.merge(normalized, 1, Integer::sum);
            }
        }

        List<WordcloudItemDto> items = freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c1 = Integer.compare(b.getValue(), a.getValue());
                    if (c1 != 0) {
                        return c1;
                    }
                    int c2 = Integer.compare(b.getKey().length(), a.getKey().length());
                    if (c2 != 0) {
                        return c2;
                    }
                    return a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .map(e -> new WordcloudItemDto(e.getKey(), e.getValue()))
                .toList();

        return new WordcloudResponseDto(items, new WordcloudMetaDto(limit, rows.size()));
    }

    private List<ReviewRow> loadReviewRows(
            Long productId,
            LocalDateTime startTime,
            LocalDateTime endExclusive,
            String sentimentLabel,
            int limit
    ) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson
                from review r
                where r.product_id = ?
                """);
        params.add(productId);

        if (sentimentLabel != null) {
            sql.append(" and r.overall_sentiment_label = ?");
            params.add(sentimentLabel);
        }
        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        // MVP: load limited rows and aggregate in Java.
        // TODO(perf): For MySQL 8, this can be pushed down via JSON_TABLE(tokens_json, '$[*]') + GROUP BY token.
        sql.append(" order by r.id desc limit ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("contentClean"),
                rs.getString("tokensJson")
        ), params.toArray());
    }

    private List<Long> loadReviewIdsByAspect(
            Long productId,
            Long aspectId,
            LocalDateTime startTime,
            LocalDateTime endExclusive,
            String sentimentLabel,
            int limit
    ) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select distinct r.id as id
                from review_aspect_result rar
                join review r on r.id = rar.review_id
                where r.product_id = ?
                  and rar.aspect_id = ?
                """);
        params.add(productId);
        params.add(aspectId);

        if (sentimentLabel != null) {
            sql.append(" and rar.sentiment_label = ?");
            params.add(sentimentLabel);
        }
        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        sql.append(" order by r.id desc limit ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("id"), params.toArray());
    }

    private List<ReviewRow> loadReviewRowsByIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(reviewIds.size(), "?"));
        String sql = """
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson
                from review r
                where r.id in (%s)
                """.formatted(placeholders);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("contentClean"),
                rs.getString("tokensJson")
        ), reviewIds.toArray());
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
            List<String> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Set<String> buildStopwordSet() {
        Set<String> words = new HashSet<>();
        if (stopwords != null && stopwords.words() != null) {
            words.addAll(stopwords.words());
        }
        words.addAll(BUILTIN_STOPWORDS);
        words.removeIf(s -> s == null || s.isBlank());
        return words;
    }

    private static String normalizeSentimentOrNull(String sentiment) {
        if (sentiment == null || sentiment.isBlank()) {
            return null;
        }
        String normalized = sentiment.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POS", "NEG", "NEU" -> normalized;
            default -> throw new BadRequestException("情感参数不正确");
        };
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (!VALID_TOKEN.matcher(trimmed).matches()) {
            return "";
        }
        if (trimmed.length() == 1) {
            int cp = trimmed.codePointAt(0);
            if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN) {
                return "";
            }
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (ONLY_NUMBER.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }

    private record ReviewRow(Long id, String contentClean, String tokensJson) {}
}
