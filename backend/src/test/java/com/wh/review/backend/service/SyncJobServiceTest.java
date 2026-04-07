package com.wh.review.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wh.review.backend.dto.SyncJobResponse;
import com.wh.review.backend.persistence.ExternalReviewRawRepository;
import com.wh.review.backend.persistence.ExternalReviewRawRepository.ExternalRawReview;
import com.wh.review.backend.persistence.ExternalReviewRawRepository.ExternalReviewPersistenceResult;
import com.wh.review.backend.persistence.SyncJobRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncJobServiceTest {

    @Mock
    private OneBoundReviewClient oneBoundReviewClient;

    @Mock
    private OneBoundProperties oneBoundProperties;

    @Mock
    private ExternalReviewRawRepository externalReviewRawRepository;

    @Mock
    private SyncJobRepository syncJobRepository;

    private SyncJobService syncJobService;

    @BeforeEach
    void setUp() {
        syncJobService = new SyncJobService(oneBoundReviewClient, oneBoundProperties, externalReviewRawRepository, syncJobRepository);
        when(oneBoundProperties.getDefaultPlatform()).thenReturn("taobao");
        when(syncJobRepository.create(any(SyncJobResponse.class), nullable(Instant.class)))
                .thenAnswer(invocation -> {
                    SyncJobResponse draft = invocation.getArgument(0);
                    return new SyncJobResponse(
                            "1",
                            draft.provider(),
                            draft.platform(),
                            draft.targetProductCode(),
                            draft.status(),
                            draft.startedAt(),
                            draft.fetchedCount(),
                            draft.errorMessage(),
                            draft.analysisHandoffStatus(),
                            draft.analysisHandoffNote()
                    );
                });
        when(syncJobRepository.updateOutcome(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any(Instant.class), nullable(String.class), anyString(), anyString()))
                .thenAnswer(invocation -> new SyncJobResponse(
                        invocation.getArgument(0),
                        "onebound",
                        "taobao",
                        "600530677643",
                        invocation.getArgument(1),
                        Instant.now(),
                        invocation.getArgument(2),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6)
                ));
    }

    @Test
    void shouldKeepQueuedWhenProviderIsNotOneBound() {
        SyncJobResponse response = syncJobService.createJob("aggregator-demo", null, "demo-earphone");

        assertEquals("QUEUED", response.status());
        assertEquals(0, response.fetchedCount());
        assertEquals("CONTROLLED_DATA_PATH", response.analysisHandoffStatus());
        verifyNoInteractions(oneBoundReviewClient);
    }

    @Test
    void shouldMarkUnsupportedWhenProviderIsUnknown() {
        SyncJobResponse response = syncJobService.createJob("mystery-sync", "taobao", "demo-earphone");

        assertEquals("UNSUPPORTED", response.status());
        assertTrue(response.errorMessage().contains("unsupported sync provider"));
        assertEquals("UNSUPPORTED_SOURCE", response.analysisHandoffStatus());
        verifyNoInteractions(oneBoundReviewClient);
    }

    @Test
    void shouldMarkUnsupportedWhenOneBoundPlatformIsNotSupported() {
        SyncJobResponse response = syncJobService.createJob("onebound", "jd", "600530677643");

        assertEquals("UNSUPPORTED", response.status());
        assertTrue(response.errorMessage().contains("unsupported onebound platform=jd"));
        assertEquals("UNSUPPORTED_SOURCE", response.analysisHandoffStatus());
    }

    @Test
    void shouldPullFromOneBoundAndMarkSucceeded() {
        when(oneBoundReviewClient.fetchFirstPage("taobao", "600530677643"))
                .thenReturn(new OneBoundReviewClient.FetchedReviewPage(
                        List.of(new ExternalRawReview(
                                "onebound",
                                "taobao",
                                "600530677643",
                                "rv-1",
                                "dedupe-1",
                                new BigDecimal("4.0"),
                                "Battery life is good",
                                Instant.parse("2026-04-06T10:15:30Z"),
                                "user-1",
                                "{\"page\":1}"
                        )),
                        "{\"page\":1}"
                ));
        when(externalReviewRawRepository.upsertReviews(eq("onebound"), eq("taobao"), eq("600530677643"), eq(1L), any()))
                .thenReturn(new ExternalReviewPersistenceResult("600530677643", 11L, 1, 0, 1));

        SyncJobResponse response = syncJobService.createJob("onebound", "taobao", "600530677643");

        assertEquals("SUCCEEDED", response.status());
        assertEquals(1, response.fetchedCount());
        assertEquals("taobao", response.platform());
        assertEquals(null, response.errorMessage());
        assertEquals("READY_FOR_ANALYSIS", response.analysisHandoffStatus());
    }

    @Test
    void shouldMarkFailedWhenOneBoundRequestErrors() {
        when(oneBoundReviewClient.fetchFirstPage(anyString(), anyString()))
                .thenThrow(new IllegalStateException("ONEBOUND_API_KEY is missing"));

        SyncJobResponse response = syncJobService.createJob("onebound", "taobao", "600530677643");

        assertEquals("FAILED", response.status());
        assertEquals(0, response.fetchedCount());
        assertNotNull(response.errorMessage());
        assertTrue(response.errorMessage().contains("ONEBOUND_API_KEY"));
        assertEquals("BLOCKED_SYNC_FAILED", response.analysisHandoffStatus());
    }
}
