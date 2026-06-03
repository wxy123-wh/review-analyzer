package com.wh.reputation.analysis;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TokenizationService {
    private static final Pattern VALID_TOKEN = Pattern.compile("^[\\p{IsHan}A-Za-z0-9]+$");

    private final Stopwords stopwords;
    private final JiebaSegmenter segmenter;

    public TokenizationService(Stopwords stopwords) {
        this.stopwords = stopwords;
        this.segmenter = new JiebaSegmenter();
    }

    public List<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> rawTokens = segmenter.sentenceProcess(content);
        if (rawTokens == null || rawTokens.isEmpty()) {
            return List.of();
        }

        Set<String> stopwordSet = stopwords == null ? Set.of() : stopwords.words();
        List<String> tokens = new ArrayList<>(rawTokens.size());
        for (String token : rawTokens) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String normalized = normalize(trimmed);
            if (normalized.isBlank()) {
                continue;
            }
            if (stopwordSet.contains(normalized)) {
                continue;
            }
            tokens.add(normalized);
        }
        return tokens;
    }

    private static String normalize(String token) {
        if (!VALID_TOKEN.matcher(token).matches()) {
            return "";
        }
        if (token.length() == 1) {
            int cp = token.codePointAt(0);
            if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.HAN) {
                return "";
            }
        }
        return token.toLowerCase(Locale.ROOT);
    }
}

