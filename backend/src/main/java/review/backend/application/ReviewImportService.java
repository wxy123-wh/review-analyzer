package review.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import review.backend.api.dto.ReviewImportItem;
import review.backend.api.dto.ReviewImportRequest;
import review.backend.api.dto.ReviewImportResponse;
import review.backend.api.dto.SyncJobResponse;
import review.backend.data.ExternalReviewRawRepository;
import review.backend.data.ExternalReviewRawRepository.ExternalRawReview;
import review.backend.data.ExternalReviewRawRepository.ExternalReviewPersistenceResult;
import review.backend.data.DataQualityRepository;
import review.backend.data.SyncJobRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReviewImportService {

    private static final String DEFAULT_PROVIDER = "local-jsonl";
    private static final String DEFAULT_PLATFORM = "jd";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String HANDOFF_READY_FOR_ANALYSIS = "READY_FOR_ANALYSIS";

    private final ExternalReviewRawRepository externalReviewRawRepository;
    private final DataQualityRepository dataQualityRepository;
    private final SyncJobRepository syncJobRepository;
    private final ObjectMapper objectMapper;

    public ReviewImportService(
            ExternalReviewRawRepository externalReviewRawRepository,
            DataQualityRepository dataQualityRepository,
            SyncJobRepository syncJobRepository,
            ObjectMapper objectMapper
    ) {
        this.externalReviewRawRepository = externalReviewRawRepository;
        this.dataQualityRepository = dataQualityRepository;
        this.syncJobRepository = syncJobRepository;
        this.objectMapper = objectMapper;
    }

    public ReviewImportResponse importReviews(ReviewImportRequest request) {
        String provider = normalize(request.provider(), DEFAULT_PROVIDER);
        String platform = normalize(request.platform(), DEFAULT_PLATFORM);
        String productCode = request.productCode().trim();
        Instant startedAt = Instant.now();

        SyncJobResponse draft = syncJobRepository.create(
                new SyncJobResponse(
                        null,
                        provider,
                        platform,
                        productCode,
                        STATUS_QUEUED,
                        startedAt,
                        0,
                        null,
                        "IMPORTING",
                        "local JSONL import accepted; persisting raw reviews"
                ),
                null
        );

        List<ExternalRawReview> externalReviews = request.reviews().stream()
                .map(item -> toExternalReview(provider, platform, productCode, item))
                .toList();
        ExternalReviewPersistenceResult result = externalReviewRawRepository.upsertReviews(
                provider,
                platform,
                productCode,
                Long.parseLong(draft.jobId()),
                externalReviews
        );
        dataQualityRepository.save(productCode, request.cleaningSummary());

        String note = "imported local JSONL reviews; next step: POST /api/v1/analysis/start with productCode="
                + productCode;
        SyncJobResponse completed = syncJobRepository.updateOutcome(
                draft.jobId(),
                STATUS_SUCCEEDED,
                externalReviews.size(),
                Instant.now(),
                null,
                HANDOFF_READY_FOR_ANALYSIS,
                note
        );

        return new ReviewImportResponse(
                completed.jobId(),
                provider,
                platform,
                productCode,
                request.reviews().size(),
                result.insertedCount(),
                result.updatedCount(),
                result.totalCount(),
                completed.analysisHandoffStatus(),
                completed.analysisHandoffNote(),
                Instant.now()
        );
    }

    private ExternalRawReview toExternalReview(
            String provider,
            String platform,
            String productCode,
            ReviewImportItem item
    ) {
        String sourceReviewId = firstNonBlank(item.sourceReviewId(), stableReviewId(productCode, item));
        String dedupeKey = sha256(productCode + "|" + sourceReviewId + "|" + item.content());
        return new ExternalRawReview(
                provider,
                platform,
                productCode,
                sourceReviewId,
                dedupeKey,
                normalizeRating(item.rating()),
                item.content().trim(),
                item.reviewTime(),
                firstNonBlank(item.anonymizedAuthorId(), "anonymous"),
                metadataJson(item)
        );
    }

    private BigDecimal normalizeRating(BigDecimal rating) {
        if (rating == null) {
            return null;
        }
        return rating.max(BigDecimal.ZERO).min(BigDecimal.valueOf(5));
    }

    private String metadataJson(ReviewImportItem item) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "source", firstNonBlank(item.source(), "jd"),
                    "category", firstNonBlank(item.category(), ""),
                    "skuInfo", firstNonBlank(item.skuInfo(), "")
            ));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String stableReviewId(String productCode, ReviewImportItem item) {
        return "local-" + sha256(productCode + "|" + item.content() + "|" + item.reviewTime()).substring(0, 32);
    }

    private String normalize(String value, String fallback) {
        return firstNonBlank(value, fallback).trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }
}
