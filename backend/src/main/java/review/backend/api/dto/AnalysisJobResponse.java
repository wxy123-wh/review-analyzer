package review.backend.api.dto;

import java.time.Instant;

public record AnalysisJobResponse(
        String jobId,
        String productCode,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        int totalReviewCount,
        int processedReviewCount,
        int progressPercent,
        String currentStage,
        int materializedReviewCount,
        int semanticLabelCount,
        int issueClusterCount,
        boolean downstreamReady
) {
    public AnalysisJobResponse(
            String jobId,
            String productCode,
            String status,
            Instant startedAt,
            Instant finishedAt,
            String errorMessage
    ) {
        this(
                jobId,
                productCode,
                status,
                startedAt,
                finishedAt,
                errorMessage,
                0,
                0,
                defaultProgressPercent(status),
                defaultCurrentStage(status),
                0,
                0,
                0,
                false
        );
    }

    private static int defaultProgressPercent(String status) {
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
            return 100;
        }
        if ("RUNNING".equals(status)) {
            return 10;
        }
        if ("QUEUED".equals(status)) {
            return 0;
        }
        return 0;
    }

    private static String defaultCurrentStage(String status) {
        if ("SUCCEEDED".equals(status)) {
            return "分析完成";
        }
        if ("FAILED".equals(status)) {
            return "分析失败";
        }
        if ("RUNNING".equals(status)) {
            return "LLM 分析中";
        }
        return "等待开始";
    }
}
