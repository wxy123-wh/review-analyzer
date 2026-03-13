package com.wh.review.backend.service;

import com.wh.review.backend.dto.ActionResponse;
import com.wh.review.backend.dto.CompareItem;
import com.wh.review.backend.dto.CompareResponse;
import com.wh.review.backend.dto.IssueItem;
import com.wh.review.backend.dto.TrendPoint;
import com.wh.review.backend.dto.TrendResponse;
import com.wh.review.backend.dto.ValidationItem;
import com.wh.review.backend.dto.ValidationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InsightQueryService {

    private static final double W_NEGATIVE_RATE = 0.35;
    private static final double W_MENTION_VOLUME = 0.25;
    private static final double W_TREND_GROWTH = 0.20;
    private static final double W_COMPETITOR_GAP = 0.20;

    private final ActionService actionService;

    public InsightQueryService(ActionService actionService) {
        this.actionService = actionService;
    }

    public List<IssueItem> listIssues(String productCode) {
        List<IssueFactors> factors = seedIssueFactors(productCode);
        List<IssueItem> items = new ArrayList<>();
        for (IssueFactors issue : factors) {
            double priority = roundTo4(
                    W_NEGATIVE_RATE * issue.negativeRate()
                            + W_MENTION_VOLUME * issue.mentionVolume()
                            + W_TREND_GROWTH * issue.trendGrowth()
                            + W_COMPETITOR_GAP * issue.competitorGap()
            );
            items.add(new IssueItem(
                    issue.issueId(),
                    issue.title(),
                    issue.aspect(),
                    priority,
                    issue.evidenceSummary()
            ));
        }
        return items;
    }

    public CompareResponse compare(String productCode) {
        List<CompareItem> items = List.of(
                new CompareItem("audio", 0.82, 0.78, roundTo4(0.82 - 0.78)),
                new CompareItem("noise_canceling", 0.76, 0.81, roundTo4(0.76 - 0.81)),
                new CompareItem("battery", 0.71, 0.84, roundTo4(0.71 - 0.84)),
                new CompareItem("connectivity", 0.64, 0.80, roundTo4(0.64 - 0.80))
        );
        return new CompareResponse(productCode, items);
    }

    public TrendResponse trends(String productCode, String aspect) {
        String normalizedAspect = aspect == null || aspect.isBlank() ? "battery" : aspect.toLowerCase(Locale.ROOT);
        List<TrendPoint> points;
        switch (normalizedAspect) {
            case "connectivity" -> points = List.of(
                    new TrendPoint("2026-W06", 0.36, 82),
                    new TrendPoint("2026-W07", 0.39, 95),
                    new TrendPoint("2026-W08", 0.42, 103),
                    new TrendPoint("2026-W09", 0.45, 118)
            );
            case "noise_canceling" -> points = List.of(
                    new TrendPoint("2026-W06", 0.27, 65),
                    new TrendPoint("2026-W07", 0.29, 73),
                    new TrendPoint("2026-W08", 0.30, 79),
                    new TrendPoint("2026-W09", 0.32, 84)
            );
            default -> points = List.of(
                    new TrendPoint("2026-W06", 0.31, 75),
                    new TrendPoint("2026-W07", 0.34, 81),
                    new TrendPoint("2026-W08", 0.37, 92),
                    new TrendPoint("2026-W09", 0.40, 105)
            );
        }
        return new TrendResponse(productCode, normalizedAspect, points);
    }

    public ValidationResponse validation(String actionId) {
        if (actionId != null && !actionId.isBlank()) {
            return actionService.findById(actionId)
                    .map(this::buildValidationFromAction)
                    .map(item -> new ValidationResponse(List.of(item)))
                    .orElseGet(() -> new ValidationResponse(List.of()));
        }

        List<ValidationItem> items = actionService.listAll().stream()
                .map(this::buildValidationFromAction)
                .toList();
        return new ValidationResponse(items);
    }

    private ValidationItem buildValidationFromAction(ActionResponse action) {
        double beforeNegativeRate = 0.42;
        double afterNegativeRate = 0.31;
        double improvementRate = roundTo4(beforeNegativeRate - afterNegativeRate);
        return new ValidationItem(
                action.actionId(),
                beforeNegativeRate,
                afterNegativeRate,
                improvementRate,
                "上线后负面率下降 " + roundTo2(improvementRate * 100) + "%，问题热度趋稳。"
        );
    }

    private List<IssueFactors> seedIssueFactors(String productCode) {
        return List.of(
                new IssueFactors(
                        "iss-connectivity-001",
                        "连接稳定性偶发断连",
                        "connectivity",
                        0.45,
                        0.84,
                        0.39,
                        0.41,
                        "近30天断连/重连相关评论占比上升，且与竞品差距扩大。"
                ),
                new IssueFactors(
                        "iss-battery-001",
                        "续航衰减快",
                        "battery",
                        0.38,
                        0.76,
                        0.31,
                        0.37,
                        "高频负面集中在通勤场景，连续两周负面率提升。"
                ),
                new IssueFactors(
                        "iss-call-001",
                        "通话收音噪声偏大",
                        "call_quality",
                        0.29,
                        0.57,
                        0.22,
                        0.34,
                        "地铁场景中“对方听不清”反馈显著高于同价位竞品。"
                )
        );
    }

    private double roundTo4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private double roundTo2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record IssueFactors(
            String issueId,
            String title,
            String aspect,
            double negativeRate,
            double mentionVolume,
            double trendGrowth,
            double competitorGap,
            String evidenceSummary
    ) {
    }
}
