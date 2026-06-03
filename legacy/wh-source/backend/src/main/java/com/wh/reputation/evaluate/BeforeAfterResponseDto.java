package com.wh.reputation.evaluate;

import java.util.List;

public record BeforeAfterResponseDto(
        BeforeAfterEventDto event,
        BeforeAfterWindowDto before,
        BeforeAfterWindowDto after,
        List<KeywordChangeDto> keywordChanges
) {}

