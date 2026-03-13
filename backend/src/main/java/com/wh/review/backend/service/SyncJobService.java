package com.wh.review.backend.service;

import com.wh.review.backend.dto.SyncJobResponse;
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
    private static final String ONEBOUND_PROVIDER = "onebound";
    private static final String DEFAULT_PLATFORM = "taobao";

    private final OneBoundReviewClient oneBoundReviewClient;
    private final SyncJobRepository syncJobRepository;

    public SyncJobService(OneBoundReviewClient oneBoundReviewClient, SyncJobRepository syncJobRepository) {
        this.oneBoundReviewClient = oneBoundReviewClient;
        this.syncJobRepository = syncJobRepository;
    }

    public SyncJobResponse createJob(String provider, String platform, String targetProductCode) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String normalizedPlatform = normalizePlatform(platform);
        Instant startedAt = Instant.now();
        SyncJobResponse response = buildResponse(
                null,
                provider,
                normalizedPlatform,
                targetProductCode,
                STATUS_QUEUED,
                startedAt,
                0,
                null
        );

        Instant finishedAt = null;
        if (ONEBOUND_PROVIDER.equals(normalizedProvider)) {
            response = runOneBoundSync(provider, normalizedPlatform, targetProductCode, startedAt);
            finishedAt = Instant.now();
        }

        return syncJobRepository.create(response, finishedAt);
    }

    public Optional<SyncJobResponse> findJob(String jobId) {
        return syncJobRepository.findById(jobId);
    }

    private SyncJobResponse runOneBoundSync(
            String provider,
            String platform,
            String targetProductCode,
            Instant startedAt
    ) {
        try {
            int fetchedCount = oneBoundReviewClient.fetchFirstPageReviewCount(platform, targetProductCode);
            return buildResponse(
                    null,
                    provider,
                    platform,
                    targetProductCode,
                    STATUS_SUCCEEDED,
                    startedAt,
                    fetchedCount,
                    null
            );
        } catch (RuntimeException ex) {
            return buildResponse(
                    null,
                    provider,
                    platform,
                    targetProductCode,
                    STATUS_FAILED,
                    startedAt,
                    0,
                    sanitizeError(ex.getMessage())
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
            String errorMessage
    ) {
        return new SyncJobResponse(
                jobId,
                provider,
                platform,
                targetProductCode,
                status,
                startedAt,
                fetchedCount,
                errorMessage
        );
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return DEFAULT_PLATFORM;
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
