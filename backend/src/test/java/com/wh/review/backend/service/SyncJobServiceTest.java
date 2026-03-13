package com.wh.review.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wh.review.backend.dto.SyncJobResponse;
import com.wh.review.backend.persistence.SyncJobRepository;
import java.time.Instant;
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
    private SyncJobRepository syncJobRepository;

    private SyncJobService syncJobService;

    @BeforeEach
    void setUp() {
        syncJobService = new SyncJobService(oneBoundReviewClient, syncJobRepository);
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
                            draft.errorMessage()
                    );
                });
    }

    @Test
    void shouldKeepQueuedWhenProviderIsNotOneBound() {
        SyncJobResponse response = syncJobService.createJob("aggregator-demo", null, "demo-earphone");

        assertEquals("QUEUED", response.status());
        assertEquals(0, response.fetchedCount());
        verifyNoInteractions(oneBoundReviewClient);
    }

    @Test
    void shouldPullFromOneBoundAndMarkSucceeded() {
        when(oneBoundReviewClient.fetchFirstPageReviewCount("taobao", "600530677643"))
                .thenReturn(3);

        SyncJobResponse response = syncJobService.createJob("onebound", "taobao", "600530677643");

        assertEquals("SUCCEEDED", response.status());
        assertEquals(3, response.fetchedCount());
        assertEquals("taobao", response.platform());
        assertEquals(null, response.errorMessage());
    }

    @Test
    void shouldMarkFailedWhenOneBoundRequestErrors() {
        when(oneBoundReviewClient.fetchFirstPageReviewCount(anyString(), anyString()))
                .thenThrow(new IllegalStateException("ONEBOUND_API_KEY is missing"));

        SyncJobResponse response = syncJobService.createJob("onebound", "taobao", "600530677643");

        assertEquals("FAILED", response.status());
        assertEquals(0, response.fetchedCount());
        assertNotNull(response.errorMessage());
        assertTrue(response.errorMessage().contains("ONEBOUND_API_KEY"));
    }
}
