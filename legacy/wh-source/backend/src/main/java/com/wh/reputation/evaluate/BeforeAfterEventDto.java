package com.wh.reputation.evaluate;

public record BeforeAfterEventDto(
        Long id,
        String name,
        String type,
        String startDate,
        String endDate
) {}

