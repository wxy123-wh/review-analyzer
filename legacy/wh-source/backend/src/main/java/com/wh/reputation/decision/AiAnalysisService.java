package com.wh.reputation.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.ai.OpenAiChatRequest;
import com.wh.reputation.ai.OpenAiChatResponse;
import com.wh.reputation.ai.OpenAiClient;
import com.wh.reputation.ai.OpenAiConfig;
import com.wh.reputation.analysis.ClusterAnalysisService;
import com.wh.reputation.analysis.ClusterRepresentativeReviewDto;
import com.wh.reputation.common.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final ClusterAnalysisService clusterAnalysisService;
    private final OpenAiClient openAiClient;
    private final OpenAiConfig openAiConfig;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(
            ClusterAnalysisService clusterAnalysisService,
            OpenAiClient openAiClient,
            OpenAiConfig openAiConfig,
            ObjectMapper objectMapper) {
        this.clusterAnalysisService = clusterAnalysisService;
        this.openAiClient = openAiClient;
        this.openAiConfig = openAiConfig;
        this.objectMapper = objectMapper;
    }

    public AiSummaryResponseDto analyzeCluster(Long clusterId) {
        if (clusterId == null) {
            throw new BadRequestException("缺少聚类编号");
        }

        // Load representative reviews from cluster
        List<ClusterRepresentativeReviewDto> reviews = clusterAnalysisService.clusterDetail(clusterId)
                .representativeReviews();

        if (reviews == null || reviews.isEmpty()) {
            throw new BadRequestException("该聚类没有代表性评论");

        }

        // Build prompt with review texts
        String reviewTexts = reviews.stream()
                .map(ClusterRepresentativeReviewDto::contentClean)
                .map(content -> "- " + content)
                .collect(Collectors.joining("\n"));

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(reviewTexts);

        // Call OpenAI API
        OpenAiChatRequest request = OpenAiChatRequest.create(
                openAiConfig.getModel(),
                systemPrompt,
                userPrompt);

        OpenAiChatResponse response = openAiClient.chatCompletion(request);
        String content = response.getContent();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI 返回了空响应");
        }

        // Parse JSON response
        return parseAiResponse(content);
    }

    private String buildSystemPrompt() {
        return """
                你是一位资深产品经理，负责分析客户反馈。
                你的任务是从一组产品评论中识别核心质量问题，并提供具体的研发优化建议。

                请分析以下评论，并返回一个符合以下格式的 JSON 响应：
                {
                  "summary": "这些评论反映的主要质量问题的简洁描述",
                  "actionItems": ["具体可执行的优化建议 1", "具体可执行的优化建议 2", ...]
                }

                要求：
                1. 简洁、专业，聚焦于可执行的见解
                2. 用中文回答
                3. actionItems 应该包含 3-5 条具体建议
                4. 只返回 JSON，不要包含其他文字
                """;
    }

    private String buildUserPrompt(String reviewTexts) {
        return "请分析以下用户评论：\n\n" + reviewTexts;
    }

    private AiSummaryResponseDto parseAiResponse(String content) {
        try {
            // Extract JSON from markdown code blocks if present
            String jsonContent = content.strip();
            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.substring("```json".length());
            }
            if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.substring("```".length());
            }
            if (jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
            }
            jsonContent = jsonContent.strip();

            log.debug("Parsing AI response: {}", jsonContent);
            return objectMapper.readValue(jsonContent, AiSummaryResponseDto.class);
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", content, e);
            throw new IllegalStateException("解析 AI 响应失败: " + e.getMessage(), e);
        }
    }
}
