package com.wh.review.backend.service;

import java.net.SocketTimeoutException;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class NlpReviewAnalysisClient {

    private final RestClient restClient;
    private final NlpProperties nlpProperties;

    public NlpReviewAnalysisClient(RestClient.Builder restClientBuilder, NlpProperties nlpProperties) {
        this.nlpProperties = nlpProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(nlpProperties.getConnectTimeout());
        requestFactory.setReadTimeout(nlpProperties.getReadTimeout());

        this.restClient = restClientBuilder
                .baseUrl(nlpProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public AnalyzeResult analyze(String jobId, String productCode, List<String> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return AnalyzeResult.degraded("degraded:nlp_invalid_request:no-reviews");
        }
        try {
            AnalyzeResponse response = restClient.post()
                    .uri(nlpProperties.getAnalyzePath())
                    .body(new AnalyzeRequest(jobId, productCode, reviews))
                    .retrieve()
                    .body(AnalyzeResponse.class);
            return validate(jobId, reviews.size(), response);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                return AnalyzeResult.degraded("degraded:nlp_timeout:" + compactMessage(ex));
            }
            return AnalyzeResult.degraded("degraded:nlp_unavailable:" + compactMessage(ex));
        } catch (RestClientResponseException ex) {
            return AnalyzeResult.degraded("degraded:nlp_http_" + ex.getStatusCode().value() + ":" + compactMessage(ex));
        } catch (RestClientException ex) {
            return AnalyzeResult.degraded("degraded:nlp_error:" + compactMessage(ex));
        }
    }

    private AnalyzeResult validate(String expectedJobId, int expectedReviewCount, AnalyzeResponse response) {
        if (response == null) {
            return AnalyzeResult.degraded("degraded:nlp_invalid_response:empty-body");
        }
        if (!expectedJobId.equals(response.jobId())) {
            return AnalyzeResult.degraded("degraded:nlp_invalid_response:job-id-mismatch");
        }
        List<AspectSentiment> aspectSentiments = response.aspectSentiments();
        if (aspectSentiments == null || aspectSentiments.size() != expectedReviewCount) {
            return AnalyzeResult.degraded("degraded:nlp_invalid_response:aspect-count-mismatch");
        }
        boolean[] seenIndexes = new boolean[expectedReviewCount];
        for (AspectSentiment item : aspectSentiments) {
            if (item == null) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:null-aspect-sentiment");
            }
            if (item.reviewIndex() < 0 || item.reviewIndex() >= expectedReviewCount) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:review-index-out-of-range");
            }
            if (seenIndexes[item.reviewIndex()]) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:duplicate-review-index");
            }
            if (item.aspect() == null || item.aspect().isBlank()) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:blank-aspect");
            }
            if (item.polarity() == null || item.polarity().isBlank()) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:blank-polarity");
            }
            seenIndexes[item.reviewIndex()] = true;
        }
        return AnalyzeResult.success(response);
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("timed out");
    }

    private String compactMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+", " ").trim();
    }

    public record AnalyzeRequest(String jobId, String productCode, List<String> reviews) {
    }

    public record AnalyzeResponse(String jobId, List<AspectSentiment> aspectSentiments, List<IssueCluster> issueClusters) {
    }

    public record AspectSentiment(int reviewIndex, String aspect, String polarity, Double score, Double confidence) {
    }

    public record IssueCluster(String aspect, String title, int mentionCount) {
    }

    public record AnalyzeResult(AnalyzeResponse response, String degradedMessage) {

        public static AnalyzeResult success(AnalyzeResponse response) {
            return new AnalyzeResult(response, null);
        }

        public static AnalyzeResult degraded(String degradedMessage) {
            return new AnalyzeResult(null, degradedMessage);
        }

        public boolean isSuccess() {
            return response != null;
        }
    }
}
