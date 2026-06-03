package review.backend.api.dto;

public record IssueItem(
        String issueId,
        String title,
        String aspect,
        String uxPrimaryLabel,
        String uxSecondaryLabel,
        double priorityScore,
        String evidenceSummary
) {
}
