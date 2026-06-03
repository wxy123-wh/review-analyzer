package com.wh.reputation.search;

import com.wh.reputation.ai.OpenAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient restClient;
    private final OpenAiConfig openAiConfig;

    public EmbeddingService(RestClient.Builder restClientBuilder, OpenAiConfig openAiConfig) {
        this.openAiConfig = openAiConfig;
        this.restClient = restClientBuilder
                .baseUrl(openAiConfig.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openAiConfig.getKey())
                .build();
    }

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[1536]; // Return zero vector or handle appropriately
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest("text-embedding-3-small", text);
            EmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response != null && response.data() != null && !response.data().isEmpty()) {
                return response.data().get(0).embedding();
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", text, e);
        }
        return new float[1536]; // Fallback
    }

    // Inner DTOs
    record EmbeddingRequest(String model, String input) {
    }

    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    record EmbeddingData(float[] embedding) {
    }
}
