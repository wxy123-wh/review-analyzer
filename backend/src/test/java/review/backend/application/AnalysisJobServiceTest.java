package review.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import review.backend.api.dto.AnalysisJobResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.api.dto.UxPrimaryLabelResponse;
import review.backend.api.dto.UxSecondaryLabelResponse;
import review.backend.data.AnalysisJobRepository;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.Materialization;
import review.backend.data.AnalysisMaterializationRepository.ReviewAspectRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisJobServiceTest {

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private ReviewAggregationService reviewAggregationService;

    @Mock
    private AnalysisMaterializationRepository analysisMaterializationRepository;

    @Mock
    private NlpReviewAnalysisClient nlpReviewAnalysisClient;

    @Mock
    private TaxonomyService taxonomyService;

    private AnalysisJobService analysisJobService;
    private TaxonomyResponse taxonomy;

    @BeforeEach
    void setUp() {
        taxonomy = taxonomy();
        lenient().when(taxonomyService.taxonomyForProduct(eq("jd-100127936932"), isNull())).thenReturn(taxonomy);
        lenient().when(taxonomyService.taxonomyForProduct(eq("missing-product"), isNull())).thenReturn(taxonomy);
        lenient().when(taxonomyService.toNlpTaxonomy(taxonomy)).thenReturn(nlpTaxonomy());
        lenient().when(taxonomyService.normalizeSecondaryLabel(eq(taxonomy), any())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(taxonomyService.primaryForSecondary(eq(taxonomy), any())).thenAnswer(invocation -> {
            String secondary = invocation.getArgument(1);
            if ("电池与续航".equals(secondary) || "连接与稳定性".equals(secondary)) {
                return "产品硬件";
            }
            if ("麦克风与通话".equals(secondary)) {
                return "声音表现";
            }
            return "无明显问题";
        });
        lenient().when(taxonomyService.legacyAspectFor(eq(taxonomy), any())).thenAnswer(invocation -> switch ((String) invocation.getArgument(1)) {
            case "电池与续航" -> "battery";
            case "连接与稳定性" -> "bluetooth";
            case "麦克风与通话" -> "microphone";
            default -> "unknown";
        });
        analysisJobService = new AnalysisJobService(
                analysisJobRepository,
                reviewAggregationService,
                analysisMaterializationRepository,
                nlpReviewAnalysisClient,
                taxonomyService
        );
    }

    @Test
    void shouldRunAnalysisLifecycleAndPersistOutputs() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("11", "jd-100127936932", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("11", "jd-100127936932", "RUNNING", startedAt, null, null);
        AnalysisJobResponse succeeded = response("11", "jd-100127936932", "SUCCEEDED", startedAt, finishedAt, null);

        lenient().when(analysisJobRepository.findLatestSucceededForProduct("jd-100127936932"))
                .thenReturn(Optional.empty());
        lenient().when(analysisMaterializationRepository.findLatestSourceUpdateTime("jd-100127936932"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("11"))
                .thenReturn(running);
        when(reviewAggregationService.loadReviews("jd-100127936932"))
                .thenReturn(List.of(
                        review(1L, "jd-100127936932", "battery", "续航很好", ReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "jd-100127936932", "battery", "蓝牙偶尔断开", ReviewAggregationService.Sentiment.POSITIVE),
                        review(3L, "jd-100127936932", "bluetooth", "通话收音发闷", ReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("11"),
                eq("jd-100127936932"),
                eq(List.of("续航很好", "蓝牙偶尔断开", "通话收音发闷")),
                eq(nlpTaxonomy())
        )).thenReturn(NlpReviewAnalysisClient.AnalyzeResult.success(new NlpReviewAnalysisClient.AnalyzeResponse(
                "11",
                List.of(
                        new NlpReviewAnalysisClient.AspectSentiment(0, "battery", "POSITIVE", 0.82D, 0.91D),
                        new NlpReviewAnalysisClient.AspectSentiment(1, "bluetooth", "NEGATIVE", -0.78D, 0.88D),
                        new NlpReviewAnalysisClient.AspectSentiment(2, "microphone", "NEGATIVE", -0.78D, 0.93D)
                ),
                List.of(
                        new NlpReviewAnalysisClient.IssueCluster("bluetooth", "蓝牙连接稳定性不足", 1),
                        new NlpReviewAnalysisClient.IssueCluster("microphone", "通话收音表现待优化", 1)
                )
        )));
        when(analysisJobRepository.markSucceeded(eq("11"), any(Instant.class), isNull()))
                .thenReturn(succeeded);

        AnalysisJobResponse response = analysisJobService.createJob("jd-100127936932");

        assertEquals("SUCCEEDED", response.status());
        assertNotNull(response.finishedAt());
        InOrder inOrder = inOrder(
                analysisJobRepository,
                analysisMaterializationRepository,
                reviewAggregationService,
                nlpReviewAnalysisClient
        );
        inOrder.verify(analysisJobRepository).create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1));
        inOrder.verify(analysisJobRepository).markRunning("11");
        inOrder.verify(reviewAggregationService).loadReviews("jd-100127936932");
        inOrder.verify(nlpReviewAnalysisClient).analyze(
                eq("11"),
                eq("jd-100127936932"),
                eq(List.of("续航很好", "蓝牙偶尔断开", "通话收音发闷")),
                eq(nlpTaxonomy())
        );
        inOrder.verify(analysisMaterializationRepository).replaceOutputs(eq("jd-100127936932"), argThat(this::usesNlpAspectOutputs));
        inOrder.verify(analysisJobRepository).markSucceeded(eq("11"), any(Instant.class), isNull());
    }

    @Test
    void shouldDegradeToLocalFallbackAnalysisWhenNlpIsUnavailable() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("14", "jd-100127936932", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("14", "jd-100127936932", "RUNNING", startedAt, null, null);
        AnalysisJobResponse degraded = response(
                "14",
                "jd-100127936932",
                "SUCCEEDED",
                startedAt,
                finishedAt,
                "degraded:nlp_unavailable:http-503"
        );

        lenient().when(analysisJobRepository.findLatestSucceededForProduct("jd-100127936932"))
                .thenReturn(Optional.empty());
        lenient().when(analysisMaterializationRepository.findLatestSourceUpdateTime("jd-100127936932"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("14"))
                .thenReturn(running);
        when(reviewAggregationService.loadReviews("jd-100127936932"))
                .thenReturn(List.of(
                        review(1L, "jd-100127936932", "battery", "续航衰减明显", ReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "jd-100127936932", "bluetooth", "蓝牙断连", ReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("14"),
                eq("jd-100127936932"),
                eq(List.of("续航衰减明显", "蓝牙断连")),
                eq(nlpTaxonomy())
        )).thenReturn(NlpReviewAnalysisClient.AnalyzeResult.degraded("degraded:nlp_unavailable:http-503"));
        when(analysisJobRepository.markSucceeded(eq("14"), any(Instant.class), eq("degraded:nlp_unavailable:http-503")))
                .thenReturn(degraded);

        AnalysisJobResponse response = analysisJobService.createJob("jd-100127936932");

        assertEquals("SUCCEEDED", response.status());
        assertEquals("degraded:nlp_unavailable:http-503", response.errorMessage());
        verify(analysisMaterializationRepository).replaceOutputs(eq("jd-100127936932"), argThat(materialization ->
                materialization.reviewAspects().stream().map(ReviewAspectRecord::aspect).toList().equals(List.of("battery", "bluetooth"))
        ));
        verify(analysisJobRepository).markSucceeded(eq("14"), any(Instant.class), eq("degraded:nlp_unavailable:http-503"));
    }

    @Test
    void shouldAcceptNlpResponseWhenLegacyAspectCannotBeMappedButUxLabelIsValid() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("15", "jd-100127936932", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("15", "jd-100127936932", "RUNNING", startedAt, null, null);
        AnalysisJobResponse succeeded = response(
                "15",
                "jd-100127936932",
                "SUCCEEDED",
                startedAt,
                finishedAt,
                null
        );

        lenient().when(analysisJobRepository.findLatestSucceededForProduct("jd-100127936932"))
                .thenReturn(Optional.empty());
        lenient().when(analysisMaterializationRepository.findLatestSourceUpdateTime("jd-100127936932"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("15"))
                .thenReturn(running);
        when(reviewAggregationService.loadReviews("jd-100127936932"))
                .thenReturn(List.of(
                        review(1L, "jd-100127936932", "battery", "续航衰减明显", ReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "jd-100127936932", "bluetooth", "蓝牙断连", ReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("15"),
                eq("jd-100127936932"),
                eq(List.of("续航衰减明显", "蓝牙断连")),
                eq(nlpTaxonomy())
        )).thenReturn(NlpReviewAnalysisClient.AnalyzeResult.success(new NlpReviewAnalysisClient.AnalyzeResponse(
                "15",
                List.of(
                        new NlpReviewAnalysisClient.AspectSentiment(0, "mystery-aspect", "POSITIVE", 0.82D, 0.91D),
                        new NlpReviewAnalysisClient.AspectSentiment(1, "bluetooth", "NEGATIVE", -0.78D, 0.88D)
                ),
                List.of(new NlpReviewAnalysisClient.IssueCluster("bluetooth", "蓝牙连接稳定性不足", 1))
        )));
        when(analysisJobRepository.markSucceeded(eq("15"), any(Instant.class), isNull()))
                .thenReturn(succeeded);

        AnalysisJobResponse response = analysisJobService.createJob("jd-100127936932");

        assertEquals("SUCCEEDED", response.status());
        assertEquals(null, response.errorMessage());
        verify(analysisMaterializationRepository).replaceOutputs(eq("jd-100127936932"), argThat(materialization ->
                materialization.reviewAspects().stream().map(ReviewAspectRecord::uxSecondaryLabel).toList()
                        .equals(List.of("无明显问题", "连接与稳定性"))
                        && materialization.reviewAspects().stream().map(ReviewAspectRecord::sentimentPolarity).toList()
                                .equals(List.of("POSITIVE", "NEGATIVE"))
        ));
        verify(analysisJobRepository).markSucceeded(eq("15"), any(Instant.class), isNull());
    }

    @Test
    void shouldMarkFailedWhenNoSourceReviewsExist() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("12", "missing-product", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("12", "missing-product", "RUNNING", startedAt, null, null);
        AnalysisJobResponse failed = response(
                "12",
                "missing-product",
                "FAILED",
                startedAt,
                finishedAt,
                "no reviews found for productCode=missing-product"
        );

        lenient().when(analysisJobRepository.findLatestSucceededForProduct("missing-product"))
                .thenReturn(Optional.empty());
        lenient().when(analysisMaterializationRepository.findLatestSourceUpdateTime("missing-product"))
                .thenReturn(Optional.empty());
        when(analysisJobRepository.create(eq("missing-product"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("12"))
                .thenReturn(running);
        when(reviewAggregationService.loadReviews("missing-product"))
                .thenReturn(List.of());
        when(analysisJobRepository.markFailed(eq("12"), any(Instant.class), eq("no reviews found for productCode=missing-product")))
                .thenReturn(failed);

        AnalysisJobResponse response = analysisJobService.createJob("missing-product");

        assertEquals("FAILED", response.status());
        assertTrue(response.errorMessage().contains("no reviews found"));
        verify(analysisMaterializationRepository, never()).replaceOutputs(eq("missing-product"), any());
        verifyNoInteractions(nlpReviewAnalysisClient);
    }

    @Test
    void shouldReuseSucceededJobWhenSourceDataHasNotChanged() {
        Instant finishedAt = Instant.parse("2026-04-06T08:05:00Z");
        AnalysisJobResponse existing = response("13", "jd-100127936932", "SUCCEEDED", finishedAt.minusSeconds(30), finishedAt, null);

        AnalysisJobResponse queued = response("16", "jd-100127936932", "QUEUED", finishedAt, null, null);
        AnalysisJobResponse running = response("16", "jd-100127936932", "RUNNING", finishedAt, null, null);
        when(analysisJobRepository.create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("16"))
                .thenReturn(running);
        when(reviewAggregationService.loadReviews("jd-100127936932"))
                .thenReturn(List.of());
        when(analysisJobRepository.markFailed(eq("16"), any(Instant.class), eq("no reviews found for productCode=jd-100127936932")))
                .thenReturn(response("16", "jd-100127936932", "FAILED", finishedAt, finishedAt, "no reviews found for productCode=jd-100127936932"));

        AnalysisJobResponse response = analysisJobService.createJob("jd-100127936932");

        assertEquals("16", response.jobId());
        assertEquals("FAILED", response.status());
        verify(analysisJobRepository).create(eq("jd-100127936932"), eq("QUEUED"), any(Instant.class), eq(1L), eq(1));
        verifyNoInteractions(nlpReviewAnalysisClient);
    }

    private AnalysisJobResponse response(
            String jobId,
            String productCode,
            String status,
            Instant startedAt,
            Instant finishedAt,
            String errorMessage
    ) {
        return new AnalysisJobResponse(jobId, productCode, status, startedAt, finishedAt, errorMessage);
    }

    private ReviewAggregationService.AggregatedReview review(
            long reviewId,
            String productCode,
            String aspect,
            String content,
            ReviewAggregationService.Sentiment sentiment
    ) {
        return new ReviewAggregationService.AggregatedReview(
                reviewId,
                productCode,
                aspect,
                content,
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(reviewId * 3600),
                sentiment
        );
    }

    private boolean usesNlpAspectOutputs(Materialization materialization) {
        return materialization.reviewAspects().stream()
                        .map(ReviewAspectRecord::aspect)
                        .toList()
                        .equals(List.of("battery", "bluetooth", "microphone"))
                && materialization.reviewAspects().stream()
                        .map(ReviewAspectRecord::sentimentPolarity)
                        .toList()
                        .equals(List.of("POSITIVE", "NEGATIVE", "NEGATIVE"));
    }

    private TaxonomyResponse taxonomy() {
        return new TaxonomyResponse(
                1L,
                "默认通用 UX 标签",
                "general",
                1,
                true,
                List.of(
                        new UxPrimaryLabelResponse(10L, "产品硬件", 0, List.of(
                                new UxSecondaryLabelResponse(11L, "电池与续航", List.of("续航", "电池"), "", 0, true),
                                new UxSecondaryLabelResponse(12L, "连接与稳定性", List.of("连接", "蓝牙", "断连"), "", 1, true)
                        )),
                        new UxPrimaryLabelResponse(20L, "声音表现", 1, List.of(
                                new UxSecondaryLabelResponse(21L, "麦克风与通话", List.of("通话", "收音"), "", 0, true)
                        )),
                        new UxPrimaryLabelResponse(30L, "无明显问题", 2, List.of(
                                new UxSecondaryLabelResponse(31L, "无明显问题", List.of("其他"), "", 0, true)
                        ))
                )
        );
    }

    private TaxonomyService.NlpTaxonomy nlpTaxonomy() {
        return new TaxonomyService.NlpTaxonomy(
                1L,
                "默认通用 UX 标签",
                "general",
                1,
                List.of(
                        new TaxonomyService.NlpPrimaryLabel("产品硬件", List.of(
                                new TaxonomyService.NlpSecondaryLabel("电池与续航", List.of("续航", "电池"), ""),
                                new TaxonomyService.NlpSecondaryLabel("连接与稳定性", List.of("连接", "蓝牙", "断连"), "")
                        )),
                        new TaxonomyService.NlpPrimaryLabel("声音表现", List.of(
                                new TaxonomyService.NlpSecondaryLabel("麦克风与通话", List.of("通话", "收音"), "")
                        )),
                        new TaxonomyService.NlpPrimaryLabel("无明显问题", List.of(
                                new TaxonomyService.NlpSecondaryLabel("无明显问题", List.of("其他"), "")
                        ))
                )
        );
    }
}
