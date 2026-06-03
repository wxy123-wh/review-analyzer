package com.wh.reputation.analytics;

import java.util.List;

public record WordcloudResponseDto(List<WordcloudItemDto> items, WordcloudMetaDto meta) {}

