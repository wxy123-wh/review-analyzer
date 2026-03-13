package com.wh.review.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OneBoundReviewClient {

    private static final List<String> REVIEW_ARRAY_KEYS = List.of("items", "comments", "rateList", "list");

    private final RestClient restClient;
    private final OneBoundProperties oneBoundProperties;

    public OneBoundReviewClient(RestClient.Builder restClientBuilder, OneBoundProperties oneBoundProperties) {
        this.oneBoundProperties = oneBoundProperties;
        this.restClient = restClientBuilder
                .baseUrl(oneBoundProperties.getBaseUrl())
                .build();
    }

    public int fetchFirstPageReviewCount(String platform, String numIid) {
        String apiKey = oneBoundProperties.getApiKey();
        String apiSecret = oneBoundProperties.getEffectiveSecret();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ONEBOUND_API_KEY is not configured");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("ONEBOUND_API_SECRET is not configured");
        }

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(platform, "item_review")
                        .queryParam("key", apiKey)
                        .queryParam("secret", apiSecret)
                        .queryParam("num_iid", numIid)
                        .queryParam("page", 1)
                        .queryParam("cache", "no")
                        .queryParam("result_type", "json")
                        .queryParam("version", 1)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return 0;
        }

        if (hasError(response)) {
            throw new IllegalStateException(extractErrorMessage(response));
        }

        return extractReviewCount(response);
    }

    private boolean hasError(JsonNode node) {
        JsonNode error = node.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            if (error.isTextual() && !error.asText().isBlank()) {
                return true;
            }
            if (error.isObject() && error.size() > 0) {
                return true;
            }
        }
        JsonNode code = node.path("error_code");
        return !code.isMissingNode() && !code.asText("").isBlank();
    }

    private String extractErrorMessage(JsonNode node) {
        JsonNode error = node.path("error");
        if (error.isTextual() && !error.asText().isBlank()) {
            return error.asText();
        }
        if (error.isObject()) {
            JsonNode message = error.path("message");
            if (!message.isMissingNode() && !message.asText("").isBlank()) {
                return message.asText();
            }
        }
        JsonNode errorCode = node.path("error_code");
        if (!errorCode.isMissingNode() && !errorCode.asText("").isBlank()) {
            return "onebound error_code=" + errorCode.asText();
        }
        return "onebound request failed";
    }

    private int extractReviewCount(JsonNode node) {
        for (String key : REVIEW_ARRAY_KEYS) {
            JsonNode direct = node.path(key);
            if (direct.isArray()) {
                return direct.size();
            }
        }

        JsonNode data = node.path("data");
        if (data.isObject()) {
            for (String key : REVIEW_ARRAY_KEYS) {
                JsonNode dataArray = data.path(key);
                if (dataArray.isArray()) {
                    return dataArray.size();
                }
            }
        }

        JsonNode item = node.path("item");
        if (item.isObject()) {
            for (String key : REVIEW_ARRAY_KEYS) {
                JsonNode itemArray = item.path(key);
                if (itemArray.isArray()) {
                    return itemArray.size();
                }
            }
        }

        return 0;
    }
}
