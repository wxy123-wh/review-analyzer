package com.wh.reputation.decision;

import java.util.List;

public record SuggestionItemDto(
        Long id,
        String refType,
        Long refId,
        String suggestionText,
        List<SuggestionEvidenceDto> evidence
) {}

