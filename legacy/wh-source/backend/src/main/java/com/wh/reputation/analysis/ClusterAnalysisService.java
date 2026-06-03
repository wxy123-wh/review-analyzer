package com.wh.reputation.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ClusterAnalysisService {

    private static final int MAX_K = 8;

    private static final int REPRESENTATIVE_REVIEWS = 5;

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final TokenizationService tokenizationService;
    private final ObjectMapper objectMapper;
    private final NlpClient nlpClient;

    public ClusterAnalysisService(JdbcTemplate jdbcTemplate, TokenizationService tokenizationService,
            ObjectMapper objectMapper, NlpClient nlpClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizationService = tokenizationService;
        this.objectMapper = objectMapper;
        this.nlpClient = nlpClient;
    }

    @Transactional
    public ClustersResponseDto clusters(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        if (!productExists(productId)) {
            throw new NotFoundException("产品不存在：" + productId);
        }

        ClustersResponseDto cached = loadLatest(productId, start, end);
        if (cached != null && cached.items() != null && !cached.items().isEmpty()) {
            return cached;
        }

        return recompute(productId, start, end);
    }

    @Transactional
    public ClustersResponseDto recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        if (!productExists(productId)) {
            throw new NotFoundException("产品不存在：" + productId);
        }

        List<ReviewRow> rows = loadReviewRows(productId, start, end);
        if (rows.isEmpty()) {
            return new ClustersResponseDto(List.of());
        }

        List<NlpClient.ReviewDoc> docs = new ArrayList<>(rows.size());
        for (ReviewRow row : rows) {
            List<String> tokens = parseTokensOrTokenize(row.tokensJson(), row.contentClean());
            docs.add(new NlpClient.ReviewDoc(row.id(), row.contentClean(), tokens, row.sentimentLabel()));
        }

        List<NlpClient.ClusterItem> results = nlpClient.cluster(docs, 2, MAX_K);
        if (results.isEmpty()) {
            return new ClustersResponseDto(List.of());
        }

        LocalDateTime createdAt = LocalDateTime.now();
        Timestamp createdAtTs = Timestamp.valueOf(createdAt);
        int finalK = results.size();

        List<ClusterListItemDto> responseItems = new ArrayList<>(results.size());
        for (NlpClient.ClusterItem result : results) {
            String topTermsJson = toJson(result.topTerms());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        """
                                insert into `cluster` (product_id, start_date, end_date, k, top_terms_json, size, neg_rate, created_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, productId);
                ps.setObject(2, toSqlDate(start));
                ps.setObject(3, toSqlDate(end));
                ps.setInt(4, finalK);
                ps.setString(5, topTermsJson);
                ps.setInt(6, result.reviewIds().size());
                ps.setDouble(7, result.negRate());
                ps.setTimestamp(8, createdAtTs);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("写入聚类结果失败");
            }
            long clusterId = key.longValue();

            if (!result.reviewIds().isEmpty()) {
                List<Object[]> mappingArgs = new ArrayList<>(result.reviewIds().size());
                for (Long reviewId : result.reviewIds()) {
                    mappingArgs.add(new Object[] { reviewId, clusterId, createdAtTs });
                }
                jdbcTemplate.batchUpdate("""
                        insert into review_cluster (review_id, cluster_id, created_at)
                        values (?, ?, ?)
                        """,
                        mappingArgs);
            }

            List<Long> repIds = result.reviewIds().subList(0,
                    Math.min(REPRESENTATIVE_REVIEWS, result.reviewIds().size()));
            responseItems.add(new ClusterListItemDto(clusterId, result.topTerms(), result.reviewIds().size(),
                    result.negRate(), repIds));
        }

        return new ClustersResponseDto(responseItems);
    }

    public ClusterDetailResponseDto clusterDetail(Long id) {
        if (id == null) {
            throw new BadRequestException("缺少聚类编号");
        }

        String sql = """
                select c.id as id,
                       c.top_terms_json as topTermsJson,
                       c.size as size,
                       c.neg_rate as negRate
                from `cluster` c
                where c.id = ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("未找到聚类");
        }
        Map<String, Object> row = rows.get(0);
        String topTermsJson = (String) row.get("topTermsJson");
        List<String> topTerms = parseJsonArray(topTermsJson);
        int size = ((Number) row.get("size")).intValue();
        double negRate = ((Number) row.get("negRate")).doubleValue();

        List<ClusterRepresentativeReviewDto> reps = loadRepresentativeReviews(id);
        return new ClusterDetailResponseDto(id, topTerms, size, negRate, reps);
    }

    private List<ClusterRepresentativeReviewDto> loadRepresentativeReviews(Long clusterId) {
        String sql = """
                select r.id as id,
                       coalesce(r.review_time, r.created_at) as reviewTime,
                       r.content_clean as contentClean,
                       r.overall_sentiment_label as overallSentiment
                from review_cluster rc
                join review r on r.id = rc.review_id
                where rc.cluster_id = ?
                order by rc.id asc
                limit 50
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("reviewTime");
            String time = ts == null ? null : DATETIME_FORMAT.format(ts.toLocalDateTime());
            return new ClusterRepresentativeReviewDto(
                    rs.getLong("id"),
                    time,
                    rs.getString("contentClean"),
                    rs.getString("overallSentiment"));
        }, clusterId);
    }

    private ClustersResponseDto loadLatest(Long productId, LocalDate start, LocalDate end) {
        String maxSql = """
                select max(c.created_at) as createdAt
                from `cluster` c
                where c.product_id = ?
                and c.start_date <=> ?
                and c.end_date <=> ?
                """;

        Timestamp createdAt = jdbcTemplate.query(maxSql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return rs.getTimestamp("createdAt");
        }, productId, toSqlDate(start), toSqlDate(end));

        if (createdAt == null) {
            return null;
        }

        String listSql = """
                select c.id as id,
                       c.top_terms_json as topTermsJson,
                       c.size as size,
                       c.neg_rate as negRate
                from `cluster` c
                where c.product_id = ?
                and c.start_date <=> ?
                and c.end_date <=> ?
                and c.created_at = ?
                order by c.id asc
                """;

        List<ClusterRow> clusters = jdbcTemplate.query(listSql, (rs, rowNum) -> new ClusterRow(
                rs.getLong("id"),
                rs.getString("topTermsJson"),
                rs.getInt("size"),
                rs.getDouble("negRate")), productId, toSqlDate(start), toSqlDate(end), createdAt);

        if (clusters.isEmpty()) {
            return null;
        }

        List<Long> clusterIds = clusters.stream().map(ClusterRow::id).toList();
        Map<Long, List<Long>> representativeIds = loadRepresentativeIds(clusterIds);

        List<ClusterListItemDto> items = clusters.stream()
                .map(c -> new ClusterListItemDto(
                        c.id(),
                        parseJsonArray(c.topTermsJson()),
                        c.size(),
                        c.negRate(),
                        representativeIds.getOrDefault(c.id(), List.of())))
                .toList();

        return new ClustersResponseDto(items);
    }

    private boolean productExists(Long productId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from product where id = ?",
                Integer.class,
                productId);
        return count != null && count > 0;
    }

    private Map<Long, List<Long>> loadRepresentativeIds(List<Long> clusterIds) {
        if (clusterIds == null || clusterIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(clusterIds.size(), "?"));
        String sql = """
                select t.cluster_id as clusterId,
                       t.review_id as reviewId
                from (
                    select rc.cluster_id,
                           rc.review_id,
                           row_number() over (partition by rc.cluster_id order by rc.id asc) as rn
                    from review_cluster rc
                    where rc.cluster_id in (%s)
                ) t
                where t.rn <= %d
                order by t.cluster_id asc, t.rn asc
                """.formatted(placeholders, REPRESENTATIVE_REVIEWS);

        Map<Long, List<Long>> map = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            long clusterId = rs.getLong("clusterId");
            long reviewId = rs.getLong("reviewId");
            map.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(reviewId);
        }, clusterIds.toArray());
        return map;
    }

    private List<ReviewRow> loadReviewRows(Long productId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson,
                       r.overall_sentiment_label as sentimentLabel
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
                rs.getString("tokensJson"),
                rs.getString("sentimentLabel")), params.toArray());
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

    private record ClusterRow(Long id, String topTermsJson, int size, double negRate) {
    }

    private record ReviewRow(Long id, String contentClean, String tokensJson, String sentimentLabel) {
    }

}
