package com.wh.reputation.review;

import java.util.List;

public record ReviewDetailDto(
        Long id,
        String platformName,
        String productName,
        Integer rating,
        String reviewTime,
        String contentRaw,
        String contentClean,
        String overallSentiment,
        double overallScore,
        List<ReviewAspectResultDto> aspectResults
) {}

