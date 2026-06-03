package com.wh.reputation.analysis;

public record AspectAnalysisItemDto(
        Long aspectId,
        String aspectName,
        long volume,
        double posRate,
        double neuRate,
        double negRate
) {}

