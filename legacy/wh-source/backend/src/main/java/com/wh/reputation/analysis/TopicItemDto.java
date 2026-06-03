package com.wh.reputation.analysis;

import java.util.List;

public record TopicItemDto(
        int topicId,
        List<String> topWords,
        double weight,
        List<Long> evidenceReviewIds
) {}

