package com.wh.reputation.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.wh.reputation.common.DateRangeParser.parseDateOrNull;

@Service
public class ReviewQueryService {
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final com.wh.reputation.search.ReviewSearchRepository reviewSearchRepository;
    private final com.wh.reputation.search.EmbeddingService embeddingService;

    public ReviewQueryService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            com.wh.reputation.search.ReviewSearchRepository reviewSearchRepository,
            com.wh.reputation.search.EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.reviewSearchRepository = reviewSearchRepository;
        this.embeddingService = embeddingService;
    }

    public ReviewsPageDto list(
            Long productId,
            Long platformId,
            Long aspectId,
            String sentiment,
            String keyword,
            String start,
            String end,
            Integer page,
            Integer pageSize) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        int safePage = page == null ? 1 : page;
        int safePageSize = pageSize == null ? 20 : pageSize;
        if (safePage < 1) {
            throw new BadRequestException("页码必须从 1 开始");
        }
        if (safePageSize < 1 || safePageSize > 200) {
            throw new BadRequestException("每页数量必须在 1 到 200 之间");
        }

        LocalDate startDate = parseDateOrNull(start);
        LocalDate endDate = parseDateOrNull(end);
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        List<Long> semanticIds = null;
        if (keyword != null && !keyword.isBlank()) {
            // Remove keywords from SQL likes? For now we assume semantic search replaces
            // keyword matching if keyword is provided.
            try {
                float[] embeddingArray = embeddingService.generateEmbedding(keyword);
                List<Float> embeddingList = new ArrayList<>();
                for (float f : embeddingArray) {
                    embeddingList.add(f);
                }

                List<com.wh.reputation.search.ReviewDocument> docs;
                if (productId != null) {
                    docs = reviewSearchRepository.searchByVector(embeddingList, productId);
                } else {
                    docs = reviewSearchRepository.searchByVectorGlobal(embeddingList);
                }
                semanticIds = docs.stream().map(com.wh.reputation.search.ReviewDocument::getId).toList();

                // If semantic search yields nothing, we might want to return empty or fallback.
                // Here we return empty result effectively by passing empty ID list.
                if (semanticIds.isEmpty()) {
                    return new ReviewsPageDto(safePage, safePageSize, 0L, List.of());
                }
            } catch (Exception e) {
                // Log and ignore? Or fallback to LIKE?
                // For MVP, simple fallback to LIKE if ES fails is optional, but let's just
                // proceed to SQL LIKE if ES fails
                // effectively by keeping semanticIds null.
                // However, if ES succeeds but finds nothing, semanticIds is empty list.
            }
        }

        String where = buildReviewWhereClause(productId, platformId, aspectId, sentiment, keyword, startTime,
                endExclusive, params, semanticIds);

        long total = jdbcTemplate.queryForObject("select count(*) from review r where " + where, Long.class,
                params.toArray());
        int offset = (safePage - 1) * safePageSize;

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safePageSize);
        listParams.add(offset);

        String sql = """
                select r.id as id,
                pf.name as platformName,
                pr.name as productName,
                r.rating as rating,
                r.review_time as reviewTime,
                r.content_clean as contentClean,
                r.overall_sentiment_label as overallSentiment,
                r.overall_sentiment_score as overallScore
                from review r
                join platform pf on pf.id = r.platform_id
                join product pr on pr.id = r.product_id
                where %s
                order by r.id desc
                limit ? offset ?
                """.formatted(where);

        List<ReviewRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("platformName"),
                rs.getString("productName"),
                (Integer) rs.getObject("rating"),
                rs.getTimestamp("reviewTime"),
                rs.getString("contentClean"),
                rs.getString("overallSentiment"),
                rs.getDouble("overallScore")), listParams.toArray());

        List<Long> reviewIds = rows.stream().map(ReviewRow::id).toList();
        Map<Long, List<ReviewListAspectDto>> aspectsByReviewId = loadAspectsForReviews(reviewIds);

        List<ReviewListItemDto> items = rows.stream().map(row -> new ReviewListItemDto(
                row.id(),
                row.platformName(),
                row.productName(),
                row.rating(),
                formatTimestamp(row.reviewTime()),
                row.contentClean(),
                row.overallSentiment(),
                row.overallScore(),
                aspectsByReviewId.getOrDefault(row.id(), List.of()))).toList();

        return new ReviewsPageDto(safePage, safePageSize, total, items);
    }

    public ReviewDetailDto detail(Long id) {
        if (id == null) {
            throw new BadRequestException("缺少评论编号");
        }

        String sql = """
                select r.id as id,
                       pf.name as platformName,
                       pr.name as productName,
                       r.rating as rating,
                       r.review_time as reviewTime,
                       r.content_raw as contentRaw,
                       r.content_clean as contentClean,
                       r.overall_sentiment_label as overallSentiment,
                       r.overall_sentiment_score as overallScore
                from review r
                join platform pf on pf.id = r.platform_id
                join product pr on pr.id = r.product_id
                where r.id = ?
                """;

        List<ReviewDetailRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewDetailRow(
                rs.getLong("id"),
                rs.getString("platformName"),
                rs.getString("productName"),
                (Integer) rs.getObject("rating"),
                rs.getTimestamp("reviewTime"),
                rs.getString("contentRaw"),
                rs.getString("contentClean"),
                rs.getString("overallSentiment"),
                rs.getDouble("overallScore")), id);

        if (rows.isEmpty()) {
            throw new NotFoundException("未找到评论");
        }

        ReviewDetailRow row = rows.get(0);
        List<ReviewAspectResultDto> aspectResults = loadAspectResults(id);
        return new ReviewDetailDto(
                row.id(),
                row.platformName(),
                row.productName(),
                row.rating(),
                formatTimestamp(row.reviewTime()),
                row.contentRaw(),
                row.contentClean(),
                row.overallSentiment(),
                row.overallScore(),
                aspectResults);
    }

    private Map<Long, List<ReviewListAspectDto>> loadAspectsForReviews(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(reviewIds.size(), "?"));
        String sql = """
                select rar.review_id as reviewId,
                       rar.aspect_id as aspectId,
                       a.name as aspectName,
                       rar.sentiment_label as sentiment,
                       rar.sentiment_score as score
                from review_aspect_result rar
                join aspect a on a.id = rar.aspect_id
                where rar.review_id in (%s)
                order by rar.review_id asc, rar.aspect_id asc
                """.formatted(placeholders);

        Map<Long, List<ReviewListAspectDto>> result = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            Long reviewId = rs.getLong("reviewId");
            ReviewListAspectDto dto = new ReviewListAspectDto(
                    rs.getLong("aspectId"),
                    rs.getString("aspectName"),
                    rs.getString("sentiment"),
                    rs.getDouble("score"));
            result.computeIfAbsent(reviewId, k -> new ArrayList<>()).add(dto);
        }, reviewIds.toArray());
        return result;
    }

    private List<ReviewAspectResultDto> loadAspectResults(Long reviewId) {
        String sql = """
                select rar.aspect_id as aspectId,
                       a.name as aspectName,
                       rar.hit_keywords_json as hitKeywordsJson,
                       rar.sentiment_label as sentiment,
                       rar.sentiment_score as score,
                       rar.confidence as confidence
                from review_aspect_result rar
                join aspect a on a.id = rar.aspect_id
                where rar.review_id = ?
                order by rar.aspect_id asc
                """;

        List<ReviewAspectResultDto> items = new ArrayList<>();
        jdbcTemplate.query(sql, (rs) -> {
            List<String> hitKeywords = parseJsonArray(rs.getString("hitKeywordsJson"));
            items.add(new ReviewAspectResultDto(
                    rs.getLong("aspectId"),
                    rs.getString("aspectName"),
                    hitKeywords,
                    rs.getString("sentiment"),
                    rs.getDouble("score"),
                    rs.getDouble("confidence")));
        }, reviewId);
        return items;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String buildReviewWhereClause(
            Long productId,
            Long platformId,
            Long aspectId,
            String sentiment,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endExclusive,
            List<Object> params,
            List<Long> semanticIds) {
        StringBuilder sb = new StringBuilder("r.product_id = ?");
        params.add(productId);

        if (platformId != null) {
            sb.append(" and r.platform_id = ?");
            params.add(platformId);
        }
        if (aspectId != null) {
            sb.append(
                    " and exists (select 1 from review_aspect_result rar where rar.review_id = r.id and rar.aspect_id = ?)");
            params.add(aspectId);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            sb.append(" and r.overall_sentiment_label = ?");
            params.add(sentiment.trim().toUpperCase(Locale.ROOT));
        }

        if (semanticIds != null) {
            // Semantic search mode
            if (semanticIds.isEmpty()) {
                sb.append(" and 1=0"); // Should have been handled before, but for safety
            } else {
                sb.append(" and r.id in (");
                for (int i = 0; i < semanticIds.size(); i++) {
                    if (i > 0)
                        sb.append(",");
                    sb.append("?");
                    params.add(semanticIds.get(i));
                }
                sb.append(")");
            }
        } else if (keyword != null && !keyword.isBlank()) {
            // Fallback to legacy keyword search if semantic search didn't run (e.g. error)
            // or not applicable
            sb.append(" and r.content_clean like ?");
            params.add("%" + keyword.trim() + "%");
        }

        if (startTime != null) {
            sb.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sb.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        return sb.toString();
    }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return DATETIME_FORMAT.format(ts.toLocalDateTime());
    }

    private record ReviewRow(
            Long id,
            String platformName,
            String productName,
            Integer rating,
            Timestamp reviewTime,
            String contentClean,
            String overallSentiment,
            double overallScore) {
    }

    private record ReviewDetailRow(
            Long id,
            String platformName,
            String productName,
            Integer rating,
            Timestamp reviewTime,
            String contentRaw,
            String contentClean,
            String overallSentiment,
            double overallScore) {
    }
}
