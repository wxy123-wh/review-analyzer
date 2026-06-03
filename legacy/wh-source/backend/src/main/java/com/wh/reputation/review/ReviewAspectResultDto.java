package com.wh.reputation.review;

import java.util.List;

public record ReviewAspectResultDto(
        Long aspectId,
        String aspectName,
        List<String> hitKeywords,
        String sentiment,
        double score,
        double confidence
) {}

