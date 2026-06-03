package com.wh.reputation.analytics;

public record SentimentTrendPointDto(
        String date,
        long pos,
        long neg,
        long neu,
        long total,
        double posRate,
        double negRate
) {}

