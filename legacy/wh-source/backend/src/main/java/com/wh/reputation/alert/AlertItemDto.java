package com.wh.reputation.alert;

public record AlertItemDto(
        Long id,
        String metric,
        Long aspectId,
        String windowStart,
        String windowEnd,
        double currentValue,
        double prevValue,
        double threshold,
        String status,
        String createdAt
) {}

