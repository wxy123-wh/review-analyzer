package review.backend.api.dto.showcase;

import java.util.List;

public record ShowcaseAgentArenaResponse(
        String status,
        boolean implemented,
        String note,
        List<ShowcaseAgentItem> agents
) {
}
