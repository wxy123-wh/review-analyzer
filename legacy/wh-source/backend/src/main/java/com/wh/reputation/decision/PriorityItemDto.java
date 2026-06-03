package com.wh.reputation.decision;

public record PriorityItemDto(
        String level,
        Long aspectId,
        String name,
        double priority,
        double negRate,
        double growth,
        long volume
) {}

