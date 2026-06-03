package com.wh.reputation.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;

@Configuration
public class SentimentLexiconConfig {
    @Bean
    public SentimentLexicon sentimentLexicon(ObjectMapper objectMapper) {
        var path = DataFileLocator.resolveRequired("sentiment_lexicon.json");
        try (var in = Files.newInputStream(path)) {
            return objectMapper.readValue(in, SentimentLexicon.class);
        } catch (IOException e) {
            throw new IllegalStateException("加载情感词典失败：" + path, e);
        }
    }
}
