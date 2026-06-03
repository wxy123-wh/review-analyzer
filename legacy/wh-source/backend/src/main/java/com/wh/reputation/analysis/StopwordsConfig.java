package com.wh.reputation.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class StopwordsConfig {
    @Bean
    public Stopwords stopwords() {
        var path = DataFileLocator.resolveRequired("stopwords.txt");
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            Set<String> words = lines
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            return new Stopwords(words);
        } catch (IOException e) {
            throw new IllegalStateException("加载停用词失败：" + path, e);
        }
    }
}
