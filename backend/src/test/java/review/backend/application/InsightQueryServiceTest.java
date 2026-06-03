package review.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import review.backend.api.dto.ActionResponse;
import review.backend.api.dto.CompareResponse;
import review.backend.api.dto.DataQualityResponse;
import review.backend.api.dto.PositiveInsightResponse;
import review.backend.api.dto.ValidationResponse;
import review.backend.api.dto.IssueListResponse;
import review.backend.api.dto.TrendResponse;
import review.backend.api.dto.WordCloudResponse;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.MaterializedCompareAspectRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedIssueRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedTrendReviewRecord;
import review.backend.data.AnalysisMaterializationRepository.MaterializedWordCloudReviewRecord;
import review.backend.data.ActionRepository.ActionValidationContext;
import review.backend.data.DataQualityRepository;
import review.backend.data.DataQualityRepository.DataQualityRun;
import review.backend.data.ReviewSemanticLabelRepository;
import review.backend.data.ReviewSemanticLabelRepository.PositiveInsightAggregate;
import review.backend.data.ValidationMetricsRepository;
import review.backend.data.ValidationMetricsRepository.MetricsPayload;
import review.backend.data.ValidationMetricsRepository.ValidationSnapshot;
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
    private DataQualityRepository dataQualityRepository;

    @Mock
    private ReviewSemanticLabelRepository reviewSemanticLabelRepository;

    @Mock
    private ReviewAggregationService reviewAggregationService;

    @Mock
    private ValidationMetricsRepository validationMetricsRepository;

    private InsightQueryService insightQueryService;

    @BeforeEach
    void setUp() {
        insightQueryService = new InsightQueryService(
                actionService,
                analysisMaterializationRepository,
                dataQualityRepository,
                reviewSemanticLabelRepository,
                reviewAggregationService,
                validationMetricsRepository
        );
    }

    @Test
    void listIssuesShouldReadMaterializedIssueRows() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.aspectDisplayName("battery")).thenReturn("续航");
        when(analysisMaterializationRepository.findIssues("jd-100127936932")).thenReturn(List.of(
                new MaterializedIssueRecord(7L, "battery", "产品硬件", "电池与续航", "电池与续航反馈待优化", 0.6842D, 0.5D, 0.125D, 8, 4)
        ));

        IssueListResponse response = insightQueryService.listIssues("jd-100127936932");

        assertEquals("success", response.state());
        assertEquals(null, response.notice());
        assertEquals(1, response.items().size());
        assertEquals("iss-电池与续航-7", response.items().getFirst().issueId());
        assertEquals("battery", response.items().getFirst().aspect());
        assertEquals("电池与续航", response.items().getFirst().uxSecondaryLabel());
        assertEquals(0.6842D, response.items().getFirst().priorityScore());
        assertTrue(response.items().getFirst().evidenceSummary().contains("相关评论共 8 条"));
        assertTrue(response.items().getFirst().evidenceSummary().contains("负向反馈 4 条"));
        verify(reviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void listIssuesShouldReturnExplicitEmptyStateWhenMaterializedIssuesAreEmpty() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.findIssues("jd-100127936932")).thenReturn(List.of());
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(true);

        IssueListResponse response = insightQueryService.listIssues("jd-100127936932");

        assertEquals("empty", response.state());
        assertEquals("评论情绪整体平稳，当前未识别到高优先级问题。", response.notice());
        assertTrue(response.items().isEmpty());
        verify(reviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void listIssuesShouldReturnExplicitEmptyStateWhenMaterializedOutputsAreMissing() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.findIssues("jd-100127936932")).thenReturn(List.of());
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(false);

        IssueListResponse response = insightQueryService.listIssues("jd-100127936932");

        assertEquals("empty", response.state());
        assertEquals("当前暂无可分析的真实评论，请先通过爬虫或 JSONL 导入评论，再启动分析。", response.notice());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void compareShouldBuildCanonicalAspectRowsFromMaterializedOutputs() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(true);
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932-competitor")).thenReturn(true);
        when(analysisMaterializationRepository.findCompareAspectScores("jd-100127936932")).thenReturn(List.of(
                new MaterializedCompareAspectRecord("battery", "产品硬件", "电池与续航", 20, 0.22D),
                new MaterializedCompareAspectRecord("bluetooth", "产品硬件", "连接与稳定性", 20, 0.78D),
                new MaterializedCompareAspectRecord("noise-canceling", "声音表现", "降噪与通透", 20, 0.5D),
                new MaterializedCompareAspectRecord("comfort", "产品体验", "佩戴与人体工学", 20, 0.78D),
                new MaterializedCompareAspectRecord("microphone", "声音表现", "麦克风与通话", 20, 0.5D)
        ));
        when(analysisMaterializationRepository.findCompareAspectScores("jd-100127936932-competitor")).thenReturn(List.of(
                new MaterializedCompareAspectRecord("battery", "产品硬件", "电池与续航", 20, 0.78D),
                new MaterializedCompareAspectRecord("bluetooth", "产品硬件", "连接与稳定性", 20, 0.5D),
                new MaterializedCompareAspectRecord("noise-canceling", "声音表现", "降噪与通透", 20, 0.78D),
                new MaterializedCompareAspectRecord("comfort", "产品体验", "佩戴与人体工学", 20, 0.5D),
                new MaterializedCompareAspectRecord("microphone", "声音表现", "麦克风与通话", 20, 0.22D)
        ));

        CompareResponse response = insightQueryService.compare("jd-100127936932", "jd-100127936932-competitor");

        assertEquals("jd-100127936932", response.productCode());
        assertEquals("jd-100127936932-competitor", response.comparisonProductCode());
        assertEquals("success", response.state());
        assertEquals(5, response.items().size());
        assertEquals("battery", response.items().get(0).aspect());
        assertEquals("电池与续航", response.items().get(0).uxSecondaryLabel());
        assertEquals(0.22D, response.items().get(0).ourScore());
        assertEquals(0.78D, response.items().get(0).competitorScore());
        assertEquals(-0.56D, response.items().get(0).gap());
    }

    @Test
    void compareShouldReturnStructuredStateWhenComparisonTargetIsMissing() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");

        CompareResponse response = insightQueryService.compare("jd-100127936932", null);

        assertEquals("jd-100127936932", response.productCode());
        assertEquals(null, response.comparisonProductCode());
        assertEquals("missing-target", response.state());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("竞品"));
    }

    @Test
    void compareShouldReturnStructuredStateWhenComparisonDataIsUnavailable() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(true);
        when(analysisMaterializationRepository.hasMaterializedOutputs("missing-competitor")).thenReturn(false);

        CompareResponse response = insightQueryService.compare("jd-100127936932", "missing-competitor");

        assertEquals("comparison-unavailable", response.state());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("竞品"));
    }

    @Test
    void compareShouldReturnStructuredStateWhenPrimaryDataIsUnavailable() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenReturn(false);

        CompareResponse response = insightQueryService.compare("jd-100127936932", "jd-100127936932-competitor");

        assertEquals("primary-unavailable", response.state());
        assertEquals("jd-100127936932-competitor", response.comparisonProductCode());
        assertTrue(response.items().isEmpty());
        assertTrue(response.notice().contains("主产品"));
    }

    @Test
    void compareShouldReturnStructuredErrorStateWhenMaterializedLookupFails() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(analysisMaterializationRepository.hasMaterializedOutputs("jd-100127936932")).thenThrow(new IllegalStateException("db down"));

        CompareResponse response = insightQueryService.compare("jd-100127936932", "jd-100127936932-competitor");

        assertEquals("error", response.state());
        assertEquals("jd-100127936932-competitor", response.comparisonProductCode());
        assertTrue(response.items().isEmpty());
        assertEquals("评论洞察正在更新，请稍后刷新重试。", response.notice());
    }

    @Test
    void trendsShouldReturnNoDataNoticeWhenMaterializedPointsAreMissing() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.normalizeTrendAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findTrendReviews("jd-100127936932", "电池与续航")).thenReturn(List.of());

        TrendResponse response = insightQueryService.trends("jd-100127936932", "battery");

        assertEquals("jd-100127936932", response.productCode());
        assertEquals("battery", response.aspect());
        assertEquals("empty", response.state());
        assertTrue(response.points().isEmpty());
        assertEquals("当前暂无可分析的真实评论，请先通过爬虫或 JSONL 导入评论，再启动分析。", response.notice());
        verify(reviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void wordCloudShouldUseMaterializedAspectAndSentimentRows() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.normalizeWordCloudAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findWordCloudReviews("jd-100127936932", "电池与续航")).thenReturn(List.of(
                new MaterializedWordCloudReviewRecord("battery battery 续航稳定", "NEGATIVE"),
                new MaterializedWordCloudReviewRecord("battery 表现稳定", "POSITIVE")
        ));

        WordCloudResponse response = insightQueryService.wordCloud("jd-100127936932", "battery");

        assertEquals("jd-100127936932", response.productCode());
        assertEquals("battery", response.aspect());
        assertEquals("success", response.state());
        assertTrue(response.notice() == null);
        assertEquals("续航", response.items().getFirst().keyword());
        assertEquals(3, response.items().getFirst().frequency());
        assertEquals("负向", response.items().getFirst().sentimentTag());
        verify(reviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void trendsShouldBuildPointsFromMaterializedWeeklyBuckets() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.normalizeTrendAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findTrendReviews("jd-100127936932", "电池与续航")).thenReturn(List.of(
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-01T00:00:00Z"), "NEGATIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-02T00:00:00Z"), "NEGATIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-03T00:00:00Z"), "POSITIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-04T00:00:00Z"), "POSITIVE"),
                new MaterializedTrendReviewRecord(Instant.parse("2026-04-05T00:00:00Z"), "POSITIVE")
        ));

        TrendResponse response = insightQueryService.trends("jd-100127936932", "battery");

        assertEquals(1, response.points().size());
        assertEquals("2026-W14", response.points().getFirst().period());
        assertEquals(0.4D, response.points().getFirst().negativeRate());
        assertEquals(5, response.points().getFirst().mentionVolume());
        assertEquals("success", response.state());
        assertEquals(null, response.notice());
    }

    @Test
    void wordCloudShouldReturnExplicitDegradedStateWhenKeywordsCannotBeExtracted() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.normalizeWordCloudAspect("battery")).thenReturn("battery");
        when(analysisMaterializationRepository.findWordCloudReviews("jd-100127936932", "电池与续航")).thenReturn(List.of(
                new MaterializedWordCloudReviewRecord("a", "NEGATIVE"),
                new MaterializedWordCloudReviewRecord("b", "POSITIVE")
        ));

        WordCloudResponse response = insightQueryService.wordCloud("jd-100127936932", "battery");

        assertEquals("degraded", response.state());
        assertTrue(response.items().isEmpty());
        assertEquals("当前评论文本暂未提取到可展示关键词，请稍后重试。", response.notice());
    }

    @Test
    void validationShouldReusePersistedSnapshotWhenAvailable() {
        ActionResponse action = new ActionResponse(
                "11",
                "jd-100127936932",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-12T00:00:00Z")
        );
        when(actionService.findValidationContextById("11")).thenReturn(Optional.of(
                new ActionValidationContext(action, 7L, Instant.parse("2026-03-12T00:00:00Z"), "battery", "电池与续航")
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
        verify(reviewAggregationService, never()).loadReviews(anyString());
    }

    @Test
    void validationShouldPersistActionLinkedSnapshotWhenMissing() {
        ActionResponse action = new ActionResponse(
                "12",
                "jd-100127936932",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(actionService.findValidationContextById("12")).thenReturn(Optional.of(
                new ActionValidationContext(action, 7L, null, "battery", "电池与续航")
        ));
        when(validationMetricsRepository.findLatestByActionId(12L)).thenReturn(Optional.empty());
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewAggregationService.normalizeAspect("battery")).thenReturn("battery");
        when(reviewAggregationService.filterByAspect(any(), anyString())).thenReturn(List.of(
                new ReviewAggregationService.AggregatedReview(1L, "jd-100127936932", "battery", "a", Instant.parse("2026-03-01T00:00:00Z"), ReviewAggregationService.Sentiment.NEGATIVE),
                new ReviewAggregationService.AggregatedReview(2L, "jd-100127936932", "battery", "b", Instant.parse("2026-03-10T00:00:00Z"), ReviewAggregationService.Sentiment.NEGATIVE),
                new ReviewAggregationService.AggregatedReview(3L, "jd-100127936932", "battery", "c", Instant.parse("2026-03-20T00:00:00Z"), ReviewAggregationService.Sentiment.POSITIVE),
                new ReviewAggregationService.AggregatedReview(4L, "jd-100127936932", "battery", "d", Instant.parse("2026-03-28T00:00:00Z"), ReviewAggregationService.Sentiment.POSITIVE)
        ));
        when(reviewAggregationService.loadReviews("jd-100127936932")).thenReturn(List.of(
                new ReviewAggregationService.AggregatedReview(1L, "jd-100127936932", "battery", "a", Instant.parse("2026-03-01T00:00:00Z"), ReviewAggregationService.Sentiment.NEGATIVE),
                new ReviewAggregationService.AggregatedReview(2L, "jd-100127936932", "battery", "b", Instant.parse("2026-03-10T00:00:00Z"), ReviewAggregationService.Sentiment.NEGATIVE),
                new ReviewAggregationService.AggregatedReview(3L, "jd-100127936932", "battery", "c", Instant.parse("2026-03-20T00:00:00Z"), ReviewAggregationService.Sentiment.POSITIVE),
                new ReviewAggregationService.AggregatedReview(4L, "jd-100127936932", "battery", "d", Instant.parse("2026-03-28T00:00:00Z"), ReviewAggregationService.Sentiment.POSITIVE)
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
    void dataQualityShouldReturnLatestCleaningSummary() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(dataQualityRepository.findLatest("jd-100127936932")).thenReturn(Optional.of(new DataQualityRun(
                "jd-100127936932",
                100,
                96,
                4,
                8,
                2,
                1,
                1,
                Instant.parse("2026-06-03T10:00:00Z")
        )));

        DataQualityResponse response = insightQueryService.dataQuality("jd-100127936932");

        assertEquals("success", response.state());
        assertEquals(100, response.rawCount());
        assertEquals(96, response.cleanedCount());
        assertEquals(4, response.removedCount());
        assertEquals(8, response.htmlCleanedCount());
    }

    @Test
    void positiveInsightsShouldAggregatePositiveUxLabels() {
        when(reviewAggregationService.normalizeProductCode("jd-100127936932")).thenReturn("jd-100127936932");
        when(reviewSemanticLabelRepository.findPositiveInsightAggregates("jd-100127936932")).thenReturn(List.of(
                new PositiveInsightAggregate("comfort", "产品体验", "佩戴与人体工学", "佩戴舒适", 3, 3, 0.9D),
                new PositiveInsightAggregate("battery", "产品硬件", "电池与续航", "续航持久", 1, 1, 0.8D)
        ));
        when(reviewSemanticLabelRepository.countPositiveLabels("jd-100127936932")).thenReturn(4);
        when(reviewSemanticLabelRepository.findEvidence("jd-100127936932", "佩戴与人体工学", 3))
                .thenReturn(List.of("戴了几个小时耳朵也不疼", "跑步不容易掉"));
        when(reviewSemanticLabelRepository.findEvidence("jd-100127936932", "电池与续航", 3))
                .thenReturn(List.of("续航很好"));

        PositiveInsightResponse response = insightQueryService.positiveInsights("jd-100127936932", 5);

        assertEquals("success", response.state());
        assertEquals(2, response.items().size());
        assertEquals("comfort", response.items().getFirst().aspect());
        assertEquals("佩戴舒适", response.items().getFirst().sellingPoint());
        assertEquals("佩戴与人体工学", response.items().getFirst().uxSecondaryLabel());
        assertEquals(3, response.items().getFirst().mentionCount());
        assertEquals(1.0D, response.items().getFirst().positiveRate());
        assertEquals(2, response.items().getFirst().evidence().size());
    }

    @Test
    void validationListShouldReturnDegradedStateWhenActionLinkedMetricsFallback() {
        ActionResponse action = new ActionResponse(
                "13",
                "jd-100127936932",
                "iss-battery-7",
                "处理：续航体验波动",
                "desc",
                "PLANNED",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(actionService.listValidationContexts()).thenReturn(List.of(
                new ActionValidationContext(action, 7L, null, "battery", "电池与续航")
        ));
        when(validationMetricsRepository.findLatestByActionId(13L)).thenReturn(Optional.empty());
        when(reviewAggregationService.normalizeProductCode("jd-100127936932"))
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
