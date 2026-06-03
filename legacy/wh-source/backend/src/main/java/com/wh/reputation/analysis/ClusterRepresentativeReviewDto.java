package com.wh.reputation.analysis;

public record ClusterRepresentativeReviewDto(
        Long id,
        String reviewTime,
        String contentClean,
        String overallSentiment
) {}

