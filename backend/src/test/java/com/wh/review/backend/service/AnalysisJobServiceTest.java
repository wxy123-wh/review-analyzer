package com.wh.review.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.Materialization;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository.ReviewAspectRecord;
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
    private DemoReviewAggregationService demoReviewAggregationService;

    @Mock
    private AnalysisMaterializationRepository analysisMaterializationRepository;

    @Mock
    private NlpReviewAnalysisClient nlpReviewAnalysisClient;

    private AnalysisJobService analysisJobService;

    @BeforeEach
    void setUp() {
        analysisJobService = new AnalysisJobService(
                analysisJobRepository,
                demoReviewAggregationService,
                analysisMaterializationRepository,
                nlpReviewAnalysisClient
        );
    }

    @Test
    void shouldRunAnalysisLifecycleAndPersistOutputs() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("11", "demo-earphone", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("11", "demo-earphone", "RUNNING", startedAt, null, null);
        AnalysisJobResponse succeeded = response("11", "demo-earphone", "SUCCEEDED", startedAt, finishedAt, null);

        when(analysisJobRepository.findLatestSucceededForProduct("demo-earphone"))
                .thenReturn(Optional.empty());
        when(analysisMaterializationRepository.findLatestSourceUpdateTime("demo-earphone"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("11"))
                .thenReturn(running);
        when(demoReviewAggregationService.loadReviews("demo-earphone"))
                .thenReturn(List.of(
                        review(1L, "demo-earphone", "battery", "续航很好", DemoReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "demo-earphone", "battery", "蓝牙偶尔断开", DemoReviewAggregationService.Sentiment.POSITIVE),
                        review(3L, "demo-earphone", "bluetooth", "通话收音发闷", DemoReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("11"),
                eq("demo-earphone"),
                eq(List.of("续航很好", "蓝牙偶尔断开", "通话收音发闷"))
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

        AnalysisJobResponse response = analysisJobService.createJob("demo-earphone");

        assertEquals("SUCCEEDED", response.status());
        assertNotNull(response.finishedAt());
        InOrder inOrder = inOrder(
                analysisJobRepository,
                analysisMaterializationRepository,
                demoReviewAggregationService,
                nlpReviewAnalysisClient
        );
        inOrder.verify(analysisJobRepository).findLatestSucceededForProduct("demo-earphone");
        inOrder.verify(analysisMaterializationRepository).findLatestSourceUpdateTime("demo-earphone");
        inOrder.verify(analysisJobRepository).create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class));
        inOrder.verify(analysisJobRepository).markRunning("11");
        inOrder.verify(demoReviewAggregationService).loadReviews("demo-earphone");
        inOrder.verify(nlpReviewAnalysisClient).analyze(
                eq("11"),
                eq("demo-earphone"),
                eq(List.of("续航很好", "蓝牙偶尔断开", "通话收音发闷"))
        );
        inOrder.verify(analysisMaterializationRepository).replaceOutputs(eq("demo-earphone"), argThat(this::usesNlpAspectOutputs));
        inOrder.verify(analysisJobRepository).markSucceeded(eq("11"), any(Instant.class), isNull());
    }

    @Test
    void shouldDegradeToControlledAnalysisWhenNlpIsUnavailable() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("14", "demo-earphone", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("14", "demo-earphone", "RUNNING", startedAt, null, null);
        AnalysisJobResponse degraded = response(
                "14",
                "demo-earphone",
                "SUCCEEDED",
                startedAt,
                finishedAt,
                "degraded:nlp_unavailable:http-503"
        );

        when(analysisJobRepository.findLatestSucceededForProduct("demo-earphone"))
                .thenReturn(Optional.empty());
        when(analysisMaterializationRepository.findLatestSourceUpdateTime("demo-earphone"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("14"))
                .thenReturn(running);
        when(demoReviewAggregationService.loadReviews("demo-earphone"))
                .thenReturn(List.of(
                        review(1L, "demo-earphone", "battery", "续航衰减明显", DemoReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "demo-earphone", "bluetooth", "蓝牙断连", DemoReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("14"),
                eq("demo-earphone"),
                eq(List.of("续航衰减明显", "蓝牙断连"))
        )).thenReturn(NlpReviewAnalysisClient.AnalyzeResult.degraded("degraded:nlp_unavailable:http-503"));
        when(analysisJobRepository.markSucceeded(eq("14"), any(Instant.class), eq("degraded:nlp_unavailable:http-503")))
                .thenReturn(degraded);

        AnalysisJobResponse response = analysisJobService.createJob("demo-earphone");

        assertEquals("SUCCEEDED", response.status());
        assertEquals("degraded:nlp_unavailable:http-503", response.errorMessage());
        verify(analysisMaterializationRepository).replaceOutputs(eq("demo-earphone"), argThat(materialization ->
                materialization.reviewAspects().stream().map(ReviewAspectRecord::aspect).toList().equals(List.of("battery", "bluetooth"))
        ));
        verify(analysisJobRepository).markSucceeded(eq("14"), any(Instant.class), eq("degraded:nlp_unavailable:http-503"));
    }

    @Test
    void shouldFallbackToControlledAnalysisWhenNlpResponseBreaksBackendAspectContract() {
        Instant startedAt = Instant.parse("2026-04-06T08:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-06T08:01:00Z");
        AnalysisJobResponse queued = response("15", "demo-earphone", "QUEUED", startedAt, null, null);
        AnalysisJobResponse running = response("15", "demo-earphone", "RUNNING", startedAt, null, null);
        AnalysisJobResponse degraded = response(
                "15",
                "demo-earphone",
                "SUCCEEDED",
                startedAt,
                finishedAt,
                "degraded:nlp_invalid_response:unsupported-nlp-aspect=mystery-aspect"
        );

        when(analysisJobRepository.findLatestSucceededForProduct("demo-earphone"))
                .thenReturn(Optional.empty());
        when(analysisMaterializationRepository.findLatestSourceUpdateTime("demo-earphone"))
                .thenReturn(Optional.of(startedAt.minusSeconds(10)));
        when(analysisJobRepository.create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("15"))
                .thenReturn(running);
        when(demoReviewAggregationService.loadReviews("demo-earphone"))
                .thenReturn(List.of(
                        review(1L, "demo-earphone", "battery", "续航衰减明显", DemoReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "demo-earphone", "bluetooth", "蓝牙断连", DemoReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(nlpReviewAnalysisClient.analyze(
                eq("15"),
                eq("demo-earphone"),
                eq(List.of("续航衰减明显", "蓝牙断连"))
        )).thenReturn(NlpReviewAnalysisClient.AnalyzeResult.success(new NlpReviewAnalysisClient.AnalyzeResponse(
                "15",
                List.of(
                        new NlpReviewAnalysisClient.AspectSentiment(0, "mystery-aspect", "POSITIVE", 0.82D, 0.91D),
                        new NlpReviewAnalysisClient.AspectSentiment(1, "bluetooth", "NEGATIVE", -0.78D, 0.88D)
                ),
                List.of(new NlpReviewAnalysisClient.IssueCluster("bluetooth", "蓝牙连接稳定性不足", 1))
        )));
        when(analysisJobRepository.markSucceeded(
                eq("15"),
                any(Instant.class),
                eq("degraded:nlp_invalid_response:unsupported-nlp-aspect=mystery-aspect")
        )).thenReturn(degraded);

        AnalysisJobResponse response = analysisJobService.createJob("demo-earphone");

        assertEquals("SUCCEEDED", response.status());
        assertEquals("degraded:nlp_invalid_response:unsupported-nlp-aspect=mystery-aspect", response.errorMessage());
        verify(analysisMaterializationRepository).replaceOutputs(eq("demo-earphone"), argThat(materialization ->
                materialization.reviewAspects().stream().map(ReviewAspectRecord::aspect).toList().equals(List.of("battery", "bluetooth"))
                        && materialization.reviewAspects().stream().map(ReviewAspectRecord::sentimentPolarity).toList()
                                .equals(List.of("NEGATIVE", "NEGATIVE"))
        ));
        verify(analysisJobRepository).markSucceeded(
                eq("15"),
                any(Instant.class),
                eq("degraded:nlp_invalid_response:unsupported-nlp-aspect=mystery-aspect")
        );
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

        when(analysisJobRepository.findLatestSucceededForProduct("missing-product"))
                .thenReturn(Optional.empty());
        when(analysisMaterializationRepository.findLatestSourceUpdateTime("missing-product"))
                .thenReturn(Optional.empty());
        when(analysisJobRepository.create(eq("missing-product"), eq("QUEUED"), any(Instant.class)))
                .thenReturn(queued);
        when(analysisJobRepository.markRunning("12"))
                .thenReturn(running);
        when(demoReviewAggregationService.loadReviews("missing-product"))
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
        AnalysisJobResponse existing = response("13", "demo-earphone", "SUCCEEDED", finishedAt.minusSeconds(30), finishedAt, null);

        when(analysisJobRepository.findLatestSucceededForProduct("demo-earphone"))
                .thenReturn(Optional.of(existing));
        when(analysisMaterializationRepository.findLatestSourceUpdateTime("demo-earphone"))
                .thenReturn(Optional.of(finishedAt.minusSeconds(1)));
        when(analysisMaterializationRepository.hasMaterializedOutputs("demo-earphone"))
                .thenReturn(true);

        AnalysisJobResponse response = analysisJobService.createJob("demo-earphone");

        assertEquals("13", response.jobId());
        assertEquals("SUCCEEDED", response.status());
        verify(analysisJobRepository, never()).create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class));
        verifyNoInteractions(demoReviewAggregationService);
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

    private DemoReviewAggregationService.AggregatedReview review(
            long reviewId,
            String productCode,
            String aspect,
            String content,
            DemoReviewAggregationService.Sentiment sentiment
    ) {
        return new DemoReviewAggregationService.AggregatedReview(
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
}
