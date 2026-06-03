package com.wh.reputation.evaluate;

import java.util.List;

public record BeforeAfterWindowDto(
        long reviewCount,
        double negRate,
        List<BeforeAfterAspectDto> aspects
) {}

