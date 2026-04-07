package com.wh.review.backend.service;

import com.wh.review.backend.dto.SyncJobResponse;
import com.wh.review.backend.persistence.ExternalReviewRawRepository;
import com.wh.review.backend.persistence.ExternalReviewRawRepository.ExternalReviewPersistenceResult;
import com.wh.review.backend.persistence.SyncJobRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SyncJobService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_UNSUPPORTED = "UNSUPPORTED";
    private static final String ONEBOUND_PROVIDER = "onebound";
    private static final String AGGREGATOR_DEMO_PROVIDER = "aggregator-demo";
    private static final String HANDOFF_CONTROLLED_DATA_PATH = "CONTROLLED_DATA_PATH";
    private static final String HANDOFF_PENDING_EXTERNAL_SYNC = "PENDING_EXTERNAL_SYNC";
    private static final String HANDOFF_READY_FOR_ANALYSIS = "READY_FOR_ANALYSIS";
    private static final String HANDOFF_EMPTY_EXTERNAL_SOURCE = "EMPTY_EXTERNAL_SOURCE";
    private static final String HANDOFF_BLOCKED_SYNC_FAILED = "BLOCKED_SYNC_FAILED";
    private static final String HANDOFF_UNSUPPORTED_SOURCE = "UNSUPPORTED_SOURCE";

    private final OneBoundReviewClient oneBoundReviewClient;
    private final OneBoundProperties oneBoundProperties;
    private final ExternalReviewRawRepository externalReviewRawRepository;
    private final SyncJobRepository syncJobRepository;

    public SyncJobService(
            OneBoundReviewClient oneBoundReviewClient,
            OneBoundProperties oneBoundProperties,
            ExternalReviewRawRepository externalReviewRawRepository,
            SyncJobRepository syncJobRepository
    ) {
        this.oneBoundReviewClient = oneBoundReviewClient;
        this.oneBoundProperties = oneBoundProperties;
        this.externalReviewRawRepository = externalReviewRawRepository;
        this.syncJobRepository = syncJobRepository;
    }

    public SyncJobResponse createJob(String provider, String platform, String targetProductCode) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String normalizedPlatform = normalizePlatform(platform);
        Instant startedAt = Instant.now();

        if (AGGREGATOR_DEMO_PROVIDER.equals(normalizedProvider)) {
            return syncJobRepository.create(
                    buildResponse(
                            null,
                            provider,
                            normalizedPlatform,
                            targetProductCode,
                            STATUS_QUEUED,
                            startedAt,
                            0,
                            null,
                            HANDOFF_CONTROLLED_DATA_PATH,
                            "controlled-data path remains primary; no external fetch attempted"
                    ),
                    null
            );
        }

        if (!ONEBOUND_PROVIDER.equals(normalizedProvider)) {
            return syncJobRepository.create(
                    buildResponse(
                            null,
                            provider,
                            normalizedPlatform,
                            targetProductCode,
                            STATUS_UNSUPPORTED,
                            startedAt,
                            0,
                            "unsupported sync provider=" + normalizedProvider,
                            HANDOFF_UNSUPPORTED_SOURCE,
                            "second-track skeleton only supports provider=onebound for external sync"
                    ),
                    Instant.now()
            );
        }

        SyncJobResponse draft = syncJobRepository.create(
                buildResponse(
                        null,
                        provider,
                        normalizedPlatform,
                        targetProductCode,
                        STATUS_QUEUED,
                        startedAt,
                        0,
                        null,
                        HANDOFF_PENDING_EXTERNAL_SYNC,
                        "awaiting onebound first-page fetch"
                ),
                null
        );
        return runOneBoundSync(draft);
    }

    public Optional<SyncJobResponse> findJob(String jobId) {
        return syncJobRepository.findById(jobId);
    }

    private SyncJobResponse runOneBoundSync(SyncJobResponse draft) {
        if (!isSupportedOneBoundPlatform(draft.platform())) {
            return syncJobRepository.updateOutcome(
                    draft.jobId(),
                    STATUS_UNSUPPORTED,
                    0,
                    Instant.now(),
                    "unsupported onebound platform=" + draft.platform(),
                    HANDOFF_UNSUPPORTED_SOURCE,
                    "only the configured default onebound platform is supported in the Task 12 skeleton"
            );
        }

        try {
            OneBoundReviewClient.FetchedReviewPage fetchedPage = oneBoundReviewClient.fetchFirstPage(
                    draft.platform(),
                    draft.targetProductCode()
            );
            ExternalReviewPersistenceResult persistenceResult = externalReviewRawRepository.upsertReviews(
                    ONEBOUND_PROVIDER,
                    draft.platform(),
                    draft.targetProductCode(),
                    Long.parseLong(draft.jobId()),
                    fetchedPage.reviews()
            );
            int fetchedCount = fetchedPage.reviews().size();
            String handoffStatus = persistenceResult.totalCount() > 0
                    ? HANDOFF_READY_FOR_ANALYSIS
                    : HANDOFF_EMPTY_EXTERNAL_SOURCE;
            String handoffNote = persistenceResult.totalCount() > 0
                    ? "persisted external raw reviews; later analysis handoff remains manual in Task 12"
                            + "; fetchedCount=" + fetchedCount
                            + "; persistedTotal=" + persistenceResult.totalCount()
                    : "external sync completed but no raw reviews were persisted for later analysis"
                            + "; fetchMetadata=" + fetchedPage.fetchMetadata();
            return syncJobRepository.updateOutcome(
                    draft.jobId(),
                    STATUS_SUCCEEDED,
                    fetchedCount,
                    Instant.now(),
                    null,
                    handoffStatus,
                    handoffNote
            );
        } catch (RuntimeException ex) {
            return syncJobRepository.updateOutcome(
                    draft.jobId(),
                    STATUS_FAILED,
                    0,
                    Instant.now(),
                    sanitizeError(ex.getMessage()),
                    HANDOFF_BLOCKED_SYNC_FAILED,
                    "external raw review persistence blocked; controlled-data launch path remains available"
            );
        }
    }

    private SyncJobResponse buildResponse(
            String jobId,
            String provider,
            String platform,
            String targetProductCode,
            String status,
            Instant startedAt,
            int fetchedCount,
            String errorMessage,
            String analysisHandoffStatus,
            String analysisHandoffNote
    ) {
        return new SyncJobResponse(
                jobId,
                provider,
                platform,
                targetProductCode,
                status,
                startedAt,
                fetchedCount,
                errorMessage,
                analysisHandoffStatus,
                analysisHandoffNote
        );
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return normalizeConfiguredPlatform(oneBoundProperties.getDefaultPlatform());
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedOneBoundPlatform(String platform) {
        return normalizeConfiguredPlatform(oneBoundProperties.getDefaultPlatform()).equals(platform);
    }

    private String normalizeConfiguredPlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "taobao";
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeError(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "onebound request failed";
        }
        return rawMessage
                .replaceAll("(?i)(key=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(secret=)[^&\\s]+", "$1***");
    }
}
