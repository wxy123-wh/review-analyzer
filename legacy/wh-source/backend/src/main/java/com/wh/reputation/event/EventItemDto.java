package com.wh.reputation.event;

public record EventItemDto(
        Long id,
        String name,
        String type,
        String startDate,
        String endDate
) {}

