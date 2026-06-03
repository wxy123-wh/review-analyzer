package com.wh.reputation.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class NlpClient {
    private static final Logger log = LoggerFactory.getLogger(NlpClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NlpClient(RestTemplateBuilder builder, @Value("${nlp.service.url:http://localhost:8000}") String baseUrl) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl;
    }

    public List<ClusterItem> cluster(List<ReviewDoc> docs, int kMin, int kMax) {
        String url = baseUrl + "/cluster";
        ClusterRequest req = new ClusterRequest(docs, kMin, kMax);
        try {
            ClusterResponse resp = restTemplate.postForObject(url, req, ClusterResponse.class);
            return resp != null ? resp.clusters() : List.of();
        } catch (Exception e) {
            log.error("Failed to call NLP service /cluster: {}", e.getMessage());
            return List.of();
        }
    }

    public List<TopicItem> topics(List<ReviewDoc> docs, int numTopics) {
        String url = baseUrl + "/topics";
        TopicRequest req = new TopicRequest(docs, numTopics);
        try {
            TopicResponse resp = restTemplate.postForObject(url, req, TopicResponse.class);
            return resp != null ? resp.topics() : List.of();
        } catch (Exception e) {
            log.error("Failed to call NLP service /topics: {}", e.getMessage());
            return List.of();
        }
    }

    public record ReviewDoc(Long id, String text, List<String> tokens,
            @JsonProperty("sentiment_label") String sentimentLabel) {
    }

    public record ClusterRequest(List<ReviewDoc> docs, @JsonProperty("k_min") int kMin,
            @JsonProperty("k_max") int kMax) {
    }

    public record ClusterItem(@JsonProperty("review_ids") List<Long> reviewIds,
            @JsonProperty("top_terms") List<String> topTerms, @JsonProperty("neg_rate") double negRate) {
    }

    public record ClusterResponse(List<ClusterItem> clusters) {
    }

    public record TopicRequest(List<ReviewDoc> docs, @JsonProperty("num_topics") int numTopics) {
    }

    public record TopicItem(@JsonProperty("topic_id") int topicId, @JsonProperty("key_words") List<String> keyWords,
            double weight, @JsonProperty("evidence_ids") List<Long> evidenceIds) {
    }

    public record TopicResponse(List<TopicItem> topics) {
    }
}
