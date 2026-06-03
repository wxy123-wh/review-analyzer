package com.wh.reputation.review;

import java.util.List;

public record ReviewListItemDto(
        Long id,
        String platformName,
        String productName,
        Integer rating,
        String reviewTime,
        String contentClean,
        String overallSentiment,
        double overallScore,
        List<ReviewListAspectDto> aspects
) {}

