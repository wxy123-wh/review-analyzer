package com.wh.reputation.analytics;

import java.util.List;

public record SentimentTrendResponseDto(List<SentimentTrendPointDto> points, SentimentTrendMetaDto meta) {}

