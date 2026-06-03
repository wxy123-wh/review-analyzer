package com.wh.reputation.dashboard;

import com.wh.reputation.decision.PriorityItemDto;

import java.util.List;

public record DashboardOverviewDto(
        long reviewCount,
        double posRate,
        double neuRate,
        double negRate,
        List<DashboardTrendPointDto> trend,
        List<PriorityItemDto> topPriorities
) {}

