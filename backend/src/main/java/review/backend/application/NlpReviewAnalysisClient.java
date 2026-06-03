package review.backend.application;

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

    public AnalyzeResult analyze(
            String jobId,
            String productCode,
            List<String> reviews,
            TaxonomyService.NlpTaxonomy taxonomy
    ) {
        if (reviews == null || reviews.isEmpty()) {
            return AnalyzeResult.degraded("degraded:nlp_invalid_request:no-reviews");
        }
        try {
            AnalyzeResponse response = restClient.post()
                    .uri(nlpProperties.getAnalyzePath())
                    .body(new AnalyzeRequest(jobId, productCode, reviews, taxonomy))
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
            if (item.polarity() == null || item.polarity().isBlank()) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:blank-polarity");
            }
            if (item.uxSecondaryLabel() == null || item.uxSecondaryLabel().isBlank()) {
                return AnalyzeResult.degraded("degraded:nlp_invalid_response:blank-ux-secondary-label");
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

    public record AnalyzeRequest(
            String jobId,
            String productCode,
            List<String> reviews,
            TaxonomyService.NlpTaxonomy taxonomy
    ) {
    }

    public record AnalyzeResponse(String jobId, List<AspectSentiment> aspectSentiments, List<IssueCluster> issueClusters) {
    }

    public record AspectSentiment(
            int reviewIndex,
            String aspect,
            String polarity,
            Double score,
            Double confidence,
            String uxPrimaryLabel,
            String uxSecondaryLabel,
            String standardizedReason,
            String evidence,
            Integer negativeIntensityScore
    ) {
        public AspectSentiment(int reviewIndex, String aspect, String polarity, Double score, Double confidence) {
            this(
                    reviewIndex,
                    aspect,
                    polarity,
                    score,
                    confidence,
                    defaultUxPrimaryLabel(aspect),
                    defaultUxSecondaryLabel(aspect),
                    null,
                    null,
                    null
            );
        }
    }

    public record IssueCluster(String aspect, String title, int mentionCount) {
    }

    private static String defaultUxPrimaryLabel(String aspect) {
        return switch (aspect == null ? "" : aspect) {
            case "battery", "bluetooth" -> "产品硬件";
            case "noise-canceling", "microphone" -> "声音表现";
            case "comfort" -> "产品体验";
            default -> "无明显问题";
        };
    }

    private static String defaultUxSecondaryLabel(String aspect) {
        return switch (aspect == null ? "" : aspect) {
            case "battery" -> "电池与续航";
            case "bluetooth" -> "连接与稳定性";
            case "noise-canceling" -> "降噪与通透";
            case "comfort" -> "佩戴与人体工学";
            case "microphone" -> "麦克风与通话";
            default -> "无明显问题";
        };
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
