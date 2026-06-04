package review.backend.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import review.backend.api.dto.AnalysisJobResponse;
import review.backend.api.dto.CrawlReviewSample;
import review.backend.api.dto.ReviewIntakeStatusResponse;
import review.backend.api.dto.TaxonomyResponse;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.ProductRepository;
import review.backend.data.ReviewQueryRepository;
import review.backend.data.ReviewQueryRepository.RecentReviewRecord;
import review.backend.data.TaxonomyRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ReviewIntakeStatusService {

    private final ReviewAggregationService reviewAggregationService;
    private final ReviewImportService reviewImportService;
    private final ReviewQueryRepository reviewQueryRepository;
    private final AnalysisMaterializationRepository analysisMaterializationRepository;
    private final AnalysisJobService analysisJobService;
    private final ProductRepository productRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final ObjectMapper objectMapper;

    public ReviewIntakeStatusService(
            ReviewAggregationService reviewAggregationService,
            ReviewImportService reviewImportService,
            ReviewQueryRepository reviewQueryRepository,
            AnalysisMaterializationRepository analysisMaterializationRepository,
            AnalysisJobService analysisJobService,
            ProductRepository productRepository,
            TaxonomyRepository taxonomyRepository,
            ObjectMapper objectMapper
    ) {
        this.reviewAggregationService = reviewAggregationService;
        this.reviewImportService = reviewImportService;
        this.reviewQueryRepository = reviewQueryRepository;
        this.analysisMaterializationRepository = analysisMaterializationRepository;
        this.analysisJobService = analysisJobService;
        this.productRepository = productRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.objectMapper = objectMapper;
    }

    public ReviewIntakeStatusResponse status(String productCode, String inputPath) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        Path rawPath = resolveInputPath(normalizedProductCode, inputPath);
        JsonlArtifactPaths artifacts = artifactPaths(rawPath, normalizedProductCode);
        boolean rawExists = Files.exists(artifacts.rawOutputPath());
        boolean cleanedExists = Files.exists(artifacts.cleanedOutputPath());
        Map<String, Object> summary = readCleaningSummary(artifacts.cleaningSummaryPath());
        int rawCount = intValue(summary, "rawCount", rawExists ? countJsonlRows(artifacts.rawOutputPath()) : 0);
        int importedReviewCount = reviewQueryRepository.countByProductCode(normalizedProductCode);
        int analyzedReviewCount = analysisMaterializationRepository.countMaterializedReviews(normalizedProductCode);
        int semanticLabelCount = analysisMaterializationRepository.countSemanticLabels(normalizedProductCode);
        Optional<TaxonomyResponse> taxonomy = taxonomyRepository.findBoundForProduct(normalizedProductCode);
        AnalysisJobResponse latestJob = analysisJobService.findLatestJobForProduct(normalizedProductCode).orElse(null);
        boolean downstreamReady = analyzedReviewCount > 0 && semanticLabelCount > 0;
        List<CrawlReviewSample> recentReviews = importedReviewCount > 0
                ? recentDatabaseReviews(normalizedProductCode)
                : (cleanedExists ? reviewImportService.sampleReviews(artifacts.cleanedOutputPath(), 10) : List.of());
        String productName = productRepository.findProductName(normalizedProductCode)
                .orElseGet(() -> firstSampleProductName(recentReviews, normalizedProductCode));

        return new ReviewIntakeStatusResponse(
                normalizedProductCode,
                productName,
                displayPath(artifacts.rawOutputPath()),
                rawExists,
                rawCount,
                displayPath(artifacts.cleanedOutputPath()),
                cleanedExists,
                displayPath(artifacts.removedOutputPath()),
                displayPath(artifacts.cleaningSummaryPath()),
                summary,
                taxonomy.isPresent(),
                taxonomy.map(TaxonomyResponse::taxonomyId).orElse(null),
                taxonomy.map(TaxonomyResponse::version).orElse(null),
                importedReviewCount,
                analyzedReviewCount,
                downstreamReady,
                latestJob,
                recentReviews,
                stage(cleanedExists, taxonomy.isPresent(), importedReviewCount, analyzedReviewCount, downstreamReady, latestJob),
                notice(cleanedExists, taxonomy.isPresent(), importedReviewCount, analyzedReviewCount, downstreamReady, latestJob)
        );
    }

    private Path resolveInputPath(String productCode, String inputPath) {
        if (inputPath != null && !inputPath.isBlank()) {
            return Path.of(inputPath.trim()).toAbsolutePath().normalize();
        }
        return Path.of("crawler", "output", "raw_reviews_" + safeFilename(productCode) + ".jsonl")
                .toAbsolutePath()
                .normalize();
    }

    private JsonlArtifactPaths artifactPaths(Path inputPath, String productCode) {
        String fileName = inputPath.getFileName() == null ? "" : inputPath.getFileName().toString().toLowerCase(Locale.ROOT);
        String safeProductCode = safeFilename(productCode);
        if (fileName.startsWith("cleaned_reviews_") && fileName.endsWith(".jsonl")) {
            Path outputDirectory = inputPath.getParent() == null
                    ? Path.of("pipeline", "output").toAbsolutePath().normalize()
                    : inputPath.getParent().toAbsolutePath().normalize();
            return new JsonlArtifactPaths(
                    inputPath,
                    inputPath,
                    outputDirectory.resolve("removed_reviews_" + safeProductCode + ".jsonl"),
                    outputDirectory.resolve("cleaning_summary_" + safeProductCode + ".json")
            );
        }
        Path outputDirectory = inputPath.getParent() == null
                ? Path.of("pipeline", "output").toAbsolutePath().normalize()
                : inputPath.getParent().resolve("cleaned").toAbsolutePath().normalize();
        return new JsonlArtifactPaths(
                inputPath,
                outputDirectory.resolve("cleaned_reviews_" + safeProductCode + ".jsonl"),
                outputDirectory.resolve("removed_reviews_" + safeProductCode + ".jsonl"),
                outputDirectory.resolve("cleaning_summary_" + safeProductCode + ".json")
        );
    }

    private Map<String, Object> readCleaningSummary(Path summaryPath) {
        if (!Files.exists(summaryPath)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(summaryPath.toFile());
            if (!node.isObject()) {
                return Map.of();
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
            return summary;
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private int countJsonlRows(Path path) {
        if (!Files.exists(path)) {
            return 0;
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return (int) lines.filter(line -> !line.isBlank()).count();
        } catch (IOException ex) {
            return 0;
        }
    }

    private List<CrawlReviewSample> recentDatabaseReviews(String productCode) {
        return reviewQueryRepository.findRecentByProductCode(productCode, 10).stream()
                .map(this::toSample)
                .toList();
    }

    private CrawlReviewSample toSample(RecentReviewRecord record) {
        return new CrawlReviewSample(
                record.sourceReviewId(),
                record.productName(),
                record.content(),
                formatRating(record.rating()),
                formatInstant(record.reviewTime())
        );
    }

    private String formatRating(BigDecimal rating) {
        if (rating == null) {
            return "";
        }
        return rating.stripTrailingZeros().toPlainString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String firstSampleProductName(List<CrawlReviewSample> samples, String fallback) {
        return samples.stream()
                .map(CrawlReviewSample::productName)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private String stage(
            boolean cleanedExists,
            boolean taxonomyBound,
            int importedReviewCount,
            int analyzedReviewCount,
            boolean downstreamReady,
            AnalysisJobResponse latestJob
    ) {
        if (latestJob != null && "FAILED".equals(latestJob.status())) {
            return "ANALYSIS_FAILED";
        }
        if (latestJob != null && ("QUEUED".equals(latestJob.status()) || "RUNNING".equals(latestJob.status()))) {
            return "ANALYZING";
        }
        if ((latestJob != null && "SUCCEEDED".equals(latestJob.status()) && !downstreamReady)
                || (analyzedReviewCount > 0 && !downstreamReady)) {
            return "ANALYSIS_INCOMPLETE";
        }
        if (downstreamReady) {
            return "ANALYZED";
        }
        if (importedReviewCount > 0) {
            return "IMPORTED";
        }
        if (taxonomyBound) {
            return "TAXONOMY_BOUND";
        }
        if (cleanedExists) {
            return "CLEANED";
        }
        return "RAW_READY";
    }

    private String notice(
            boolean cleanedExists,
            boolean taxonomyBound,
            int importedReviewCount,
            int analyzedReviewCount,
            boolean downstreamReady,
            AnalysisJobResponse latestJob
    ) {
        if (latestJob != null && "FAILED".equals(latestJob.status())) {
            return "LLM 分析失败，请查看任务错误信息或重新启动分析。";
        }
        if (latestJob != null && ("QUEUED".equals(latestJob.status()) || "RUNNING".equals(latestJob.status()))) {
            return "LLM 正在分析评论，可查看任务进度和最近评论列表。";
        }
        if ((latestJob != null && "SUCCEEDED".equals(latestJob.status()) && !downstreamReady)
                || (analyzedReviewCount > 0 && !downstreamReady)) {
            return "LLM 任务已结束，但问题、趋势、词云和卖点依赖的物化数据没有写入，请重新导入 cleaned JSONL 后再启动分析。";
        }
        if (downstreamReady) {
            return "LLM 分析结果已入库，下游问题、趋势、词云和卖点可读取。";
        }
        if (importedReviewCount > 0) {
            return "清洗评论已导入数据库，可以启动 LLM 分析。";
        }
        if (taxonomyBound) {
            return "taxonomy 已绑定，请导入 cleaned JSONL。";
        }
        if (cleanedExists) {
            return "cleaned JSONL 已存在，请绑定 taxonomy。";
        }
        return "请先选择 raw JSONL 并执行清洗。";
    }

    private int intValue(Map<String, Object> summary, String key, int fallback) {
        Object value = summary.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String safeFilename(String value) {
        String safe = (value == null || value.isBlank() ? "product" : value.trim())
                .replaceAll("[^a-zA-Z0-9_.-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "product" : safe;
    }

    private String displayPath(Path normalizedPath) {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path display = normalizedPath;
        if (normalizedPath.startsWith(root)) {
            display = root.relativize(normalizedPath);
        }
        return display.toString().replace('\\', '/');
    }

    private record JsonlArtifactPaths(
            Path rawOutputPath,
            Path cleanedOutputPath,
            Path removedOutputPath,
            Path cleaningSummaryPath
    ) {
    }
}
