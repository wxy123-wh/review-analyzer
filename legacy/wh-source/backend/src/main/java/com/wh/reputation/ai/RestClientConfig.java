package com.wh.reputation.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient openAiRestClient(OpenAiConfig config) {
        return RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
