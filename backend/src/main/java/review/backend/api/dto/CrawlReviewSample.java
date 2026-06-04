package review.backend.api.dto;

public record CrawlReviewSample(
        String sourceReviewId,
        String productName,
        String content,
        String rating,
        String reviewTime
) {
}
