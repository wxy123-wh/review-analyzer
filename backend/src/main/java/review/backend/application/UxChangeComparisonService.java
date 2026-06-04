package review.backend.application;

import review.backend.api.dto.UxChangeComparisonHistoryResponse;
import review.backend.api.dto.UxChangeComparisonItem;
import review.backend.api.dto.UxChangeComparisonRecord;
import review.backend.api.dto.UxChangeComparisonRequest;
import review.backend.api.dto.UxChangeComparisonResponse;
import review.backend.api.dto.UxChangeComparisonWindow;
import review.backend.data.AnalysisMaterializationRepository;
import review.backend.data.AnalysisMaterializationRepository.MaterializedUxChangeReviewRecord;
import review.backend.data.ProductRepository;
import review.backend.data.UxChangeComparisonRepository;
import review.backend.data.UxChangeComparisonRepository.UxChangeCheckpoint;
import review.backend.util.MathUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UxChangeComparisonService {

    private static final ZoneId WINDOW_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MIN_SAMPLE_PER_WINDOW = 5;
    private static final String STATE_SUCCESS = "success";
    private static final String STATE_EMPTY = "empty";
    private static final String STATE_ERROR = "error";
    private static final String DEFAULT_WINDOW_PRESET = "ONE_MONTH";
    private static final String CUSTOM_WINDOW_PRESET = "CUSTOM";
    private static final int DEFAULT_CUSTOM_WINDOW_DAYS = 30;
    private static final int MAX_CUSTOM_WINDOW_DAYS = 365;
    private static final String NO_DATA_NOTICE = "当前商品暂无已分析评论，请先导入真实评论并启动分析。";

    private final UxChangeComparisonRepository checkpointRepository;
    private final AnalysisMaterializationRepository materializationRepository;
    private final ProductRepository productRepository;
    private final ReviewAggregationService reviewAggregationService;

    public UxChangeComparisonService(
            UxChangeComparisonRepository checkpointRepository,
            AnalysisMaterializationRepository materializationRepository,
            ProductRepository productRepository,
            ReviewAggregationService reviewAggregationService
    ) {
        this.checkpointRepository = checkpointRepository;
        this.materializationRepository = materializationRepository;
        this.productRepository = productRepository;
        this.reviewAggregationService = reviewAggregationService;
    }

    public UxChangeComparisonResponse createAndCompare(UxChangeComparisonRequest request) {
        String productCode = reviewAggregationService.normalizeProductCode(request == null ? null : request.productCode());
        WindowSpec window = resolveWindow(request);
        UxChangeCheckpoint checkpoint = checkpointRepository.save(
                productCode,
                window.changeDate(),
                window.windowPreset(),
                window.beforeStart(),
                window.beforeEnd(),
                window.afterStart(),
                window.afterEnd()
        );
        return compare(checkpoint);
    }

    public UxChangeComparisonHistoryResponse list(String productCode) {
        String normalizedProductCode = reviewAggregationService.normalizeProductCode(productCode);
        List<UxChangeComparisonRecord> records = checkpointRepository.findByProductCode(normalizedProductCode).stream()
                .map(this::toRecord)
                .toList();
        if (records.isEmpty()) {
            return new UxChangeComparisonHistoryResponse(
                    normalizedProductCode,
                    resolveProductName(normalizedProductCode),
                    STATE_EMPTY,
                    "当前暂无前后对比时间点，请先选择一个时间点生成对比。",
                    List.of()
            );
        }
        return new UxChangeComparisonHistoryResponse(
                normalizedProductCode,
                resolveProductName(normalizedProductCode),
                STATE_SUCCESS,
                null,
                records
        );
    }

    public UxChangeComparisonResponse findAndCompare(String checkpointId) {
        Long parsedId = parseId(checkpointId);
        if (parsedId == null) {
            return emptyError(null, "时间点记录不存在。");
        }
        return checkpointRepository.findById(parsedId)
                .map(this::compare)
                .orElseGet(() -> emptyError(String.valueOf(parsedId), "时间点记录不存在。"));
    }

    private UxChangeComparisonResponse compare(UxChangeCheckpoint checkpoint) {
        if (!materializationRepository.hasMaterializedOutputs(checkpoint.productCode())) {
            return response(checkpoint, STATE_EMPTY, NO_DATA_NOTICE, List.of());
        }

        Instant beforeStart = toStartInstant(checkpoint.beforeStart());
        Instant beforeEnd = toStartInstant(checkpoint.beforeEnd());
        Instant afterStart = toStartInstant(checkpoint.afterStart());
        Instant afterEnd = toStartInstant(checkpoint.afterEnd());
        List<MaterializedUxChangeReviewRecord> reviews = materializationRepository.findUxChangeReviews(
                checkpoint.productCode(),
                beforeStart,
                afterEnd
        );

        Map<String, LabelStats> grouped = new LinkedHashMap<>();
        for (MaterializedUxChangeReviewRecord review : reviews) {
            String label = labelOrFallback(review.uxSecondaryLabel());
            if (TaxonomyService.FALLBACK_SECONDARY_LABEL.equals(label)) {
                continue;
            }
            LabelStats stats = grouped.computeIfAbsent(
                    label,
                    ignored -> new LabelStats(review.aspect(), review.uxPrimaryLabel(), label)
            );
            if (!review.reviewTime().isBefore(beforeStart) && review.reviewTime().isBefore(beforeEnd)) {
                stats.beforeMentionCount++;
                if (isNegative(review.sentimentPolarity())) {
                    stats.beforeNegativeCount++;
                }
            } else if (!review.reviewTime().isBefore(afterStart) && review.reviewTime().isBefore(afterEnd)) {
                stats.afterMentionCount++;
                if (isNegative(review.sentimentPolarity())) {
                    stats.afterNegativeCount++;
                }
            }
        }

        List<UxChangeComparisonItem> items = grouped.values().stream()
                .map(this::toItem)
                .sorted(Comparator
                        .comparingDouble((UxChangeComparisonItem item) -> Math.abs(item.negativeRateChange()))
                        .reversed()
                        .thenComparing(UxChangeComparisonItem::uxSecondaryLabel))
                .toList();
        if (items.isEmpty()) {
            return response(checkpoint, STATE_EMPTY, "当前时间窗口内暂无可对比的 UX 标签评论。", List.of());
        }
        return response(checkpoint, STATE_SUCCESS, null, items);
    }

    private UxChangeComparisonItem toItem(LabelStats stats) {
        double beforeNegativeRate = stats.beforeMentionCount == 0
                ? 0D
                : roundTo4((double) stats.beforeNegativeCount / stats.beforeMentionCount);
        double afterNegativeRate = stats.afterMentionCount == 0
                ? 0D
                : roundTo4((double) stats.afterNegativeCount / stats.afterMentionCount);
        double improvementRate = roundTo4(beforeNegativeRate - afterNegativeRate);
        double negativeRateChange = roundTo4(afterNegativeRate - beforeNegativeRate);
        int mentionCountChange = stats.afterMentionCount - stats.beforeMentionCount;
        String sampleState = stats.beforeMentionCount < MIN_SAMPLE_PER_WINDOW || stats.afterMentionCount < MIN_SAMPLE_PER_WINDOW
                ? "insufficient"
                : "ok";
        String direction = direction(improvementRate, sampleState);
        return new UxChangeComparisonItem(
                stats.aspect,
                labelOrFallback(stats.uxPrimaryLabel),
                stats.uxSecondaryLabel,
                stats.beforeMentionCount,
                stats.beforeNegativeCount,
                beforeNegativeRate,
                stats.afterMentionCount,
                stats.afterNegativeCount,
                afterNegativeRate,
                improvementRate,
                negativeRateChange,
                mentionCountChange,
                direction,
                sampleState,
                summary(stats.uxSecondaryLabel, beforeNegativeRate, afterNegativeRate, improvementRate, sampleState)
        );
    }

    private String direction(double improvementRate, String sampleState) {
        if ("insufficient".equals(sampleState)) {
            return "INSUFFICIENT_DATA";
        }
        if (improvementRate > 0.005D) {
            return "IMPROVED";
        }
        if (improvementRate < -0.005D) {
            return "WORSENED";
        }
        return "STABLE";
    }

    private String summary(
            String uxSecondaryLabel,
            double beforeNegativeRate,
            double afterNegativeRate,
            double improvementRate,
            String sampleState
    ) {
        if ("insufficient".equals(sampleState)) {
            return uxSecondaryLabel + "前后窗口样本量不足，暂不建议直接下结论。";
        }
        double delta = MathUtils.roundTo2(Math.abs(improvementRate) * 100D);
        if (improvementRate > 0.005D) {
            return uxSecondaryLabel + "负面率下降 " + delta + "%，体验反馈改善。";
        }
        if (improvementRate < -0.005D) {
            return uxSecondaryLabel + "负面率上升 " + delta + "%，需要继续关注。";
        }
        return uxSecondaryLabel + "负面率基本持平（"
                + MathUtils.roundTo2(beforeNegativeRate * 100D)
                + "% -> "
                + MathUtils.roundTo2(afterNegativeRate * 100D)
                + "%）。";
    }

    private UxChangeComparisonResponse response(
            UxChangeCheckpoint checkpoint,
            String state,
            String notice,
            List<UxChangeComparisonItem> items
    ) {
        return new UxChangeComparisonResponse(
                String.valueOf(checkpoint.checkpointId()),
                checkpoint.productCode(),
                resolveProductName(checkpoint.productCode()),
                checkpoint.changeDate(),
                checkpoint.windowPreset(),
                new UxChangeComparisonWindow(checkpoint.beforeStart(), checkpoint.beforeEnd()),
                new UxChangeComparisonWindow(checkpoint.afterStart(), checkpoint.afterEnd()),
                state,
                notice,
                items
        );
    }

    private UxChangeComparisonResponse emptyError(String checkpointId, String notice) {
        return new UxChangeComparisonResponse(
                checkpointId,
                "",
                null,
                null,
                DEFAULT_WINDOW_PRESET,
                null,
                null,
                STATE_ERROR,
                notice,
                List.of()
        );
    }

    private UxChangeComparisonRecord toRecord(UxChangeCheckpoint checkpoint) {
        return new UxChangeComparisonRecord(
                String.valueOf(checkpoint.checkpointId()),
                checkpoint.productCode(),
                resolveProductName(checkpoint.productCode()),
                checkpoint.changeDate(),
                checkpoint.windowPreset(),
                new UxChangeComparisonWindow(checkpoint.beforeStart(), checkpoint.beforeEnd()),
                new UxChangeComparisonWindow(checkpoint.afterStart(), checkpoint.afterEnd()),
                checkpoint.createdAt()
        );
    }

    private WindowSpec resolveWindow(UxChangeComparisonRequest request) {
        LocalDate changeDate = request == null || request.changeDate() == null
                ? LocalDate.now(WINDOW_ZONE)
                : request.changeDate();
        String preset = normalizeWindowPreset(request == null ? null : request.windowPreset());
        if (CUSTOM_WINDOW_PRESET.equals(preset) && hasCustomDayWindow(request)) {
            int beforeDays = clampCustomDays(request.customBeforeDays());
            int afterDays = clampCustomDays(request.customAfterDays());
            return new WindowSpec(
                    changeDate,
                    preset,
                    changeDate.minusDays(beforeDays),
                    changeDate,
                    changeDate,
                    changeDate.plusDays(afterDays)
            );
        }
        if (CUSTOM_WINDOW_PRESET.equals(preset) && hasCustomDateWindow(request)) {
            validateWindow(request.beforeStart(), request.beforeEnd(), "before");
            validateWindow(request.afterStart(), request.afterEnd(), "after");
            return new WindowSpec(
                    changeDate,
                    preset,
                    request.beforeStart(),
                    request.beforeEnd(),
                    request.afterStart(),
                    request.afterEnd()
            );
        }
        if ("ONE_MONTH".equals(preset)) {
            return new WindowSpec(changeDate, preset, changeDate.minusMonths(1), changeDate, changeDate, changeDate.plusMonths(1));
        }
        if ("THREE_MONTHS".equals(preset)) {
            return new WindowSpec(changeDate, preset, changeDate.minusMonths(3), changeDate, changeDate, changeDate.plusMonths(3));
        }
        int days = "TWO_WEEKS".equals(preset) ? 14 : DEFAULT_CUSTOM_WINDOW_DAYS;
        return new WindowSpec(changeDate, preset, changeDate.minusDays(days), changeDate, changeDate, changeDate.plusDays(days));
    }

    private String normalizeWindowPreset(String windowPreset) {
        if (windowPreset == null || windowPreset.isBlank()) {
            return DEFAULT_WINDOW_PRESET;
        }
        String normalized = windowPreset.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ONE_MONTH", "P1M", "P30D" -> "ONE_MONTH";
            case "TWO_WEEKS", "P14D" -> "TWO_WEEKS";
            case "THREE_MONTHS", "P90D" -> "THREE_MONTHS";
            case CUSTOM_WINDOW_PRESET -> CUSTOM_WINDOW_PRESET;
            default -> DEFAULT_WINDOW_PRESET;
        };
    }

    private boolean hasCustomDayWindow(UxChangeComparisonRequest request) {
        return request != null && (request.customBeforeDays() != null || request.customAfterDays() != null);
    }

    private boolean hasCustomDateWindow(UxChangeComparisonRequest request) {
        return request != null
                && request.beforeStart() != null
                && request.beforeEnd() != null
                && request.afterStart() != null
                && request.afterEnd() != null;
    }

    private int clampCustomDays(Integer value) {
        if (value == null) {
            return DEFAULT_CUSTOM_WINDOW_DAYS;
        }
        return Math.max(1, Math.min(MAX_CUSTOM_WINDOW_DAYS, value));
    }

    private void validateWindow(LocalDate start, LocalDate end, String name) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(name + " window start must be before end");
        }
    }

    private Instant toStartInstant(LocalDate date) {
        return date.atStartOfDay(WINDOW_ZONE).toInstant();
    }

    private boolean isNegative(String sentimentPolarity) {
        return "NEGATIVE".equalsIgnoreCase(sentimentPolarity);
    }

    private double roundTo4(double value) {
        return MathUtils.roundTo4(value);
    }

    private String labelOrFallback(String label) {
        if (label == null || label.isBlank()) {
            return TaxonomyService.FALLBACK_SECONDARY_LABEL;
        }
        return label.trim();
    }

    private String resolveProductName(String productCode) {
        try {
            java.util.Optional<String> productName = productRepository.findProductName(productCode);
            return productName == null
                    ? null
                    : productName.filter(name -> !productCode.equals(name)).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long parseId(String checkpointId) {
        if (checkpointId == null || checkpointId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(checkpointId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record WindowSpec(
            LocalDate changeDate,
            String windowPreset,
            LocalDate beforeStart,
            LocalDate beforeEnd,
            LocalDate afterStart,
            LocalDate afterEnd
    ) {
    }

    private static final class LabelStats {
        private final String aspect;
        private final String uxPrimaryLabel;
        private final String uxSecondaryLabel;
        private int beforeMentionCount;
        private int beforeNegativeCount;
        private int afterMentionCount;
        private int afterNegativeCount;

        private LabelStats(String aspect, String uxPrimaryLabel, String uxSecondaryLabel) {
            this.aspect = aspect;
            this.uxPrimaryLabel = uxPrimaryLabel;
            this.uxSecondaryLabel = uxSecondaryLabel;
        }
    }
}
