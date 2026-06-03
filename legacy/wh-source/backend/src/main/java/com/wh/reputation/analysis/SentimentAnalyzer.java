package com.wh.reputation.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SentimentAnalyzer {
    private final SentimentLexicon lexicon;

    public SentimentAnalyzer(SentimentLexicon lexicon) {
        this.lexicon = lexicon;
    }

    public SentimentResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new SentimentResult("NEU", 0.0, 0, 0);
        }

        List<Occurrence> posOccurrences = findOccurrences(text, lexicon.pos());
        List<Occurrence> negOccurrences = findOccurrences(text, lexicon.neg());
        List<Occurrence> negationOccurrences = findOccurrences(text, lexicon.negation());

        int negatedPosCount = 0;
        for (Occurrence posOccurrence : posOccurrences) {
            if (isNegated(posOccurrence, negationOccurrences)) {
                negatedPosCount++;
            }
        }

        int posCount = Math.max(0, posOccurrences.size() - negatedPosCount);
        int negCount = Math.max(0, negOccurrences.size() + negatedPosCount);

        String label;
        if (negCount - posCount >= 1) {
            label = "NEG";
        } else if (posCount - negCount >= 1) {
            label = "POS";
        } else {
            label = "NEU";
        }

        double score = (double) (posCount - negCount) / (posCount + negCount + 1);
        return new SentimentResult(label, score, posCount, negCount);
    }

    private static boolean isNegated(Occurrence posOccurrence, List<Occurrence> negations) {
        for (Occurrence negation : negations) {
            if (negation.index() > posOccurrence.index()) {
                continue;
            }
            int gap = posOccurrence.index() - (negation.index() + negation.length());
            if (gap >= 0 && gap <= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<Occurrence> findOccurrences(String text, List<String> terms) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            int index = 0;
            while (true) {
                index = text.indexOf(term, index);
                if (index < 0) {
                    break;
                }
                occurrences.add(new Occurrence(index, term.length()));
                index = index + Math.max(1, term.length());
            }
        }
        occurrences.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return occurrences;
    }

    private record Occurrence(int index, int length) {
        private Occurrence {
            Objects.checkIndex(index, Integer.MAX_VALUE);
        }
    }
}

