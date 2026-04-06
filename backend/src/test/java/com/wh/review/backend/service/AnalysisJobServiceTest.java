package com.wh.review.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wh.review.backend.dto.AnalysisJobResponse;
import com.wh.review.backend.persistence.AnalysisJobRepository;
import com.wh.review.backend.persistence.AnalysisMaterializationRepository;
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

    private AnalysisJobService analysisJobService;

    @BeforeEach
    void setUp() {
        analysisJobService = new AnalysisJobService(
                analysisJobRepository,
                demoReviewAggregationService,
                analysisMaterializationRepository
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
                        review(1L, "demo-earphone", "battery", DemoReviewAggregationService.Sentiment.NEGATIVE),
                        review(2L, "demo-earphone", "battery", DemoReviewAggregationService.Sentiment.POSITIVE),
                        review(3L, "demo-earphone", "bluetooth", DemoReviewAggregationService.Sentiment.NEGATIVE)
                ));
        when(analysisJobRepository.markSucceeded(eq("11"), any(Instant.class)))
                .thenReturn(succeeded);

        AnalysisJobResponse response = analysisJobService.createJob("demo-earphone");

        assertEquals("SUCCEEDED", response.status());
        assertNotNull(response.finishedAt());
        InOrder inOrder = inOrder(analysisJobRepository, analysisMaterializationRepository, demoReviewAggregationService);
        inOrder.verify(analysisJobRepository).findLatestSucceededForProduct("demo-earphone");
        inOrder.verify(analysisMaterializationRepository).findLatestSourceUpdateTime("demo-earphone");
        inOrder.verify(analysisJobRepository).create(eq("demo-earphone"), eq("QUEUED"), any(Instant.class));
        inOrder.verify(analysisJobRepository).markRunning("11");
        inOrder.verify(demoReviewAggregationService).loadReviews("demo-earphone");
        inOrder.verify(analysisMaterializationRepository).replaceOutputs(eq("demo-earphone"), any());
        inOrder.verify(analysisJobRepository).markSucceeded(eq("11"), any(Instant.class));
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
            DemoReviewAggregationService.Sentiment sentiment
    ) {
        return new DemoReviewAggregationService.AggregatedReview(
                reviewId,
                productCode,
                aspect,
                "review-" + reviewId + "-" + aspect,
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(reviewId * 3600),
                sentiment
        );
    }
}
