package review.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import review.backend.api.dto.CrawlImportResponse;
import review.backend.api.dto.CrawlReviewSample;
import review.backend.api.dto.ReviewImportItem;
import review.backend.api.dto.ReviewImportRequest;
import review.backend.api.dto.ReviewImportResponse;
import review.backend.api.dto.ReviewJsonlFileCandidate;
import review.backend.api.dto.SyncJobResponse;
import review.backend.data.AnalysisJobRepository;
import review.backend.data.ExternalReviewRawRepository;
import review.backend.data.ExternalReviewRawRepository.ExternalRawReview;
import review.backend.data.ExternalReviewRawRepository.ExternalReviewPersistenceResult;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.DataQualityRepository;
import review.backend.data.SyncJobRepository;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
public class ReviewImportService {

    private static final String DEFAULT_PROVIDER = "local-jsonl";
    private static final String DEFAULT_PLATFORM = "jd";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String HANDOFF_READY_FOR_ANALYSIS = "READY_FOR_ANALYSIS";
    private static final String HANDOFF_READY_FOR_TAXONOMY_BINDING = "READY_FOR_TAXONOMY_BINDING";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200b\\u200c\\u200d\\ufeff]");
    private static final Pattern PLACEHOLDER_COMMENT_PATTERN = Pattern.compile("(?:此|该)?用户(?:未及时|没有|未|尚未|未按时)?填写评价内容");
    private static final Pattern LEADING_SEPARATOR_PATTERN = Pattern.compile("^[\\s|｜:：,，;；。.!！?？\\-—_]+");
    private static final Pattern TRAILING_SEPARATOR_PATTERN = Pattern.compile("[\\s|｜:：,，;；\\-—_]+$");
    private static final Pattern RAW_JSONL_FILENAME_PATTERN = Pattern.compile("^raw_reviews_(.+)\\.jsonl$", Pattern.CASE_INSENSITIVE);
    private static final Path JSONL_DISCOVERY_DIRECTORY = Path.of("crawler", "output");
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOCAL_DATE_TIME_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final ExternalReviewRawRepository externalReviewRawRepository;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final DataQualityRepository dataQualityRepository;
    private final SyncJobRepository syncJobRepository;
    private final ObjectMapper objectMapper;

    public ReviewImportService(
            ExternalReviewRawRepository externalReviewRawRepository,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            AnalysisJobRepository analysisJobRepository,
            DataQualityRepository dataQualityRepository,
            SyncJobRepository syncJobRepository,
            ObjectMapper objectMapper
    ) {
        this.externalReviewRawRepository = externalReviewRawRepository;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.dataQualityRepository = dataQualityRepository;
        this.syncJobRepository = syncJobRepository;
        this.objectMapper = objectMapper;
    }

    public ReviewImportResponse importReviews(ReviewImportRequest request) {
        String provider = normalize(request.provider(), DEFAULT_PROVIDER);
        String platform = normalize(request.platform(), DEFAULT_PLATFORM);
        String productCode = request.productCode().trim();
        ImportSanitizationResult sanitized = sanitizeImportItems(request.reviews(), request.cleaningSummary());
        if (sanitized.reviews().isEmpty()) {
            throw new IllegalStateException("no importable reviews after cleaning placeholder or empty content for productCode=" + productCode);
        }
        String productName = resolveProductName(request.productName(), sanitized.reviews(), productCode);
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

        List<ExternalRawReview> externalReviews = sanitized.reviews().stream()
                .map(item -> toExternalReview(provider, platform, productCode, item))
                .toList();
        ExternalReviewPersistenceResult result = externalReviewRawRepository.upsertReviews(
                provider,
                platform,
                productCode,
                productName,
                Long.parseLong(draft.jobId()),
                externalReviews
        );
        dataQualityRepository.save(productCode, sanitized.cleaningSummary());

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
                productName,
                sanitized.reviews().size(),
                result.insertedCount(),
                result.updatedCount(),
                result.totalCount(),
                completed.analysisHandoffStatus(),
                completed.analysisHandoffNote(),
                Instant.now()
        );
    }

    public CrawlImportResponse importJsonlFile(
            String crawlJobId,
            String productCode,
            String platform,
            Path rawOutputPath
    ) {
        return importJsonlFile(crawlJobId, productCode, null, platform, rawOutputPath);
    }

    public CrawlImportResponse importJsonlFile(
            String crawlJobId,
            String productCode,
            String productName,
            String platform,
            Path inputPath
    ) {
        return importJsonlFile(crawlJobId, productCode, productName, platform, inputPath, false);
    }

    @Transactional
    public CrawlImportResponse importJsonlFile(
            String crawlJobId,
            String productCode,
            String productName,
            String platform,
            Path inputPath,
            boolean replaceExisting
    ) {
        if (replaceExisting && analysisJobRepository.hasActiveJobForProduct(productCode)) {
            throw new IllegalStateException(
                    "当前商品仍有 LLM 分析任务正在运行，不能覆盖导入评论。请等待任务结束后刷新结果；如果确实要替换数据，请等当前任务完成或失败后再重新导入。"
            );
        }
        JsonlCleaningResult cleaningResult = isCleanedJsonl(inputPath)
                ? cleanedJsonlResult(inputPath, productCode)
                : cleanJsonl(inputPath, productCode, productName);
        List<ReviewImportItem> reviews = readImportItems(cleaningResult.cleanedOutputPath(), productCode);
        String resolvedProductName = resolveProductName(productName, reviews, productCode);
        String normalizedPlatform = normalize(platform, DEFAULT_PLATFORM);
        if (replaceExisting) {
            analysisMaterializationRepository.clearOutputs(productCode);
            externalReviewRawRepository.deleteReviews(DEFAULT_PROVIDER, normalizedPlatform, productCode);
        }
        ReviewImportResponse importResponse = importReviews(new ReviewImportRequest(
                DEFAULT_PROVIDER,
                normalizedPlatform,
                productCode,
                resolvedProductName,
                reviews,
                cleaningResult.summary()
        ));

        return new CrawlImportResponse(
                crawlJobId,
                importResponse.importJobId(),
                importResponse.productCode(),
                importResponse.productName(),
                importResponse.provider(),
                importResponse.platform(),
                cleaningResult.rawOutputPath().toString(),
                cleaningResult.cleanedOutputPath().toString(),
                cleaningResult.removedOutputPath().toString(),
                cleaningResult.summaryOutputPath().toString(),
                importResponse.receivedCount(),
                importResponse.insertedReviewCount(),
                importResponse.updatedReviewCount(),
                importResponse.totalReviewCount(),
                cleaningResult.summary(),
                sampleReviews(cleaningResult.cleanedOutputPath(), 3),
                importResponse.analysisHandoffStatus(),
                importResponse.analysisHandoffNote(),
                importResponse.importedAt()
        );
    }

    public CrawlImportResponse cleanJsonlFile(
            String jobId,
            String productCode,
            String productName,
            String platform,
            Path rawOutputPath
    ) {
        JsonlCleaningResult cleaningResult = cleanJsonl(rawOutputPath, productCode, productName);
        List<CrawlReviewSample> samples = sampleReviews(cleaningResult.cleanedOutputPath(), 3);
        String resolvedProductName = resolveProductNameFromSamples(productName, samples, productCode);
        String normalizedPlatform = normalize(platform, DEFAULT_PLATFORM);
        String note = "cleaned JSONL is ready; bind taxonomy before starting background LLM analysis for productCode="
                + productCode;

        return new CrawlImportResponse(
                jobId,
                null,
                productCode,
                resolvedProductName,
                DEFAULT_PROVIDER,
                normalizedPlatform,
                cleaningResult.rawOutputPath().toString(),
                cleaningResult.cleanedOutputPath().toString(),
                cleaningResult.removedOutputPath().toString(),
                cleaningResult.summaryOutputPath().toString(),
                intSummary(cleaningResult.summary(), "cleanedCount"),
                0,
                0,
                0,
                cleaningResult.summary(),
                samples,
                HANDOFF_READY_FOR_TAXONOMY_BINDING,
                note,
                Instant.now()
        );
    }

    public List<ReviewJsonlFileCandidate> discoverJsonlFiles() {
        Path discoveryDirectory = JSONL_DISCOVERY_DIRECTORY.toAbsolutePath().normalize();
        if (!Files.isDirectory(discoveryDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(discoveryDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isRawJsonlCandidate)
                    .map(this::toJsonlFileCandidate)
                    .sorted(Comparator.comparing(ReviewJsonlFileCandidate::lastModifiedAt).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private boolean isRawJsonlCandidate(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jsonl")
                && normalized.startsWith("raw_reviews_")
                && !normalized.startsWith("cleaned_")
                && !normalized.startsWith("removed_")
                && !normalized.startsWith("dry_run_");
    }

    private ReviewJsonlFileCandidate toJsonlFileCandidate(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        String fileName = normalizedPath.getFileName() == null ? normalizedPath.toString() : normalizedPath.getFileName().toString();
        String productCode = firstNonBlank(inferProductCodeFromFilename(fileName), firstTextField(normalizedPath, "productCode"));
        List<CrawlReviewSample> samples = sampleReviews(normalizedPath, 3);
        String productName = samples.stream()
                .map(CrawlReviewSample::productName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> firstTextField(normalizedPath, "productName"));
        try {
            return new ReviewJsonlFileCandidate(
                    displayPath(normalizedPath),
                    fileName,
                    firstNonBlank(productCode, ""),
                    firstNonBlank(productName, ""),
                    Files.size(normalizedPath),
                    Files.getLastModifiedTime(normalizedPath).toInstant(),
                    samples
            );
        } catch (IOException ex) {
            return new ReviewJsonlFileCandidate(
                    displayPath(normalizedPath),
                    fileName,
                    firstNonBlank(productCode, ""),
                    firstNonBlank(productName, ""),
                    0L,
                    Instant.EPOCH,
                    samples
            );
        }
    }

    private String displayPath(Path normalizedPath) {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path display = normalizedPath;
        if (normalizedPath.startsWith(root)) {
            display = root.relativize(normalizedPath);
        }
        return display.toString().replace('\\', '/');
    }

    private String inferProductCodeFromFilename(String fileName) {
        Matcher matcher = RAW_JSONL_FILENAME_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(1) : "";
    }

    private String firstTextField(Path jsonlPath, String fieldName) {
        try (BufferedReader reader = Files.newBufferedReader(jsonlPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                if (node.isObject()) {
                    String value = textValue(node.get(fieldName));
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    private ImportSanitizationResult sanitizeImportItems(
            List<ReviewImportItem> sourceItems,
            Map<String, Object> sourceSummary
    ) {
        List<ReviewImportItem> sanitizedItems = new ArrayList<>();
        Map<String, Object> summary = sourceSummary == null
                ? newCleaningSummary(sourceItems == null ? 0 : sourceItems.size())
                : new LinkedHashMap<>(sourceSummary);
        summary.putIfAbsent("placeholderContentCount", 0);

        int removedBySanitizer = 0;
        int placeholderTouched = 0;
        int htmlTouched = 0;
        if (sourceItems != null) {
            for (ReviewImportItem item : sourceItems) {
                CleanedContent cleanedContent = cleanContent(item.content());
                if (cleanedContent.placeholderCleaned()) {
                    placeholderTouched++;
                }
                if (cleanedContent.htmlCleaned()) {
                    htmlTouched++;
                }
                if (cleanedContent.content().isBlank()) {
                    removedBySanitizer++;
                    continue;
                }
                sanitizedItems.add(new ReviewImportItem(
                        item.source(),
                        item.sourceReviewId(),
                        item.productCode(),
                        item.productName(),
                        item.category(),
                        item.rating(),
                        cleanedContent.content(),
                        item.reviewTime(),
                        item.skuInfo(),
                        item.anonymizedAuthorId()
                ));
            }
        }

        if (sourceSummary == null) {
            summary.put("cleanedCount", sanitizedItems.size());
            summary.put("removedCount", removedBySanitizer);
            summary.put("htmlCleanedCount", htmlTouched);
        } else {
            addToSummary(summary, "removedCount", removedBySanitizer);
            addToSummary(summary, "htmlCleanedCount", htmlTouched);
            Object cleanedCount = summary.get("cleanedCount");
            if (cleanedCount instanceof Number number && removedBySanitizer > 0) {
                summary.put("cleanedCount", Math.max(0, number.intValue() - removedBySanitizer));
            }
        }
        addToSummary(summary, "placeholderContentCount", placeholderTouched);
        return new ImportSanitizationResult(sanitizedItems, summary);
    }

    private Map<String, Object> newCleaningSummary(int rawCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rawCount", rawCount);
        summary.put("cleanedCount", 0);
        summary.put("removedCount", 0);
        summary.put("htmlCleanedCount", 0);
        summary.put("exactDuplicateCount", 0);
        summary.put("emptyContentCount", 0);
        summary.put("invalidJsonCount", 0);
        summary.put("placeholderContentCount", 0);
        return summary;
    }

    private void addToSummary(Map<String, Object> summary, String key, int delta) {
        if (delta <= 0) {
            return;
        }
        Object value = summary.get(key);
        int current = value instanceof Number number ? number.intValue() : 0;
        summary.put(key, current + delta);
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

    private JsonlCleaningResult cleanJsonl(Path rawOutputPath, String productCode) {
        return cleanJsonl(rawOutputPath, productCode, null);
    }

    private JsonlCleaningResult cleanJsonl(Path rawOutputPath, String productCode, String productName) {
        Path normalizedRawPath = rawOutputPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRawPath)) {
            throw new IllegalStateException("raw JSONL file not found: " + normalizedRawPath);
        }

        Path outputDirectory = normalizedRawPath.getParent() == null
                ? Path.of("pipeline", "output").toAbsolutePath().normalize()
                : normalizedRawPath.getParent().resolve("cleaned").toAbsolutePath().normalize();
        String safeProductCode = safeFilename(productCode);
        Path cleanedOutputPath = outputDirectory.resolve("cleaned_reviews_" + safeProductCode + ".jsonl");
        Path removedOutputPath = outputDirectory.resolve("removed_reviews_" + safeProductCode + ".jsonl");
        Path summaryOutputPath = outputDirectory.resolve("cleaning_summary_" + safeProductCode + ".json");

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create JSONL cleaning output directory: " + outputDirectory, ex);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rawCount", 0);
        summary.put("cleanedCount", 0);
        summary.put("removedCount", 0);
        summary.put("htmlCleanedCount", 0);
        summary.put("exactDuplicateCount", 0);
        summary.put("emptyContentCount", 0);
        summary.put("invalidJsonCount", 0);
        summary.put("placeholderContentCount", 0);

        Set<String> seen = new LinkedHashSet<>();
        try (
                BufferedReader reader = Files.newBufferedReader(normalizedRawPath, StandardCharsets.UTF_8);
                BufferedWriter cleaned = Files.newBufferedWriter(cleanedOutputPath, StandardCharsets.UTF_8);
                BufferedWriter removed = Files.newBufferedWriter(removedOutputPath, StandardCharsets.UTF_8)
        ) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String rawLine = line.strip();
                increment(summary, "rawCount");
                if (rawLine.isBlank()) {
                    increment(summary, "removedCount");
                    increment(summary, "emptyContentCount");
                    writeRemoved(removed, lineNumber, "empty_content", line);
                    continue;
                }

                JsonNode node;
                try {
                    node = objectMapper.readTree(rawLine);
                } catch (IOException ex) {
                    increment(summary, "removedCount");
                    increment(summary, "invalidJsonCount");
                    writeRemoved(removed, lineNumber, "invalid_json", rawLine);
                    continue;
                }

                if (!node.isObject()) {
                    increment(summary, "removedCount");
                    increment(summary, "invalidJsonCount");
                    writeRemoved(removed, lineNumber, "invalid_json", node);
                    continue;
                }

                ObjectNode review = ((ObjectNode) node).deepCopy();
                CleanedContent cleanedContent = cleanContent(textValue(review.get("content")));
                if (cleanedContent.placeholderCleaned()) {
                    increment(summary, "placeholderContentCount");
                }
                if (cleanedContent.content().isBlank()) {
                    increment(summary, "removedCount");
                    if (cleanedContent.placeholderCleaned()) {
                        writeRemoved(removed, lineNumber, "placeholder_content", review);
                    } else {
                        increment(summary, "emptyContentCount");
                        writeRemoved(removed, lineNumber, "empty_content", review);
                    }
                    continue;
                }
                review.put("content", cleanedContent.content());
                review.put("productCode", productCode);
                String resolvedProductName = firstNonBlank(textValue(review.get("productName")), productName);
                if (resolvedProductName != null && !resolvedProductName.isBlank()) {
                    review.put("productName", resolvedProductName.trim());
                }

                String dedupeKey = reviewKey(review, productCode);
                if (seen.contains(dedupeKey)) {
                    increment(summary, "removedCount");
                    increment(summary, "exactDuplicateCount");
                    writeRemoved(removed, lineNumber, "exact_duplicate", review);
                    continue;
                }

                seen.add(dedupeKey);
                if (cleanedContent.htmlCleaned()) {
                    increment(summary, "htmlCleanedCount");
                }
                increment(summary, "cleanedCount");
                cleaned.write(objectMapper.writeValueAsString(review));
                cleaned.newLine();
            }

            Files.writeString(summaryOutputPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to clean JSONL file: " + normalizedRawPath, ex);
        }

        return new JsonlCleaningResult(normalizedRawPath, cleanedOutputPath, removedOutputPath, summaryOutputPath, summary);
    }

    private boolean isCleanedJsonl(Path inputPath) {
        String fileName = inputPath.getFileName() == null ? "" : inputPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.startsWith("cleaned_reviews_") && fileName.endsWith(".jsonl");
    }

    private JsonlCleaningResult cleanedJsonlResult(Path cleanedOutputPath, String productCode) {
        Path normalizedCleanedPath = cleanedOutputPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedCleanedPath)) {
            throw new IllegalStateException("cleaned JSONL file not found: " + normalizedCleanedPath);
        }
        Path outputDirectory = normalizedCleanedPath.getParent() == null
                ? Path.of("pipeline", "output").toAbsolutePath().normalize()
                : normalizedCleanedPath.getParent().toAbsolutePath().normalize();
        String safeProductCode = safeFilename(productCode);
        Path removedOutputPath = outputDirectory.resolve("removed_reviews_" + safeProductCode + ".jsonl");
        Path summaryOutputPath = outputDirectory.resolve("cleaning_summary_" + safeProductCode + ".json");
        int cleanedRows = countJsonlRows(normalizedCleanedPath);
        Map<String, Object> summary = readCleaningSummary(summaryOutputPath)
                .orElseGet(() -> newCleaningSummary(cleanedRows));
        Object cleanedCount = summary.get("cleanedCount");
        if (!(cleanedCount instanceof Number number) || number.intValue() <= 0) {
            summary.put("cleanedCount", cleanedRows);
        }
        return new JsonlCleaningResult(
                normalizedCleanedPath,
                normalizedCleanedPath,
                removedOutputPath,
                summaryOutputPath,
                summary
        );
    }

    private Optional<Map<String, Object>> readCleaningSummary(Path summaryOutputPath) {
        if (!Files.exists(summaryOutputPath)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(summaryOutputPath.toFile());
            if (!node.isObject()) {
                return Optional.empty();
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    summary.put(entry.getKey(), value.numberValue());
                } else if (value.isBoolean()) {
                    summary.put(entry.getKey(), value.booleanValue());
                } else {
                    summary.put(entry.getKey(), value.asText());
                }
            });
            return Optional.of(summary);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private int countJsonlRows(Path jsonlPath) {
        try (Stream<String> lines = Files.lines(jsonlPath, StandardCharsets.UTF_8)) {
            return (int) lines.filter(line -> !line.isBlank()).count();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to count cleaned JSONL rows: " + jsonlPath, ex);
        }
    }

    private List<ReviewImportItem> readImportItems(Path cleanedOutputPath, String productCode) {
        List<ReviewImportItem> reviews = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(cleanedOutputPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                reviews.add(toImportItem(node, productCode));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read cleaned JSONL file: " + cleanedOutputPath, ex);
        }
        if (reviews.isEmpty()) {
            throw new IllegalStateException("cleaned JSONL contains no importable reviews: " + cleanedOutputPath);
        }
        return reviews;
    }

    private ReviewImportItem toImportItem(JsonNode node, String productCode) {
        return new ReviewImportItem(
                firstNonBlank(textValue(node.get("source")), "jd"),
                textValue(node.get("sourceReviewId")),
                productCode,
                textValue(node.get("productName")),
                textValue(node.get("category")),
                decimalValue(node.get("rating")),
                textValue(node.get("content")),
                parseReviewTime(textValue(node.get("reviewTime"))),
                textValue(node.get("skuInfo")),
                firstNonBlank(textValue(node.get("anonymizedAuthorId")), "anonymous")
        );
    }

    public List<CrawlReviewSample> sampleReviews(Path jsonlPath, int limit) {
        List<CrawlReviewSample> samples = new ArrayList<>();
        Path normalizedPath = jsonlPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPath)) {
            return samples;
        }
        try (BufferedReader reader = Files.newBufferedReader(normalizedPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && samples.size() < limit) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                samples.add(new CrawlReviewSample(
                        textValue(node.get("sourceReviewId")),
                        textValue(node.get("productName")),
                        textValue(node.get("content")),
                        textValue(node.get("rating")),
                        textValue(node.get("reviewTime"))
                ));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return samples;
    }

    private CleanedContent cleanContent(String raw) {
        String rawValue = raw == null ? "" : raw;
        String unescaped = HtmlUtils.htmlUnescape(rawValue);
        String withoutTags = HTML_TAG_PATTERN.matcher(unescaped).replaceAll(" ");
        String withoutZeroWidth = ZERO_WIDTH_PATTERN.matcher(withoutTags).replaceAll("");
        String normalized = WHITESPACE_PATTERN
                .matcher(withoutZeroWidth.replace('\u00A0', ' ').replace('\r', ' ').replace('\n', ' '))
                .replaceAll(" ")
                .trim();
        String withoutPlaceholder = PLACEHOLDER_COMMENT_PATTERN.matcher(normalized).replaceAll(" ");
        withoutPlaceholder = LEADING_SEPARATOR_PATTERN.matcher(withoutPlaceholder).replaceAll("");
        withoutPlaceholder = TRAILING_SEPARATOR_PATTERN.matcher(withoutPlaceholder).replaceAll("");
        normalized = WHITESPACE_PATTERN.matcher(withoutPlaceholder).replaceAll(" ").trim();
        boolean htmlCleaned = HTML_TAG_PATTERN.matcher(rawValue).find() || !unescaped.equals(rawValue);
        boolean placeholderCleaned = PLACEHOLDER_COMMENT_PATTERN.matcher(rawValue).find()
                || PLACEHOLDER_COMMENT_PATTERN.matcher(unescaped).find();
        return new CleanedContent(normalized, htmlCleaned, placeholderCleaned);
    }

    private void writeRemoved(BufferedWriter writer, int lineNumber, String reason, Object raw) throws IOException {
        writer.write(objectMapper.writeValueAsString(Map.of(
                "lineNumber", lineNumber,
                "removeReason", reason,
                "raw", raw
        )));
        writer.newLine();
    }

    private void increment(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        int current = value instanceof Number number ? number.intValue() : 0;
        summary.put(key, current + 1);
    }

    private String reviewKey(ObjectNode review, String fallbackProductCode) {
        String source = textValue(review.get("source"));
        String sourceReviewId = textValue(review.get("sourceReviewId"));
        if (!sourceReviewId.isBlank()) {
            return "id:" + source + "|" + sourceReviewId;
        }
        String productCode = firstNonBlank(textValue(review.get("productCode")), fallbackProductCode);
        String content = textValue(review.get("content"));
        String reviewTime = textValue(review.get("reviewTime"));
        return "hash:" + sha256(productCode + "|" + content + "|" + reviewTime);
    }

    private BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = textValue(node);
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant parseReviewTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        String normalized = value.trim();
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Try common e-commerce local time formats below.
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try local date-time formats below.
        }
        for (DateTimeFormatter formatter : List.of(LOCAL_DATE_TIME, LOCAL_DATE_TIME_SLASH)) {
            try {
                return LocalDateTime.parse(normalized, formatter).atZone(CHINA_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // Continue trying other formats.
            }
        }
        try {
            return LocalDate.parse(normalized).atStartOfDay(CHINA_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        return node.asText("").trim();
    }

    private String safeFilename(String value) {
        String safe = firstNonBlank(value, "product").replaceAll("[^a-zA-Z0-9_.-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "product" : safe;
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
                    "productName", firstNonBlank(item.productName(), ""),
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

    private String resolveProductName(String requestedProductName, List<ReviewImportItem> reviews, String productCode) {
        String normalized = firstNonBlank(requestedProductName, "");
        if (!normalized.isBlank()) {
            return normalized.trim();
        }
        for (ReviewImportItem review : reviews) {
            String itemProductName = firstNonBlank(review.productName(), "");
            if (!itemProductName.isBlank()) {
                return itemProductName.trim();
            }
        }
        return productCode;
    }

    private String resolveProductNameFromSamples(String requestedProductName, List<CrawlReviewSample> samples, String productCode) {
        String normalized = firstNonBlank(requestedProductName, "");
        if (!normalized.isBlank()) {
            return normalized.trim();
        }
        for (CrawlReviewSample sample : samples) {
            String sampleProductName = firstNonBlank(sample.productName(), "");
            if (!sampleProductName.isBlank()) {
                return sampleProductName.trim();
            }
        }
        return productCode;
    }

    private int intSummary(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private record JsonlCleaningResult(
            Path rawOutputPath,
            Path cleanedOutputPath,
            Path removedOutputPath,
            Path summaryOutputPath,
            Map<String, Object> summary
    ) {
    }

    private record ImportSanitizationResult(
            List<ReviewImportItem> reviews,
            Map<String, Object> cleaningSummary
    ) {
    }

    private record CleanedContent(String content, boolean htmlCleaned, boolean placeholderCleaned) {
    }
}
