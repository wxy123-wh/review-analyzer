package com.wh.review.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.ValidationResponse;
import com.wh.review.backend.dto.IssueListResponse;
import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.dto.WordCloudResponse;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedCompareAspectRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedIssueRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedTrendReviewRecord;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.MaterializedWordCloudReviewRecord;
import com.wh.review.backend.persistence.ActionRepository.ActionValidationContext;
import com.wh.review.backend.persistence.ValidationMetricsRepository;
import com.wh.review.backend.persistence.ValidationMetricsRepository.MetricsPayload;
import com.wh.review.backend.persistence.ValidationMetricsRepository.ValidationSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightQueryServiceTest {

    @Mock
    private ActionService actionService;

    @Mock
    private AnalysisMaterializationRepository analysisMaterializationRepository;

    @Mock
    private DemoReviewAggregationService demoReviewAggregationService;

    @Mock
    private ValidationMetricsRepository validationMetricsRepository;

    private InsightQueryService insightQueryService;

    @BeforeEach
    void setUp() {
        insightQueryService = new InsightQueryService(
                actionService,
                analysisMaterializationRepository,
                demoReviewAggregationService,
                validationMetricsRepository
        );
    }

    @Test
    void listIssuesShouldReadMaterializedIssueRows() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.aspectDisplayName("battery")).thenReturn("续航");
        when(analysisMaterializationRepository.findIssues("demo-earphone")).thenReturn(List.of(
                new MaterializedIssueRecord(7L, "battery", "续航体验波动", 0.6842D, 0.5D, 0.125D, 8, 4)
        ));

        IssueListResponse response = insightQueryService.listIssues("demo-earphone");

        assertEquals("success", response.state());
        assertEquals(null, response.notice());
        assertEquals(1, response.items().size());
        assertEquals("iss-battery-7", response.items().getFirst().issueId());
        assertEquals("battery", response.items().getFirst().aspect());
        assertEquals(0.6842D, response.items().getFirst().priorityScore());
        assertTrue(response.items().getFirst().evidenceSummary().contains("相关评论共 8 条"));
        assertTrue(response.items().getFirst().evidenceSummary().contains("负向反馈 4 条"));
        verify(demoReviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void listIssuesShouldReturnExplicitEmptyStateWhenMaterializedIssuesAreEmpty() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(analysisMaterializationRepository.findIssues("demo-earphone")).thenReturn(List.of());
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenReturn(true);

        IssueListResponse response = insightQueryService.listIssues("demo-earphone");

        assertEquals("empty", response.state());
        assertEquals("评论情绪整体平稳，当前未识别到高优先级问题。", response.notice());
        assertTrue(response.items().isEmpty());
        verify(demoReviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void listIssuesShouldReturnExplicitEmptyStateWhenMaterializedOutputsAreMissing() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(analysisMaterializationRepository.findIssues("demo-earphone")).thenReturn(List.of());
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenReturn(false);

        IssueListResponse response = insightQueryService.listIssues("demo-earphone");

        assertEquals("empty", response.state());
        assertEquals("当前暂无可分析的演示评论，请先初始化演示数据。", response.notice());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void compareShouldBuildCanonicalAspectRowsFromMaterializedOutputs() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.allAspectCodes()).thenReturn(List.of(
                "battery",
                "bluetooth",
                "noise-canceling",
                "comfort",
                "microphone"
        ));
        when(demoReviewAggregationService.normalizeAspect("battery")).thenReturn("battery");
        when(demoReviewAggregationService.normalizeAspect("bluetooth")).thenReturn("bluetooth");
        when(demoReviewAggregationService.normalizeAspect("noise-canceling")).thenReturn("noise-canceling");
        when(demoReviewAggregationService.normalizeAspect("comfort")).thenReturn("comfort");
        when(demoReviewAggregationService.normalizeAspect("microphone")).thenReturn("microphone");
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenReturn(true);
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone-competitor")).thenReturn(true);
        when(analysisMaterializationRepository.findCompareAspectScores("demo-earphone")).thenReturn(List.of(
                new MaterializedCompareAspectRecord("battery", 20, 0.22D),
                new MaterializedCompareAspectRecord("bluetooth", 20, 0.78D),
                new MaterializedCompareAspectRecord("noise-canceling", 20, 0.5D),
                new MaterializedCompareAspectRecord("comfort", 20, 0.78D),
                new MaterializedCompareAspectRecord("microphone", 20, 0.5D)
        ));
        when(analysisMaterializationRepository.findCompareAspectScores("demo-earphone-competitor")).thenReturn(List.of(
                new MaterializedCompareAspectRecord("battery", 20, 0.78D),
                new MaterializedCompareAspectRecord("bluetooth", 20, 0.5D),
                new MaterializedCompareAspectRecord("noise-canceling", 20, 0.78D),
                new MaterializedCompareAspectRecord("comfort", 20, 0.5D),
                new MaterializedCompareAspectRecord("microphone", 20, 0.22D)
        ));

        CompareResponse response = insightQueryService.compare("demo-earphone", "demo-earphone-competitor");

        assertEquals("demo-earphone", response.productCode());
        assertEquals("demo-earphone-competitor", response.comparisonProductCode());
        assertEquals("success", response.state());
        assertEquals(5, response.items().size());
        assertEquals("battery", response.items().get(0).aspect());
        assertEquals(0.22D, response.items().get(0).ourScore());
        assertEquals(0.78D, response.items().get(0).competitorScore());
        assertEquals(-0.56D, response.items().get(0).gap());
    }

    @Test
    void compareShouldReturnStructuredStateWhenComparisonTargetIsMissing() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");

        CompareResponse response = insightQueryService.compare("demo-earphone", null);

        assertEquals("demo-earphone", response.productCode());
        assertEquals(null, response.comparisonProductCode());
        assertEquals("missing-target", response.state());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("竞品"));
    }

    @Test
    void compareShouldReturnStructuredStateWhenComparisonDataIsUnavailable() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenReturn(true);
        when(analysisMaterializationRepository.hasMaterializedOutputs("missing-competitor")).thenReturn(false);

        CompareResponse response = insightQueryService.compare("demo-earphone", "missing-competitor");

        assertEquals("comparison-unavailable", response.state());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("竞品"));
    }

    @Test
    void compareShouldReturnStructuredStateWhenPrimaryDataIsUnavailable() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenReturn(false);

        CompareResponse response = insightQueryService.compare("demo-earphone", "demo-earphone-competitor");

        assertEquals("primary-unavailable", response.state());
        assertEquals("demo-earphone-competitor", response.comparisonProductCode());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("主产品"));
    }

    @Test
    void compareShouldReturnStructuredErrorStateWhenMaterializedLookupFails() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone")).thenThrow(new IllegalStateException("db down"));

        CompareResponse response = insightQueryService.compare("demo-earphone", "demo-earphone-competitor");

        assertEquals("error", response.state());
        assertEquals("demo-earphone-competitor", response.comparisonProductCode());
        assertTrue(response.items().isEmpty());
        assertEquals("评论洞察正在更新，请稍后刷新重试。", response.notice());
    }

    @Test
    void trendsShouldReturnNoDataNoticeWhenMaterializedPointsAreMissing() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.normalizeTrendAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findTrendReviews("demo-earphone", "battery")).thenReturn(List.of());

        TrendResponse response = insightQueryService.trends("demo-earphone", "battery");

        assertEquals("demo-earphone", response.productCode());
        assertEquals("battery", response.aspect());
        assertEquals("empty", response.state());
        assertTrue(response.points().isEmpty());
        assertEquals("当前暂无可分析的演示评论，请先初始化演示数据。", response.notice());
        verify(demoReviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void wordCloudShouldUseMaterializedAspectAndSentimentRows() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.normalizeWordCloudAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findWordCloudReviews("demo-earphone", "battery")).thenReturn(List.of(
                new MaterializedWordCloudReviewRecord("battery battery 续航稳定", "NEGATIVE"),
                new MaterializedWordCloudReviewRecord("battery 表现稳定", "POSITIVE")
        ));

        WordCloudResponse response = insightQueryService.wordCloud("demo-earphone", "battery");

        assertEquals("demo-earphone", response.productCode());
        assertEquals("battery", response.aspect());
        assertEquals("success", response.state());
        assertTrue(response.notice() == null);
        assertEquals("续航", response.items().getFirst().keyword());
        assertEquals(3, response.items().getFirst().frequency());
        assertEquals("负向", response.items().getFirst().sentimentTag());
        verify(demoReviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void trendsShouldBuildPointsFromMaterializedWeeklyBuckets() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.normalizeTrendAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findTrendReviews("demo-earphone", "battery")).thenReturn(List.of(
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-01T00:00:00Z"), "NEGATIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-02T00:00:00Z"), "NEGATIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-03T00:00:00Z"), "POSITIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-04T00:00:00Z"), "POSITIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-05T00:00:00Z"), "POSITIVE")
        ));

        TrendResponse response = insightQueryService.trends("demo-earphone", "battery");

        assertEquals(1, response.points().size());
        assertEquals("2026-W14", response.points().getFirst().period());
        assertEquals(0.4D, response.points().getFirst().negativeRate());
        assertEquals(5, response.points().getFirst().mentionVolume());
        assertEquals("success", response.state());
        assertEquals(null, response.notice());
    }

    @Test
    void wordCloudShouldReturnExplicitDegradedStateWhenKeywordsCannotBeExtracted() {
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.normalizeWordCloudAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findWordCloudReviews("demo-earphone", "battery")).thenReturn(List.of(
                new MaterializedWordCloudReviewRecord("a", "NEGATIVE"),
                new MaterializedWordCloudReviewRecord("b", "POSITIVE")
        ));

        WordCloudResponse response = insightQueryService.wordCloud("demo-earphone", "battery");

        assertEquals("degraded", response.state());
        assertTrue(response.items().isEmpty());
        assertEquals("当前评论文本暂未提取到可展示关键词，请稍后重试。", response.notice());
    }

    @Test
    void validationShouldReusePersistedSnapshotWhenAvailable() {
        ActionResponse action = new ActionResponse(
                "11",
                "demo-earphone",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-12T00:00:00Z")
        );
        when(actionService.findValidationContextById("11")).thenReturn(Optional.of(
                new ActionValidationContext(action, 7L, Instant.parse("2026-03-12T00:00:00Z"), "battery")
        ));
        when(validationMetricsRepository.findLatestByActionId(11L)).thenReturn(Optional.of(
                new ValidationSnapshot(
                        11L,
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-03-20T00:00:00Z"),
                        new MetricsPayload(10, 4, 0.4D, "battery", "2026-03-12T00:00:00Z"),
                        new MetricsPayload(8, 2, 0.25D, "battery", "2026-03-12T00:00:00Z"),
                        "persisted summary",
                        Instant.parse("2026-03-21T00:00:00Z")
                )
        ));

        ValidationResponse response = insightQueryService.validation("11");

        assertEquals(1, response.items().size());
        assertEquals("success", response.state());
        assertEquals("11", response.items().getFirst().actionId());
        assertEquals(0.4D, response.items().getFirst().beforeNegativeRate());
        assertEquals(0.25D, response.items().getFirst().afterNegativeRate());
        assertEquals("persisted summary", response.items().getFirst().summary());
        verify(demoReviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void validationShouldPersistActionLinkedSnapshotWhenMissing() {
        ActionResponse action = new ActionResponse(
                "12",
                "demo-earphone",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(actionService.findValidationContextById("12")).thenReturn(Optional.of(
                new ActionValidationContext(action, 7L, null, "battery")
        ));
        when(validationMetricsRepository.findLatestByActionId(12L)).thenReturn(Optional.empty());
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone")).thenReturn("demo-earphone");
        when(demoReviewAggregationService.normalizeAspect("battery")).thenReturn("battery");
        when(demoReviewAggregationService.aspectDisplayName("battery")).thenReturn("续航");
        when(demoReviewAggregationService.filterByAspect(any(), anyString())).thenReturn(List.of(
                new DemoReviewAggregationService.AggregatedReview(1L, "demo-earphone", "battery", "a", Instant.parse("2026-03-01T00:00:00Z"), DemoReviewAggregationService.Sentiment.NEGATIVE),
                new DemoReviewAggregationService.AggregatedReview(2L, "demo-earphone", "battery", "b", Instant.parse("2026-03-10T00:00:00Z"), DemoReviewAggregationService.Sentiment.NEGATIVE),
                new DemoReviewAggregationService.AggregatedReview(3L, "demo-earphone", "battery", "c", Instant.parse("2026-03-20T00:00:00Z"), DemoReviewAggregationService.Sentiment.POSITIVE),
                new DemoReviewAggregationService.AggregatedReview(4L, "demo-earphone", "battery", "d", Instant.parse("2026-03-28T00:00:00Z"), DemoReviewAggregationService.Sentiment.POSITIVE)
        ));
        when(demoReviewAggregationService.loadReviews("demo-earphone")).thenReturn(List.of(
                new DemoReviewAggregationService.AggregatedReview(1L, "demo-earphone", "battery", "a", Instant.parse("2026-03-01T00:00:00Z"), DemoReviewAggregationService.Sentiment.NEGATIVE),
                new DemoReviewAggregationService.AggregatedReview(2L, "demo-earphone", "battery", "b", Instant.parse("2026-03-10T00:00:00Z"), DemoReviewAggregationService.Sentiment.NEGATIVE),
                new DemoReviewAggregationService.AggregatedReview(3L, "demo-earphone", "battery", "c", Instant.parse("2026-03-20T00:00:00Z"), DemoReviewAggregationService.Sentiment.POSITIVE),
                new DemoReviewAggregationService.AggregatedReview(4L, "demo-earphone", "battery", "d", Instant.parse("2026-03-28T00:00:00Z"), DemoReviewAggregationService.Sentiment.POSITIVE)
        ));

        ValidationResponse response = insightQueryService.validation("12");

        assertEquals(1, response.items().size());
        assertEquals("success", response.state());
        assertEquals(1D, response.items().getFirst().beforeNegativeRate());
        assertEquals(0D, response.items().getFirst().afterNegativeRate());
        assertTrue(response.items().getFirst().summary().contains("动作关联评论窗口"));
        verify(validationMetricsRepository).save(any(ValidationSnapshot.class));
        verify(actionService).updateLaunchContext(anyString(), any(Instant.class));
    }

    @Test
    void validationShouldPreserveMissingAndNoActionNotices() {
        when(actionService.findValidationContextById("404")).thenReturn(Optional.empty());
        when(actionService.listValidationContexts()).thenReturn(List.of());

        ValidationResponse missing = insightQueryService.validation("404");
        ValidationResponse empty = insightQueryService.validation(null);

        assertEquals("empty", missing.state());
        assertEquals("未找到对应改进动作，请确认动作编号。", missing.notice());
        assertTrue(missing.items().isEmpty());
        assertEquals("empty", empty.state());
        assertEquals("当前暂无改进动作，请先创建动作后查看验证结果。", empty.notice());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void validationListShouldReturnDegradedStateWhenActionLinkedMetricsFallback() {
        ActionResponse action = new ActionResponse(
                "13",
                "demo-earphone",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(actionService.listValidationContexts()).thenReturn(List.of(
                new ActionValidationContext(action, 7L, null, "battery")
        ));
        when(validationMetricsRepository.findLatestByActionId(13L)).thenReturn(Optional.empty());
        when(demoReviewAggregationService.normalizeProductCode("demo-earphone"))
                .thenThrow(new IllegalStateException("query path unavailable"));

        ValidationResponse response = insightQueryService.validation(null);

        assertEquals("degraded", response.state());
        assertEquals("评论洞察正在更新，请稍后刷新重试。", response.notice());
        assertEquals(1, response.items().size());
        assertEquals("13", response.items().getFirst().actionId());
        assertEquals(0D, response.items().getFirst().beforeNegativeRate());
        assertEquals(0D, response.items().getFirst().afterNegativeRate());
        assertEquals("评论洞察正在更新，请稍后刷新重试。", response.items().getFirst().summary());
    }
}
