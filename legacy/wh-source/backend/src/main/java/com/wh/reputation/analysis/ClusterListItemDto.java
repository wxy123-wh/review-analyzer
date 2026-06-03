package com.wh.reputation.analysis;

import java.util.List;

public record ClusterListItemDto(
        Long id,
        List<String> topTerms,
        int size,
        double negRate,
        List<Long> representativeReviewIds
) {}

