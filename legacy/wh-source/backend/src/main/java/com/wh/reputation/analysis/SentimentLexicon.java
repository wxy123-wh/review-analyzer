package com.wh.reputation.analysis;

import java.util.List;

public record SentimentLexicon(List<String> pos, List<String> neg, List<String> negation) {}

