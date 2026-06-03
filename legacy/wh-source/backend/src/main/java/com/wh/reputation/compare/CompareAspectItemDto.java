package com.wh.reputation.compare;

public record CompareAspectItemDto(
        Long aspectId,
        String aspectName,
        CompareRateDto self,
        CompareRateDto competitor,
        CompareRateDto diff,
        CompareNormalizedDto normalized
) {}

