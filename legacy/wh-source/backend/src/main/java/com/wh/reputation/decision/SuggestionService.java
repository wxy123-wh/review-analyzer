package com.wh.reputation.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.analysis.AnalysisQueryService;
import com.wh.reputation.analysis.AspectAnalysisItemDto;
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
public class SuggestionService {
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_EVIDENCE = 3;

    private final JdbcTemplate jdbcTemplate;
    private final AnalysisQueryService analysisQueryService;
    private final ObjectMapper objectMapper;

    public SuggestionService(JdbcTemplate jdbcTemplate, AnalysisQueryService analysisQueryService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.analysisQueryService = analysisQueryService;
        this.objectMapper = objectMapper;
    }

    public SuggestionsResponseDto suggestions(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        List<SuggestionItemDto> items = loadSuggestions(productId);
        if (!items.isEmpty()) {
            return new SuggestionsResponseDto(items);
        }

        recompute(productId, start, end);
        return new SuggestionsResponseDto(loadSuggestions(productId));
    }

    @Transactional
    public void recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }

        jdbcTemplate.update("delete from suggestion_instance where product_id = ?", productId);

        List<AspectAnalysisItemDto> aspects = analysisQueryService.aspects(productId, start, end).items();
        if (aspects == null || aspects.isEmpty()) {
            return;
        }

        List<AspectAnalysisItemDto> candidates = aspects.stream()
                .filter(a -> a != null && a.aspectId() != null)
                .filter(a -> a.volume() > 0)
                .sorted((a, b) -> {
                    int c1 = Double.compare(b.negRate(), a.negRate());
                    if (c1 != 0) {
                        return c1;
                    }
                    int c2 = Long.compare(b.volume(), a.volume());
                    if (c2 != 0) {
                        return c2;
                    }
                    return Long.compare(a.aspectId(), b.aspectId());
                })
                .limit(MAX_SUGGESTIONS)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now());
        for (AspectAnalysisItemDto a : candidates) {
            List<SuggestionEvidenceDto> evidence = loadEvidence(productId, a.aspectId(), start, end);
            if (evidence.isEmpty()) {
                continue;
            }

            String text = buildSuggestionText(a.aspectName());
            jdbcTemplate.update("""
                            insert into suggestion_instance (product_id, ref_type, ref_id, suggestion_text, evidence_json, created_at)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    productId,
                    "ASPECT",
                    a.aspectId(),
                    text,
                    toJson(evidence),
                    createdAt
            );
        }
    }

    private List<SuggestionItemDto> loadSuggestions(Long productId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        select si.id as id,
                               si.ref_type as refType,
                               si.ref_id as refId,
                               si.suggestion_text as suggestionText,
                               si.evidence_json as evidenceJson
                        from suggestion_instance si
                        where si.product_id = ?
                        order by si.created_at desc, si.id desc
                        limit 50
                        """,
                productId
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        List<SuggestionItemDto> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String refType = (String) row.get("refType");
            Long refId = ((Number) row.get("refId")).longValue();
            String suggestionText = (String) row.get("suggestionText");
            String evidenceJson = (String) row.get("evidenceJson");
            List<SuggestionEvidenceDto> evidence = parseEvidence(evidenceJson);
            items.add(new SuggestionItemDto(id, refType, refId, suggestionText, evidence));
        }
        return items;
    }

    private List<SuggestionEvidenceDto> loadEvidence(Long productId, Long aspectId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as reviewId,
                       r.content_clean as contentClean
                from review_aspect_result rar
                join review r on r.id = rar.review_id
                where r.product_id = ?
                  and rar.aspect_id = ?
                """);
        params.add(productId);
        params.add(aspectId);

        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }

        sql.append(" and rar.sentiment_label = 'NEG'");
        sql.append(" order by coalesce(r.review_time, r.created_at) desc, r.id desc limit ").append(MAX_EVIDENCE);

        List<SuggestionEvidenceDto> evidence = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SuggestionEvidenceDto(
                rs.getLong("reviewId"),
                truncate(rs.getString("contentClean"), 80),
                "维度负向代表评论"
        ), params.toArray());
        if (!evidence.isEmpty()) {
            return evidence;
        }

        return jdbcTemplate.query("""
                        select r.id as reviewId,
                               r.content_clean as contentClean
                        from review_aspect_result rar
                        join review r on r.id = rar.review_id
                        where r.product_id = ?
                          and rar.aspect_id = ?
                        order by coalesce(r.review_time, r.created_at) desc, r.id desc
                        limit ?
                        """,
                (rs, rowNum) -> new SuggestionEvidenceDto(
                        rs.getLong("reviewId"),
                        truncate(rs.getString("contentClean"), 80),
                        "维度代表评论"
                ),
                productId,
                aspectId,
                MAX_EVIDENCE
        );
    }

    private String buildSuggestionText(String aspectName) {
        String name = aspectName == null ? "" : aspectName.trim();
        if (name.isBlank()) {
            return "建议优先优化高负向反馈模块，降低负向率。";
        }
        return "建议优先优化「" + name + "」体验，聚焦高频负向反馈，降低负向率。";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    private List<SuggestionEvidenceDto> parseEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<SuggestionEvidenceDto> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() <= maxLen) {
            return v;
        }
        return v.substring(0, maxLen) + "...";
    }
}
