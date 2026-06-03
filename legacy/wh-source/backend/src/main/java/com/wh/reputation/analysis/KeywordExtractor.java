package com.wh.reputation.analysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class KeywordExtractor {
    private KeywordExtractor() {}

    static Map<String, Integer> extractCounts(String content, List<String> dictionary, Set<String> stopwords) {
        Map<String, Integer> counts = new HashMap<>();
        if (content == null || content.isBlank() || dictionary.isEmpty()) {
            return counts;
        }
        for (String term : dictionary) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (stopwords != null && stopwords.contains(term)) {
                continue;
            }
            int c = countOccurrences(content, term);
            if (c > 0) {
                counts.put(term, c);
            }
        }
        return counts;
    }

    private static int countOccurrences(String content, String term) {
        int count = 0;
        int index = 0;
        while (true) {
            index = content.indexOf(term, index);
            if (index < 0) {
                break;
            }
            count++;
            index = index + Math.max(1, term.length());
        }
        return count;
    }
}

