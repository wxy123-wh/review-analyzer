package review.backend.application;

import review.backend.api.dto.CrawlJobResponse;
import review.backend.api.dto.CrawlStartRequest;
import review.backend.api.dto.SyncJobResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.data.SyncJobRepository;
import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CrawlJobService {

    private static final Pattern JD_ITEM_PATTERN = Pattern.compile("item\\.jd\\.com/(\\d+)\\.html");
    private static final String PROVIDER_BROWSER_CRAWLER = "browser-crawler";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_WAITING_FOR_MANUAL_ACTION = "WAITING_FOR_MANUAL_ACTION";
    private static final String HANDOFF_PENDING_CRAWL = "PENDING_CRAWL";
    private static final String HANDOFF_CRAWL_RUNNING = "CRAWL_RUNNING";
    private static final String HANDOFF_READY_FOR_IMPORT = "READY_FOR_IMPORT";
    private static final String HANDOFF_MANUAL_ACTION = "WAITING_FOR_MANUAL_ACTION";
    private static final String HANDOFF_NOT_READY = "NOT_READY";

    private final JdbcTemplate jdbcTemplate;
    private final ReviewAggregationService reviewAggregationService;
    private final SyncJobRepository syncJobRepository;
    private final TaxonomyService taxonomyService;
    private final RestClient crawlerRestClient;

    public CrawlJobService(
            JdbcTemplate jdbcTemplate,
            ReviewAggregationService reviewAggregationService,
            SyncJobRepository syncJobRepository,
            TaxonomyService taxonomyService,
            RestClient.Builder restClientBuilder,
            CrawlerProperties crawlerProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.reviewAggregationService = reviewAggregationService;
        this.syncJobRepository = syncJobRepository;
        this.taxonomyService = taxonomyService;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(crawlerProperties.getConnectTimeout());
        requestFactory.setReadTimeout(crawlerProperties.getReadTimeout());
        this.crawlerRestClient = restClientBuilder
                .baseUrl(crawlerProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public CrawlJobResponse start(CrawlStartRequest request) {
        String productUrl = request.productUrl().trim();
        String platform = inferPlatform(productUrl);
        String productCode = reviewAggregationService.normalizeProductCode(
                firstNonBlank(request.productCode(), inferProductCode(platform, productUrl))
        );
        TaxonomyResponse taxonomy = taxonomyService.taxonomyForProduct(productCode, request.taxonomyId());
        int maxPackets = request.maxPackets() == null ? 20 : Math.max(1, Math.min(200, request.maxPackets()));
        String note = "browser crawler requested; productUrl=" + productUrl
                + "; taxonomyId=" + taxonomy.taxonomyId()
                + "; maxPackets=" + maxPackets
                + "; crawler service should collect JSONL and then import into productCode=" + productCode;

        SyncJobResponse job = syncJobRepository.create(
                new SyncJobResponse(
                        null,
                        PROVIDER_BROWSER_CRAWLER,
                        platform,
                        productCode,
                        STATUS_QUEUED,
                        Instant.now(),
                        0,
                        null,
                        HANDOFF_PENDING_CRAWL,
                        note
                ),
                null
        );
        jdbcTemplate.update(
                "UPDATE sync_jobs SET source_url = ?, taxonomy_id = ? WHERE id = ?",
                productUrl,
                taxonomy.taxonomyId(),
                Long.parseLong(job.jobId())
        );
        return startExternalCrawler(job, productUrl, productCode, taxonomy, maxPackets);
    }

    public Optional<CrawlJobResponse> findJob(String jobId) {
        Long id = parseId(jobId);
        if (id == null) {
            return Optional.empty();
        }
        refreshExternalCrawlerJob(id);
        List<CrawlJobResponse> rows = jdbcTemplate.query(
                """
                SELECT id,
                       provider,
                       platform,
                       target_product_code,
                       status,
                       fetched_count,
                       started_at,
                       error_message,
                       analysis_handoff_status,
                       analysis_handoff_note,
                       source_url,
                       taxonomy_id
                FROM sync_jobs
                WHERE id = ?
                """,
                this::mapCrawlJob,
                id
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    private CrawlJobResponse startExternalCrawler(
            SyncJobResponse job,
            String productUrl,
            String productCode,
            TaxonomyResponse taxonomy,
            int maxPackets
    ) {
        try {
            ExternalCrawlerJob externalJob = crawlerRestClient.post()
                    .uri("/crawl/start")
                    .body(Map.of(
                            "productUrl", productUrl,
                            "productCode", productCode,
                            "category", taxonomy.productCategory(),
                            "maxPackets", maxPackets,
                            "outputMode", "raw"
                    ))
                    .retrieve()
                    .body(ExternalCrawlerJob.class);
            if (externalJob == null || externalJob.jobId() == null || externalJob.jobId().isBlank()) {
                return markCrawlerUnavailable(job, productUrl, taxonomy.taxonomyId(), "crawler service returned empty job id");
            }
            String status = normalizeExternalStatus(externalJob.status());
            updateCrawlerSnapshot(Long.parseLong(job.jobId()), externalJob, status);
            return findJob(job.jobId()).orElseGet(() -> toCrawlJobResponse(
                    job,
                    productUrl,
                    taxonomy.taxonomyId()
            ));
        } catch (RestClientException ex) {
            return markCrawlerUnavailable(job, productUrl, taxonomy.taxonomyId(), compactMessage(ex));
        }
    }

    private CrawlJobResponse markCrawlerUnavailable(
            SyncJobResponse job,
            String productUrl,
            Long taxonomyId,
            String errorMessage
    ) {
        jdbcTemplate.update(
                """
                UPDATE sync_jobs
                SET status = ?,
                    error_message = ?,
                    analysis_handoff_status = ?,
                    analysis_handoff_note = ?
                WHERE id = ?
                """,
                STATUS_QUEUED,
                errorMessage,
                HANDOFF_PENDING_CRAWL,
                "crawler service is not reachable yet; start python -m uvicorn crawler.service:app --host 127.0.0.1 --port 8010 and retry/refresh.",
                Long.parseLong(job.jobId())
        );
        return findJob(job.jobId()).orElseGet(() -> toCrawlJobResponse(job, productUrl, taxonomyId));
    }

    private void refreshExternalCrawlerJob(long backendJobId) {
        List<ExternalBinding> bindings = jdbcTemplate.query(
                """
                SELECT external_job_id, source_url, taxonomy_id
                FROM sync_jobs
                WHERE id = ?
                """,
                (rs, rowNum) -> new ExternalBinding(
                        rs.getString("external_job_id"),
                        rs.getString("source_url"),
                        rs.getLong("taxonomy_id")
                ),
                backendJobId
        );
        if (bindings.isEmpty()) {
            return;
        }
        String externalJobId = bindings.getFirst().externalJobId();
        if (externalJobId == null || externalJobId.isBlank()) {
            return;
        }
        try {
            ExternalCrawlerJob externalJob = crawlerRestClient.get()
                    .uri("/crawl/jobs/{jobId}", externalJobId)
                    .retrieve()
                    .body(ExternalCrawlerJob.class);
            if (externalJob != null) {
                updateCrawlerSnapshot(backendJobId, externalJob, normalizeExternalStatus(externalJob.status()));
            }
        } catch (RestClientException ignored) {
            // The backend job remains queryable even if the local crawler process is temporarily offline.
        }
    }

    private void updateCrawlerSnapshot(long backendJobId, ExternalCrawlerJob externalJob, String status) {
        Timestamp finishedAt = switch (status) {
            case STATUS_SUCCEEDED, STATUS_FAILED, STATUS_WAITING_FOR_MANUAL_ACTION -> Timestamp.from(Instant.now());
            default -> null;
        };
        jdbcTemplate.update(
                """
                UPDATE sync_jobs
                SET external_job_id = ?,
                    status = ?,
                    fetched_count = ?,
                    finished_at = ?,
                    error_message = ?,
                    analysis_handoff_status = ?,
                    analysis_handoff_note = ?
                WHERE id = ?
                """,
                externalJob.jobId(),
                status,
                Math.max(0, externalJob.newReviewCount()),
                finishedAt,
                externalJob.errorMessage(),
                handoffStatus(status),
                handoffNote(status, externalJob),
                backendJobId
        );
    }

    private CrawlJobResponse mapCrawlJob(ResultSet rs, int rowNum) throws SQLException {
        long taxonomyId = rs.getLong("taxonomy_id");
        Long nullableTaxonomyId = rs.wasNull() ? null : taxonomyId;
        return new CrawlJobResponse(
                String.valueOf(rs.getLong("id")),
                rs.getString("source_url"),
                rs.getString("target_product_code"),
                nullableTaxonomyId,
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getInt("fetched_count"),
                rs.getString("error_message"),
                rs.getString("analysis_handoff_status"),
                rs.getString("analysis_handoff_note")
        );
    }

    private CrawlJobResponse toCrawlJobResponse(SyncJobResponse job, String productUrl, Long taxonomyId) {
        return new CrawlJobResponse(
                job.jobId(),
                productUrl,
                job.targetProductCode(),
                taxonomyId,
                job.status(),
                job.startedAt(),
                job.fetchedCount(),
                job.errorMessage(),
                job.analysisHandoffStatus(),
                job.analysisHandoffNote()
        );
    }

    private String inferPlatform(String productUrl) {
        String lower = productUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("jd.com")) {
            return "jd";
        }
        if (lower.contains("taobao.com") || lower.contains("tmall.com")) {
            return "taobao";
        }
        return "web";
    }

    private String normalizeExternalStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RUNNING" -> STATUS_RUNNING;
            case "SUCCEEDED" -> STATUS_SUCCEEDED;
            case "FAILED" -> STATUS_FAILED;
            case "WAITING_FOR_HUMAN", "NEEDS_HUMAN_VERIFICATION", "WAITING_FOR_MANUAL_ACTION" ->
                    STATUS_WAITING_FOR_MANUAL_ACTION;
            default -> STATUS_QUEUED;
        };
    }

    private String handoffStatus(String status) {
        return switch (status) {
            case STATUS_RUNNING -> HANDOFF_CRAWL_RUNNING;
            case STATUS_SUCCEEDED -> HANDOFF_READY_FOR_IMPORT;
            case STATUS_WAITING_FOR_MANUAL_ACTION -> HANDOFF_MANUAL_ACTION;
            case STATUS_FAILED -> HANDOFF_NOT_READY;
            default -> HANDOFF_PENDING_CRAWL;
        };
    }

    private String handoffNote(String status, ExternalCrawlerJob externalJob) {
        String outputPath = externalJob.outputPath() == null ? "" : "; outputPath=" + externalJob.outputPath();
        String message = externalJob.message() == null ? "" : externalJob.message();
        return switch (status) {
            case STATUS_RUNNING -> "crawler is running; keep the browser session available. " + message;
            case STATUS_SUCCEEDED ->
                    "crawl finished; import the JSONL into the same productCode before starting analysis" + outputPath;
            case STATUS_WAITING_FOR_MANUAL_ACTION ->
                    "crawler is waiting for manual action; handle login/captcha/risk prompt in the browser. " + message;
            case STATUS_FAILED -> "crawler failed; " + (externalJob.errorMessage() == null ? message : externalJob.errorMessage());
            default -> "crawler job is queued. " + message;
        };
    }

    private String compactMessage(Exception ex) {
        if (isTimeout(ex)) {
            return "crawler_timeout:" + messageOrClass(ex);
        }
        return messageOrClass(ex);
    }

    private boolean isTimeout(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return ex instanceof ResourceAccessException
                && ex.getMessage() != null
                && ex.getMessage().toLowerCase(Locale.ROOT).contains("timed out");
    }

    private String messageOrClass(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+", " ").trim();
    }

    private String inferProductCode(String platform, String productUrl) {
        if ("jd".equals(platform)) {
            Matcher matcher = JD_ITEM_PATTERN.matcher(productUrl);
            if (matcher.find()) {
                return "jd-" + matcher.group(1);
            }
        }
        return platform + "-" + Integer.toHexString(productUrl.hashCode());
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private Long parseId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(jobId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ExternalCrawlerJob(
            String jobId,
            String status,
            String outputPath,
            int newReviewCount,
            String errorMessage,
            String message
    ) {
    }

    private record ExternalBinding(String externalJobId, String sourceUrl, long taxonomyId) {
    }
}
