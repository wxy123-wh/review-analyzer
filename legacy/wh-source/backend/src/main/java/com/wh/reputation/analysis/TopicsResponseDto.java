package com.wh.reputation.analysis;

import java.util.List;

public record TopicsResponseDto(int topicCount, List<TopicItemDto> items) {}

