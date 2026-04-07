package com.wh.review.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wh.review.backend.persistence.ExternalReviewRawRepository.ExternalRawReview;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    public FetchedReviewPage fetchFirstPage(String platform, String numIid) {
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
            return new FetchedReviewPage(List.of(), "{\"provider\":\"onebound\",\"platform\":\"%s\",\"page\":1,\"responseState\":\"empty\"}"
                    .formatted(platform));
        }

        if (hasError(response)) {
            throw new IllegalStateException(extractErrorMessage(response));
        }

        ReviewArrayEnvelope reviewArray = findReviewArray(response).orElse(null);
        List<ExternalRawReview> reviews = reviewArray == null
                ? List.of()
                : extractReviews(platform, numIid, reviewArray);
        String fetchMetadata = buildFetchMetadata(
                platform,
                numIid,
                reviewArray == null ? "none" : reviewArray.sourcePath(),
                reviews.size()
        );
        return new FetchedReviewPage(reviews, fetchMetadata);
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

    private Optional<ReviewArrayEnvelope> findReviewArray(JsonNode node) {
        for (String key : REVIEW_ARRAY_KEYS) {
            JsonNode direct = node.path(key);
            if (direct.isArray()) {
                return Optional.of(new ReviewArrayEnvelope(direct, key));
            }
        }

        JsonNode data = node.path("data");
        if (data.isObject()) {
            for (String key : REVIEW_ARRAY_KEYS) {
                JsonNode dataArray = data.path(key);
                if (dataArray.isArray()) {
                    return Optional.of(new ReviewArrayEnvelope(dataArray, "data." + key));
                }
            }
        }

        JsonNode item = node.path("item");
        if (item.isObject()) {
            for (String key : REVIEW_ARRAY_KEYS) {
                JsonNode itemArray = item.path(key);
                if (itemArray.isArray()) {
                    return Optional.of(new ReviewArrayEnvelope(itemArray, "item." + key));
                }
            }
        }

        return Optional.empty();
    }

    private List<ExternalRawReview> extractReviews(String platform, String numIid, ReviewArrayEnvelope reviewArray) {
        return java.util.stream.StreamSupport.stream(reviewArray.arrayNode().spliterator(), false)
                .filter(JsonNode::isObject)
                .map(node -> buildReview(platform, numIid, reviewArray.sourcePath(), node))
                .toList();
    }

    private ExternalRawReview buildReview(String platform, String numIid, String arrayKey, JsonNode node) {
        String content = firstNonBlankText(node, "content", "comment", "rate_content", "review", "text")
                .orElse(node.toString());
        String authorId = firstNonBlankText(node, "user_id", "author_id", "nick", "nickname", "display_user_nick")
                .orElse("anonymous");
        Instant reviewTime = firstInstant(node, "review_time", "created", "created_at", "rate_date", "date").orElse(null);
        BigDecimal rating = firstDecimal(node, "rating", "score", "rate", "star").orElse(null);
        String reviewId = firstNonBlankText(node, "id", "rate_id", "review_id", "oid")
                .orElseGet(() -> fingerprint(platform + "|" + numIid + "|" + authorId + "|" + content + "|" + reviewTime));
        String dedupeKey = fingerprint(platform + "|" + numIid + "|" + reviewId + "|" + authorId + "|" + content);
        String fetchMetadata = buildFetchMetadata(platform, numIid, arrayKey, 1);
        return new ExternalRawReview(
                "onebound",
                platform,
                numIid,
                reviewId,
                dedupeKey,
                rating,
                content,
                reviewTime,
                authorId,
                fetchMetadata
        );
    }

    private Optional<String> firstNonBlankText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                String value = child.asText("").trim();
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> firstDecimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isNumber()) {
                return Optional.of(BigDecimal.valueOf(child.decimalValue().doubleValue()));
            }
            if (!child.isMissingNode() && !child.isNull()) {
                String value = child.asText("").trim();
                if (!value.isBlank()) {
                    try {
                        return Optional.of(new BigDecimal(value));
                    } catch (NumberFormatException ignored) {
                        // ignore malformed rating fields in skeleton mode
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> firstInstant(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (child.isNumber()) {
                long raw = child.asLong();
                return Optional.of(raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw));
            }
            if (!child.isMissingNode() && !child.isNull()) {
                String value = child.asText("").trim();
                if (!value.isBlank()) {
                    try {
                        return Optional.of(Instant.parse(value));
                    } catch (Exception ignored) {
                        try {
                            return Optional.of(OffsetDateTime.parse(value).toInstant());
                        } catch (Exception ignoredAgain) {
                            // ignore unparsable review times in skeleton mode
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String buildFetchMetadata(String platform, String numIid, String sourcePath, int reviewCount) {
        return "{\"provider\":\"onebound\",\"platform\":\"%s\",\"externalProductCode\":\"%s\",\"page\":1,\"sourcePath\":\"%s\",\"reviewCount\":%d}"
                .formatted(escapeJson(platform), escapeJson(numIid), escapeJson(sourcePath), reviewCount);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String fingerprint(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record FetchedReviewPage(List<ExternalRawReview> reviews, String fetchMetadata) {
    }

    private record ReviewArrayEnvelope(JsonNode arrayNode, String sourcePath) {
    }
}
