package review.backend.api.dto.showcase;

public record ShowcaseAgentItem(
        String agentName,
        String role,
        String state,
        double confidence
) {
}
