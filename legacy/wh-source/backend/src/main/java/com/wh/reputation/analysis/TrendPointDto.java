package com.wh.reputation.analysis;

public record TrendPointDto(String date, long count, long pos, long neu, long neg, double negRate) {}

