package com.wh.reputation.event;

public record CreateEventRequest(
        Long productId,
        String name,
        String type,
        String startDate,
        String endDate
) {}

