package review.backend.api.dto;

public record WordCloudItem(
        String keyword,
        int frequency,
        double weight,
        String sentimentTag
) {
}
