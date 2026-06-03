package com.wh.reputation.analysis;

import java.util.List;

public record ClusterDetailResponseDto(
        Long id,
        List<String> topTerms,
        int size,
        double negRate,
        List<ClusterRepresentativeReviewDto> representativeReviews
) {}

