package com.wh.reputation.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OpenAiClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private final RestClient restClient;

    public OpenAiClient(RestClient openAiRestClient) {
        this.restClient = openAiRestClient;
    }

    public OpenAiChatResponse chatCompletion(OpenAiChatRequest request) {
        try {
            log.info("Calling OpenAI API with model: {}", request.model());
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null) {
                throw new IllegalStateException("OpenAI API returned null response");
            }

            log.info("OpenAI API call successful. Usage: {} tokens",
                    response.usage() != null ? response.usage().totalTokens() : "unknown");
            return response;
        } catch (RestClientException e) {
            log.error("Failed to call OpenAI API", e);
            throw new IllegalStateException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }
}
