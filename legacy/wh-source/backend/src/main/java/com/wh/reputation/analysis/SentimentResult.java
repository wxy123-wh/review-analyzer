package com.wh.reputation.analysis;

public record SentimentResult(String label, double score, int posCount, int negCount) {}

